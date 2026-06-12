/*
 * IndieKitAds.kt — IndieKitAds
 *
 * 역할
 *  - AdMob 의 배너 / 전면 / 리워드 / Native 광고를 한 줄로 띄우는 단일 진입점.
 *  - 유럽 광고 동의창 (UMP) 도 같이 처리.
 *
 * 주요 개념
 *  - 처음 설정: 앱 시작 시 한 번 `IndieKitAds.configure(application, ...)` 호출.
 *    광고 ID 미주입 시 Google 공식 테스트 ID 자동 적용 — 개발 초기 / SDK 동작 확인용.
 *  - 자리 (placement): 같은 종류의 광고를 화면마다 다른 ID 로 분리하고 싶을 때 사용.
 *    예: 배너를 홈 / 상세 / 설정 화면에 각각 다른 ID 로. configure 의 `bannerAdUnitIDs` 에
 *    자리 이름 → ID 묶음으로 등록하고, 띄울 때 `BannerAdView(placement = "home")` 처럼 자리 이름을 넘긴다.
 *    자리 이름을 안 넘기면 기본 ID (`bannerAdUnitID`) 사용 — 기존 호출 코드 그대로 동작.
 *  - Pro 사용자 광고 숨김은 앱이 분기. 라이브러리는 결제 상태를 모른다.
 *  - 통계 자동 연결: 광고 표시 / 클릭 이벤트는 약한 연결 통로 (`AnalyticsBus`) 로 흘려보냄.
 *    통계 모듈을 깐 앱이면 자동으로 Firebase Analytics 에 들어감.
 *
 * 사용 방법 (앱 측)
 *  ```kotlin
 *  // Application.onCreate 에서 한 번:
 *  IndieKitAds.configure(this, requestConsent = true)
 *
 *  // 화면마다 다른 배너 ID 를 쓰고 싶으면 자리 (placement) 등록:
 *  IndieKitAds.configure(
 *      context = this,
 *      bannerAdUnitID = AdUnitID(release = "ca-app-pub-..."),          // 기본 배너 ID
 *      bannerAdUnitIDs = mapOf(                                        // 자리별 배너 ID (선택)
 *          "home"     to AdUnitID(release = "ca-app-pub-...-home"),
 *          "settings" to AdUnitID(release = "ca-app-pub-...-settings")
 *      )
 *  )
 *
 *  // 화면 안에서 (Compose):
 *  if (!subscription.isPro) {
 *      BannerAdView(modifier = Modifier.fillMaxWidth())                      // 기본 자리
 *      BannerAdView(modifier = Modifier.fillMaxWidth(), placement = "home")  // "home" 자리 ID 사용
 *      NativeAdView(modifier = Modifier.fillMaxWidth())
 *  }
 *
 *  // 운동 완료 시점:
 *  IndieKitAds.showInterstitial(activity) {
 *      // 광고 닫힘 후 실행
 *  }
 *
 *  // 사용자가 "광고 보고 힌트 받기" 눌렀을 때:
 *  IndieKitAds.showRewarded(activity) { reward ->
 *      if (reward != null) hintCount += reward.amount
 *  }
 *  ```
 *
 * 주의사항
 *  - `AndroidManifest.xml` 의 `com.google.android.gms.ads.APPLICATION_ID` meta-data 는 라이브러리가 미리
 *    Google 공식 테스트 app id 로 채워 둠. 출시 직전 사용처 앱이 자기 매니페스트의 같은 키를 실제 app id 로 덮어쓴다.
 *  - 출시 빌드인데 release 광고 ID 가 비어 있으면 Logcat 강한 경고 (정책 위반).
 *
 * iOS 자매 (`IndieKitAds` enum) 와 같은 진입점 이름 / 시그니처. 단 Native 자체 구현은 안드로이드 선행.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import kr.co.junu.indiekit.core.AnalyticsValue
import kr.co.junu.indiekit.core.IKLogger
import kr.co.junu.indiekit.core.analyticsParams
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.locks.ReentrantLock

/** 광고 모듈의 단일 진입점. */
public object IndieKitAds {

    /** 단계 2 진입 표식 — placeholder 가 실 구현으로 교체되었음. */
    public const val isPlaceholder: Boolean = false

    @Volatile
    private var initializedFlag: Boolean = false
    private val configureLock = ReentrantLock()

    /** Application context — preload / show 에서 다시 사용. */
    @Volatile
    private var appContext: Context? = null

    // 광고 종류마다 기본 ID 한 쌍 + 자리별 ID 묶음 두 칸.
    private var bannerAdUnitID: AdUnitID = AdUnitID()
    private var interstitialAdUnitID: AdUnitID = AdUnitID()
    private var rewardedAdUnitID: AdUnitID = AdUnitID()
    private var nativeAdUnitID: AdUnitID = AdUnitID()

    private var bannerAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var interstitialAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var rewardedAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var nativeAdUnitIDs: Map<String, AdUnitID> = emptyMap()

    /** configure 가 한 번 이상 호출되었는지 (BannerAdView / NativeAdView 가 placeholder 모드 판별에 사용). */
    public val isConfigured: Boolean
        get() = initializedFlag

    /** 전면 광고가 적재된 상태인지 — 디버그용. (기본 자리) */
    public val isInterstitialReady: Boolean
        get() = InterstitialAdLoader.loader(null).isLoaded

    /** 리워드 광고가 적재된 상태인지 — 디버그용. (기본 자리) */
    public val isRewardedReady: Boolean
        get() = RewardedAdLoader.loader(null).isLoaded

    /** 자리 이름으로 묻는 전면 광고 적재 상태 — 디버그용. */
    public fun isInterstitialReady(placement: String?): Boolean =
        InterstitialAdLoader.loader(placement).isLoaded

    /** 자리 이름으로 묻는 리워드 광고 적재 상태 — 디버그용. */
    public fun isRewardedReady(placement: String?): Boolean =
        RewardedAdLoader.loader(placement).isLoaded

    // ────────────────────────────────────────────────────────────────────────
    // 처음 설정
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고 모듈을 처음 설정한다.
     *
     * 동작
     *  1. 광고 ID 보관 (debug / release 분리 그대로, 기본 + 자리별 묶음).
     *  2. MobileAds.initialize 호출 (AdMob SDK 시작).
     *  3. 전면 / 리워드 광고 미리 적재 (preload) — 기본 자리 + 등록한 모든 자리.
     *  4. `requestConsent` 가 true 이면 UMP 동의창 흐름 시작 (EEA 사용자에 한해 동의창 노출).
     *
     * @param context Application context. (Application 클래스의 this 를 권장 — Activity 는 onDestroy 시 누수 위험.)
     * @param bannerAdUnitID 배너 광고 기본 ID 한 쌍. 미주입 시 테스트 ID.
     * @param interstitialAdUnitID 전면 광고 기본 ID 한 쌍. 미주입 시 테스트 ID.
     * @param rewardedAdUnitID 리워드 광고 기본 ID 한 쌍. 미주입 시 테스트 ID.
     * @param nativeAdUnitID Native 광고 기본 ID 한 쌍. 미주입 시 테스트 ID.
     * @param bannerAdUnitIDs 자리 이름 → 배너 광고 ID 묶음. 화면마다 다른 배너 ID 를 쓸 때.
     *                        예: `mapOf("home" to AdUnitID(release = "..."), "settings" to AdUnitID(release = "..."))`
     * @param interstitialAdUnitIDs 자리 이름 → 전면 광고 ID 묶음.
     * @param rewardedAdUnitIDs 자리 이름 → 리워드 광고 ID 묶음.
     * @param nativeAdUnitIDs 자리 이름 → Native 광고 ID 묶음.
     * @param requestConsent true 면 UMP 동의창 자동 시작. EEA 외 사용자엔 영향 없음.
     *                       Application.onCreate 에서 호출하면 Activity 가 없어 form 표시는 첫 Activity 시점으로 미뤄진다.
     */
    public fun configure(
        context: Context,
        bannerAdUnitID: AdUnitID = AdUnitID(),
        interstitialAdUnitID: AdUnitID = AdUnitID(),
        rewardedAdUnitID: AdUnitID = AdUnitID(),
        nativeAdUnitID: AdUnitID = AdUnitID(),
        bannerAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        interstitialAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        rewardedAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        nativeAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        requestConsent: Boolean = true
    ) {
        configureLock.lock()
        val alreadyConfigured = initializedFlag
        appContext = context.applicationContext
        this.bannerAdUnitID = bannerAdUnitID
        this.interstitialAdUnitID = interstitialAdUnitID
        this.rewardedAdUnitID = rewardedAdUnitID
        this.nativeAdUnitID = nativeAdUnitID
        this.bannerAdUnitIDs = bannerAdUnitIDs
        this.interstitialAdUnitIDs = interstitialAdUnitIDs
        this.rewardedAdUnitIDs = rewardedAdUnitIDs
        this.nativeAdUnitIDs = nativeAdUnitIDs
        initializedFlag = true
        configureLock.unlock()

        if (alreadyConfigured) {
            IKLogger.ads.debug("IndieKitAds.configure 재호출 — 광고 ID 만 갱신")
            return
        }

        // SDK 시작 호출.
        MobileAds.initialize(context.applicationContext) {
            IKLogger.ads.info("MobileAds.initialize 완료")
            // SDK 준비 완료 후 미리 적재 — 기본 자리 + 등록한 모든 자리.
            val appCtx = context.applicationContext
            InterstitialAdLoader.loader(null).preload(appCtx)
            interstitialAdUnitIDs.keys.forEach { placement ->
                InterstitialAdLoader.loader(placement).preload(appCtx)
            }
            RewardedAdLoader.loader(null).preload(appCtx)
            rewardedAdUnitIDs.keys.forEach { placement ->
                RewardedAdLoader.loader(placement).preload(appCtx)
            }
        }

        if (requestConsent) {
            // configure 시점이 Application.onCreate 라면 activity 가 null. 첫 Activity 시점에
            // 사용자가 직접 requestConsentForm(activity) 호출하거나, 아래 ActivityLifecycleCallbacks 자동 흐름 사용.
            tryAutoRequestConsent(context)
        }
    }

    /**
     * Application 이 주입되면 첫 Activity 가 잡히는 시점에 자동 동의 흐름을 돌린다.
     * 다른 형태의 context (Service 등) 가 들어오면 자동 흐름은 건너뜀 — 사용자가 직접 requestConsentForm 호출.
     */
    private fun tryAutoRequestConsent(context: Context) {
        val app = context.applicationContext as? Application ?: run {
            IKLogger.ads.debug("자동 UMP 동의 흐름 건너뜀 — Application context 가 아님.")
            return
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: android.os.Bundle?
            ) {
                app.unregisterActivityLifecycleCallbacks(this)
                UMPCoordinator.requestConsentInfoUpdate(app, activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // ────────────────────────────────────────────────────────────────────────
    // 광고 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 전면 광고가 미리 적재되어 있으면 표시. 없으면 즉시 onDismiss 호출.
     *
     * @param activity 광고를 띄울 Activity.
     * @param placement configure 의 `interstitialAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 자리.
     * @param onDismiss 광고 닫힘 후 (또는 광고 없음 시 즉시) 실행할 콜백.
     */
    public fun showInterstitial(
        activity: Activity,
        placement: String? = null,
        onDismiss: () -> Unit = {}
    ) {
        if (!ensureConfigured()) {
            onDismiss()
            return
        }
        InterstitialAdLoader.loader(placement).showIfLoaded(activity, onDismiss)
    }

    /**
     * 리워드 광고가 미리 적재되어 있으면 표시. 사용자가 끝까지 시청하면 보상.
     *
     * @param activity 광고를 띄울 Activity.
     * @param placement configure 의 `rewardedAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 자리.
     * @param onResult 광고 종료 후 실행. 보상 받았으면 AdReward, 아니면 null.
     */
    public fun showRewarded(
        activity: Activity,
        placement: String? = null,
        onResult: (AdReward?) -> Unit
    ) {
        if (!ensureConfigured()) {
            onResult(null)
            return
        }
        RewardedAdLoader.loader(placement).showIfLoaded(activity, onResult)
    }

    /**
     * UMP 동의창을 다시 띄운다 (예: 설정 화면의 "광고 설정 변경" 버튼).
     *
     * EEA 외 사용자에겐 동의창이 자동으로 안 뜨므로 이 함수 호출도 무의미.
     *
     * @param activity 동의창을 띄울 Activity.
     * @param onComplete 동의창 닫힘 후 호출. 에러 발생 시 Throwable 로 전달.
     */
    public fun requestConsentForm(activity: Activity, onComplete: (Throwable?) -> Unit = {}) {
        UMPCoordinator.presentForm(activity, onComplete)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 광고 ID 해석 (모듈 내부 + Composable 이 사용)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 배너 광고 ID (해석된 값 — 절대 null 아님).
     *
     * @param placement configure 의 `bannerAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 ID.
     */
    public fun resolvedBannerAdUnitID(context: Context, placement: String? = null): String =
        selectAdUnitID(bannerAdUnitIDs, bannerAdUnitID, placement, "배너")
            .resolve(context, AdMobTestUnitID.BANNER)

    /** 전면 광고 ID. */
    public fun resolvedInterstitialAdUnitID(context: Context, placement: String? = null): String =
        selectAdUnitID(interstitialAdUnitIDs, interstitialAdUnitID, placement, "전면")
            .resolve(context, AdMobTestUnitID.INTERSTITIAL)

    /** 리워드 광고 ID. */
    public fun resolvedRewardedAdUnitID(context: Context, placement: String? = null): String =
        selectAdUnitID(rewardedAdUnitIDs, rewardedAdUnitID, placement, "리워드")
            .resolve(context, AdMobTestUnitID.REWARDED)

    /** Native 광고 ID. */
    public fun resolvedNativeAdUnitID(context: Context, placement: String? = null): String =
        selectAdUnitID(nativeAdUnitIDs, nativeAdUnitID, placement, "Native")
            .resolve(context, AdMobTestUnitID.NATIVE)

    /**
     * 자리 이름으로 광고 ID 한 쌍을 고른다.
     *
     * 규칙
     *  - 자리 이름이 null → 기본 ID.
     *  - 자리 이름이 등록되어 있음 → 그 자리의 ID.
     *  - 자리 이름이 등록 안 됨 → 경고 로그 후 기본 ID (크래시 없음 — 인디 앱 특성상 관대하게).
     *
     * internal — 단위 검증에서 직접 호출.
     */
    internal fun selectAdUnitID(
        placements: Map<String, AdUnitID>,
        defaultID: AdUnitID,
        placement: String?,
        format: String
    ): AdUnitID {
        if (placement == null) return defaultID
        placements[placement]?.let { return it }
        IKLogger.ads.warning("등록 안 된 $format 광고 자리 '$placement' — 기본 ID 로 대체합니다.")
        return defaultID
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ────────────────────────────────────────────────────────────────────────

    private fun ensureConfigured(): Boolean {
        if (!initializedFlag) {
            IKLogger.ads.warning(
                "IndieKitAds.configure 가 호출되지 않은 상태에서 광고 표시 시도 — 무시합니다."
            )
            return false
        }
        return true
    }
}

/**
 * 광고 통계 이벤트 파라미터를 만든다 — 자리 이름이 있으면 `placement` 칸도 같이 실어
 * Firebase 보고서에서 자리별로 분리해 볼 수 있게 한다. (모듈 내부 공용)
 */
internal fun adAnalyticsParams(format: String, placement: String?): Map<String, AnalyticsValue> =
    if (placement == null) analyticsParams("format" to format)
    else analyticsParams("format" to format, "placement" to placement)
