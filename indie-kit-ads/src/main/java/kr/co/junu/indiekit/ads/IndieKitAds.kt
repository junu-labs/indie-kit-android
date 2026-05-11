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
 *  - Pro 사용자 광고 숨김은 앱이 분기. 라이브러리는 결제 상태를 모른다.
 *  - 통계 자동 연결: 광고 표시 / 클릭 이벤트는 약한 연결 통로 (`AnalyticsBus`) 로 흘려보냄.
 *    통계 모듈을 깐 앱이면 자동으로 Firebase Analytics 에 들어감.
 *
 * 사용 방법 (앱 측)
 *  ```kotlin
 *  // Application.onCreate 에서 한 번:
 *  IndieKitAds.configure(this, requestConsent = true)
 *
 *  // 화면 안에서 (Compose):
 *  if (!subscription.isPro) {
 *      BannerAdView(modifier = Modifier.fillMaxWidth())
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
import kr.co.junu.indiekit.core.IKLogger
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

    private var bannerAdUnitID: AdUnitID = AdUnitID()
    private var interstitialAdUnitID: AdUnitID = AdUnitID()
    private var rewardedAdUnitID: AdUnitID = AdUnitID()
    private var nativeAdUnitID: AdUnitID = AdUnitID()

    /** configure 가 한 번 이상 호출되었는지 (BannerAdView / NativeAdView 가 placeholder 모드 판별에 사용). */
    public val isConfigured: Boolean
        get() = initializedFlag

    /** 전면 광고가 적재된 상태인지 — 디버그용. */
    public val isInterstitialReady: Boolean
        get() = InterstitialAdLoader.isLoaded

    /** 리워드 광고가 적재된 상태인지 — 디버그용. */
    public val isRewardedReady: Boolean
        get() = RewardedAdLoader.isLoaded

    // ────────────────────────────────────────────────────────────────────────
    // 처음 설정
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고 모듈을 처음 설정한다.
     *
     * 동작
     *  1. 광고 ID 보관 (debug / release 분리 그대로).
     *  2. MobileAds.initialize 호출 (AdMob SDK 시작).
     *  3. 전면 / 리워드 광고 미리 적재 (preload).
     *  4. `requestConsent` 가 true 이면 UMP 동의창 흐름 시작 (EEA 사용자에 한해 동의창 노출).
     *
     * @param context Application context. (Application 클래스의 this 를 권장 — Activity 는 onDestroy 시 누수 위험.)
     * @param bannerAdUnitID 배너 광고 ID 한 쌍. 미주입 시 테스트 ID.
     * @param interstitialAdUnitID 전면 광고 ID 한 쌍. 미주입 시 테스트 ID.
     * @param rewardedAdUnitID 리워드 광고 ID 한 쌍. 미주입 시 테스트 ID.
     * @param nativeAdUnitID Native 광고 ID 한 쌍. 미주입 시 테스트 ID.
     * @param requestConsent true 면 UMP 동의창 자동 시작. EEA 외 사용자엔 영향 없음.
     *                       Application.onCreate 에서 호출하면 Activity 가 없어 form 표시는 첫 Activity 시점으로 미뤄진다.
     */
    public fun configure(
        context: Context,
        bannerAdUnitID: AdUnitID = AdUnitID(),
        interstitialAdUnitID: AdUnitID = AdUnitID(),
        rewardedAdUnitID: AdUnitID = AdUnitID(),
        nativeAdUnitID: AdUnitID = AdUnitID(),
        requestConsent: Boolean = true
    ) {
        configureLock.lock()
        val alreadyConfigured = initializedFlag
        appContext = context.applicationContext
        this.bannerAdUnitID = bannerAdUnitID
        this.interstitialAdUnitID = interstitialAdUnitID
        this.rewardedAdUnitID = rewardedAdUnitID
        this.nativeAdUnitID = nativeAdUnitID
        initializedFlag = true
        configureLock.unlock()

        if (alreadyConfigured) {
            IKLogger.ads.debug("IndieKitAds.configure 재호출 — 광고 ID 만 갱신")
            return
        }

        // SDK 시작 호출.
        MobileAds.initialize(context.applicationContext) {
            IKLogger.ads.info("MobileAds.initialize 완료")
            // SDK 준비 완료 후 미리 적재.
            InterstitialAdLoader.preload(context.applicationContext)
            RewardedAdLoader.preload(context.applicationContext)
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
     * @param onDismiss 광고 닫힘 후 (또는 광고 없음 시 즉시) 실행할 콜백.
     */
    public fun showInterstitial(activity: Activity, onDismiss: () -> Unit = {}) {
        if (!ensureConfigured()) {
            onDismiss()
            return
        }
        InterstitialAdLoader.showIfLoaded(activity, onDismiss)
    }

    /**
     * 리워드 광고가 미리 적재되어 있으면 표시. 사용자가 끝까지 시청하면 보상.
     *
     * @param activity 광고를 띄울 Activity.
     * @param onResult 광고 종료 후 실행. 보상 받았으면 AdReward, 아니면 null.
     */
    public fun showRewarded(activity: Activity, onResult: (AdReward?) -> Unit) {
        if (!ensureConfigured()) {
            onResult(null)
            return
        }
        RewardedAdLoader.showIfLoaded(activity, onResult)
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

    /** 배너 광고 ID (해석된 값 — 절대 null 아님). */
    public fun resolvedBannerAdUnitID(context: Context): String =
        bannerAdUnitID.resolve(context, AdMobTestUnitID.BANNER)

    /** 전면 광고 ID. */
    public fun resolvedInterstitialAdUnitID(context: Context): String =
        interstitialAdUnitID.resolve(context, AdMobTestUnitID.INTERSTITIAL)

    /** 리워드 광고 ID. */
    public fun resolvedRewardedAdUnitID(context: Context): String =
        rewardedAdUnitID.resolve(context, AdMobTestUnitID.REWARDED)

    /** Native 광고 ID. */
    public fun resolvedNativeAdUnitID(context: Context): String =
        nativeAdUnitID.resolve(context, AdMobTestUnitID.NATIVE)

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
