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
 *    자리 이름을 안 넘기면 기본 자리 (`IndieKitAds.DEFAULT_PLACEMENT` = "default") 의 ID 를 쓴다.
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
 *      bannerAdUnitIDs = mapOf(                                                    // 배너 ID 묶음
 *          IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-..."),  // 기본 배너
 *          "settings"                    to AdUnitID(release = "ca-app-pub-...-settings")
 *      )
 *  )
 *
 *  // 화면 안에서 (Compose):
 *  if (!subscription.isPro) {
 *      BannerAdView(modifier = Modifier.fillMaxWidth())                          // 기본 자리 (DEFAULT_PLACEMENT)
 *      BannerAdView(modifier = Modifier.fillMaxWidth(), placement = "settings")  // "settings" 자리 ID 사용
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
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableIntStateOf
import kr.co.junu.indiekit.core.AnalyticsValue
import kr.co.junu.indiekit.core.IKLogger
import kr.co.junu.indiekit.core.analyticsParams
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.locks.ReentrantLock

/** 광고 모듈의 단일 진입점. */
public object IndieKitAds {

    /** 단계 2 진입 표식 — placeholder 가 실 구현으로 교체되었음. */
    public const val isPlaceholder: Boolean = false

    /**
     * 자리 이름을 안 넘기고 광고를 띄울 때 (`BannerAdView()` 등) 찾는 기본 자리 이름.
     * configure 의 `bannerAdUnitIDs` 등에 이 이름으로 ID 를 등록해 두면 기본 광고가 된다.
     * 예: `mapOf(IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "..."), "settings" to AdUnitID(release = "..."))`
     *
     * iOS 자매 (`IndieKitAds.defaultPlacement`) 와 같은 값 ("default").
     */
    public const val DEFAULT_PLACEMENT: String = "default"

    @Volatile
    private var initializedFlag: Boolean = false
    private val configureLock = ReentrantLock()

    /** Application context — preload / show 에서 다시 사용. */
    @Volatile
    private var appContext: Context? = null

    // 광고 종류마다 자리 이름 → ID 묶음 한 칸. 자리 이름을 안 넘긴 경우는 DEFAULT_PLACEMENT 키로 찾는다.
    private var bannerAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var interstitialAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var rewardedAdUnitIDs: Map<String, AdUnitID> = emptyMap()
    private var nativeAdUnitIDs: Map<String, AdUnitID> = emptyMap()

    /** 시험용 나라 강제 설정 (configure 에서 보관). null 이면 실제 위치대로. 디버그 빌드에서만 반영. */
    @Volatile
    private var consentTestSettings: ConsentTestSettings? = null

    /**
     * configure 가 끝난 것을 *화면이 지켜볼 수 있는 형태* 로 담아 두는 자리.
     *
     * [isConfigured] 는 평범한 Boolean 이라, 배너가 이 값을 읽고 "아직 설정 전" 이라 판단해
     * 빈 칸을 그리면 **설정이 나중에 끝나도 스스로 다시 그려지지 않는다** — 빈 칸으로 굳는다.
     * 그래서 화면용으로는 이 자리를 따로 두고, [isConfiguredForCompose] 로 읽게 한다.
     */
    private val configuredRevision = mutableIntStateOf(0)

    /** configure 가 한 번 이상 호출되었는지 (BannerAdView / NativeAdView 가 placeholder 모드 판별에 사용). */
    public val isConfigured: Boolean
        get() = initializedFlag

    /**
     * 화면 (Composable) 이 읽는 설정 완료 여부.
     *
     * 이 값을 읽은 화면은 configure 가 끝나는 순간 스스로 다시 그려진다.
     * 앱이 configure 를 뒷실에서 부르거나 화면보다 늦게 불러도 배너가 빈 칸으로 굳지 않는다.
     */
    internal val isConfiguredForCompose: Boolean
        get() = configuredRevision.intValue > 0

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
     *  1. 광고 ID 보관 (자리 이름 → 출시 ID 묶음). 부른 실에서 곧바로 끝난다.
     *  2. `requestConsent` 가 true 이면 UMP 동의창 흐름 예약 (EEA 사용자에 한해 동의창 노출).
     *  3. MobileAds.initialize 호출 (AdMob SDK 시작) — **뒷실에서**.
     *  4. SDK 준비가 끝나면 전면 / 리워드 광고 미리 적재 (preload) — 앱이 등록한 자리만, 화면 그리는 실에서.
     *
     * 실 (스레드) 규칙
     *  - 이 함수는 **화면 그리는 실에서 그대로 불러도 된다.** 오래 걸리는 일 (SDK 시작) 은
     *    이 안에서 스스로 뒷실로 비켜 간다. 앱이 따로 실을 만들 필요가 없다.
     *
     * 부르는 시점
     *  - Application.onCreate 가 기본. 하지만 **화면이 뜬 뒤로 미뤄 불러도 동의 절차는
     *    빠지지 않는다** — 모듈이 "지금 떠 있는 화면" 을 스스로 찾아 그 자리에서 진행한다.
     *    (첫 안내가 동의창에 덮이지 않게 configure 를 안내 뒤로 미루는 앱도 안전.)
     *
     * @param context Application context. (Application 클래스의 this 를 권장 — Activity 는 onDestroy 시 누수 위험.)
     * @param bannerAdUnitIDs 자리 이름 → 배너 광고 ID 묶음. 자리 이름을 안 넘기는 기본 배너는
     *                        `IndieKitAds.DEFAULT_PLACEMENT` ("default") 키로 등록한다.
     *                        예: `mapOf(IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "..."), "settings" to AdUnitID(release = "..."))`
     * @param interstitialAdUnitIDs 자리 이름 → 전면 광고 ID 묶음.
     * @param rewardedAdUnitIDs 자리 이름 → 리워드 광고 ID 묶음.
     * @param nativeAdUnitIDs 자리 이름 → Native 광고 ID 묶음.
     * @param requestConsent true 면 UMP 동의창 자동 시작. EEA 외 사용자엔 영향 없음.
     *                       Application.onCreate 에서 호출하면 첫 화면이 잡히는 시점에,
     *                       화면이 뜬 *뒤* 에 호출하면 (첫 안내를 먼저 보여 주고 미룬 경우 등)
     *                       지금 떠 있는 화면으로 즉시 동의 절차가 돈다.
     * @param consentTestSettings 시험용 나라 강제 설정 — 유럽 동의창이 뜨는 모습을
     *                            에뮬레이터 / 시험 기기에서 확인할 때만 넘긴다.
     *                            디버그 빌드에서만 반영, 출시 빌드에서는 무시 (경고 로그).
     */
    public fun configure(
        context: Context,
        bannerAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        interstitialAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        rewardedAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        nativeAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        requestConsent: Boolean = true,
        consentTestSettings: ConsentTestSettings? = null
    ) {
        configureLock.lock()
        val alreadyConfigured = initializedFlag
        appContext = context.applicationContext
        this.bannerAdUnitIDs = bannerAdUnitIDs
        this.interstitialAdUnitIDs = interstitialAdUnitIDs
        this.rewardedAdUnitIDs = rewardedAdUnitIDs
        this.nativeAdUnitIDs = nativeAdUnitIDs
        this.consentTestSettings = consentTestSettings
        initializedFlag = true
        configureLock.unlock()

        // 화면이 지켜보는 자리도 같이 올린다 — 이미 그려진 배너가 스스로 다시 그려진다.
        configuredRevision.intValue += 1

        if (alreadyConfigured) {
            IKLogger.ads.debug("IndieKitAds.configure 재호출 — 광고 ID 만 갱신")
            return
        }

        val appCtx = context.applicationContext

        // ── 1. 동의 절차를 먼저 건다 ──────────────────────────────────────────
        // 화면이 이미 떠 있으면 그 화면으로 바로, 아직 없으면 첫 화면이 잡히는 순간
        // 진행한다 (tryAutoRequestConsent 안에서 갈라짐). 어느 쪽이든 오래 걸리는 일이
        // 아니라 화면을 붙잡지 않는다. 그리고 **반드시 SDK 시작보다 앞** 이어야 한다 —
        // 동의 판정 전에 광고 요청이 먼저 나가는 틈을 줄인다.
        if (requestConsent) {
            tryAutoRequestConsent(context)
        }

        // ── 2. SDK 시작은 뒷실에서 ─────────────────────────────────────────────
        // MobileAds.initialize 는 Google Play 서비스에 붙고 광고 설정을 그물 너머에서 받아온다.
        // 특히 깔고 나서 첫 실행에서는 수백 밀리초 이상 걸린다. 화면 그리는 실에서 부르면
        // 첫 화면을 그리지 못한 채 폰이 "응답 없음" 으로 앱을 끈다.
        // (매니페스트 OPTIMIZE_INITIALIZATION 표시는 SDK 24.0.0 부터 이미 기본으로 켜져 있지만,
        //  구글 설명대로 시작 일의 *일부* 만 옮길 뿐이라 이 뒷실이 여전히 필요하다.)
        // 출처: https://developers.google.com/admob/android/quick-start
        //
        // 코루틴 대신 Thread 를 쓰는 까닭: 이 모듈은 kotlinx-coroutines 를 자기 의존성으로
        // 선언하지 않는다 (Compose 가 딸려 오는 것에 얹혀 있을 뿐). 한 번 쓰고 끝나는 일에
        // 남의 짐을 기대지 않는다.
        Thread({
            MobileAds.initialize(appCtx) {
                IKLogger.ads.info("MobileAds.initialize 완료")
                // 광고를 불러오는 일은 화면 그리는 실에서 한다 (구글 광고 묶음 규칙).
                // 위에서 뒷실로 옮겼으므로 여기서 화면 그리는 실로 되돌린다.
                Handler(Looper.getMainLooper()).post { preloadRegisteredPlacements(appCtx) }
            }
        }, "indie-kit-ads-init").start()
    }

    /**
     * 전면 / 리워드 광고를 미리 적재한다 — **앱이 실제로 등록한 자리만.**
     *
     * 등록 안 한 기본 자리까지 부르지 않는 까닭: 시험용 (디버그) 빌드에서는 [AdUnitID.resolve] 가
     * 등록 여부와 상관없이 늘 구글 시험용 번호를 채운다. 그래서 예전 짜임에서는 앱이 쓰지도 않는
     * 전면 광고를 켤 때마다 불러왔다 — 앱 켜는 순간에 쓸데없는 그물 요청이 얹혔다.
     */
    private fun preloadRegisteredPlacements(appCtx: Context) {
        interstitialAdUnitIDs.keys.forEach { placement ->
            InterstitialAdLoader.loader(placement).preload(appCtx)
        }
        rewardedAdUnitIDs.keys.forEach { placement ->
            RewardedAdLoader.loader(placement).preload(appCtx)
        }
    }

    /**
     * 자동 동의 흐름을 돌린다 — configure 시점과 무관하게 동작해야 한다.
     *
     * 두 갈래:
     *  1. **이미 떠 있는 화면이 있으면** (앱이 configure 를 첫 안내 뒤로 미룬 경우 등)
     *     그 화면으로 *지금 바로* 진행한다. "지금 화면" 은 CurrentActivityTracker 가 알고 있다
     *     — 추적자는 앱 시작 순간 (어떤 화면보다 먼저) 자동으로 심어진다.
     *  2. **아직 화면이 없으면** (Application.onCreate 에서 부른 보통 경우) 첫 화면이
     *     잡히는 순간 한 번 진행하고 예약을 거둔다.
     *
     * 예전 짜임은 2번만 있었다 — "화면이 새로 만들어지는 순간" 만 듣고 바로 예약을
     * 거뒀기 때문에, configure 가 화면보다 늦으면 그 실행에서 동의 절차가 통째로
     * 안 돌았다 (화면이 하나뿐이면 기회가 다시 안 옴 — 유럽에서는 법 위반).
     *
     * 판단은 화면 그리는 실 (main) 에 모아 한다 — configure 를 뒷실에서 불러도
     * "지금 화면" 읽기와 예약 걸기가 서로 엇갈리지 않는다.
     *
     * 다른 형태의 context (Service 등) 가 들어오면 자동 흐름은 건너뜀 — 사용자가 직접 requestConsentForm 호출.
     */
    private fun tryAutoRequestConsent(context: Context) {
        val app = context.applicationContext as? Application ?: run {
            IKLogger.ads.debug("자동 UMP 동의 흐름 건너뜀 — Application context 가 아님.")
            return
        }

        // 추적자가 자동으로 안 심긴 별난 환경 대비 — 이미 심겼으면 아무 일 안 함.
        CurrentActivityTracker.install(app)

        Handler(Looper.getMainLooper()).post {
            val activityNow = CurrentActivityTracker.current
            if (activityNow != null) {
                // 갈래 1 — 화면이 이미 있다: 지금 바로 진행.
                UMPCoordinator.requestConsentInfoUpdate(app, activityNow, consentTestSettings)
            } else {
                // 갈래 2 — 화면이 아직 없다: 첫 화면이 잡히는 순간 한 번 진행.
                app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: android.os.Bundle?
                    ) = fire(activity)

                    // 만들어짐 신호를 어떤 이유로든 놓쳐도 (추적 공백 등) 다음 신호에서 잡는다.
                    override fun onActivityStarted(activity: Activity) = fire(activity)
                    override fun onActivityResumed(activity: Activity) = fire(activity)

                    private fun fire(activity: Activity) {
                        app.unregisterActivityLifecycleCallbacks(this)
                        UMPCoordinator.requestConsentInfoUpdate(app, activity, consentTestSettings)
                    }

                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivityStopped(activity: Activity) {}
                    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                })
            }
        }
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

    /**
     * 저장된 동의 답을 지운다 — **시험용.**
     *
     * 동의창은 한 번 답하면 다음 실행부터 안 뜨므로, 뜨는 모습을 다시 확인하려면
     * 이걸 부르고 앱을 재시작한다. `consentTestSettings` 와 짝으로 쓴다.
     *
     * 출시 빌드에서는 무시된다 (경고 로그) — 실사용자의 동의 기록을 지우는 사고 방지.
     *
     * @param context 아무 context. 내부에서 application context 로 바꿔 쓴다.
     */
    public fun resetConsentForTesting(context: Context) {
        UMPCoordinator.resetForTesting(context)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 광고 ID 해석 (모듈 내부 + Composable 이 사용)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 배너 광고 ID (해석된 값). null 이면 광고 미표시.
     *
     * @param placement configure 의 `bannerAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 자리.
     */
    public fun resolvedBannerAdUnitID(context: Context, placement: String? = null): String? =
        selectAdUnitID(bannerAdUnitIDs, placement, "배너")
            .resolve(context, AdMobTestUnitID.BANNER)

    /** 전면 광고 ID. null 이면 광고 미표시. */
    public fun resolvedInterstitialAdUnitID(context: Context, placement: String? = null): String? =
        selectAdUnitID(interstitialAdUnitIDs, placement, "전면")
            .resolve(context, AdMobTestUnitID.INTERSTITIAL)

    /** 리워드 광고 ID. null 이면 광고 미표시. */
    public fun resolvedRewardedAdUnitID(context: Context, placement: String? = null): String? =
        selectAdUnitID(rewardedAdUnitIDs, placement, "리워드")
            .resolve(context, AdMobTestUnitID.REWARDED)

    /** Native 광고 ID. null 이면 광고 미표시. */
    public fun resolvedNativeAdUnitID(context: Context, placement: String? = null): String? =
        selectAdUnitID(nativeAdUnitIDs, placement, "Native")
            .resolve(context, AdMobTestUnitID.NATIVE)

    /**
     * 자리 이름으로 광고 ID 한 쌍을 고른다.
     *
     * 규칙
     *  - 자리 이름이 null → DEFAULT_PLACEMENT 키의 ID (기본 자리).
     *  - 자리 이름이 등록되어 있음 → 그 자리의 ID.
     *  - 자리 이름이 등록 안 됨 → 경고 로그 후 기본 자리 ID 로 대체 (크래시 없음 — 인디 앱 특성상 관대하게).
     *  - 그래도 없으면 빈 AdUnitID — resolve 단계에서 null (미표시) 로 떨어진다.
     *
     * internal — 단위 검증에서 직접 호출.
     */
    internal fun selectAdUnitID(
        placements: Map<String, AdUnitID>,
        placement: String?,
        format: String
    ): AdUnitID {
        val key = placement ?: DEFAULT_PLACEMENT
        placements[key]?.let { return it }
        if (placement != null) {
            IKLogger.ads.warning("등록 안 된 $format 광고 자리 '$placement' — 기본 자리로 대체합니다.")
            placements[DEFAULT_PLACEMENT]?.let { return it }
        }
        return AdUnitID()
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
