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
 *  // Application.onCreate 에서 한 번 — 광고 종류마다 ID 목록. 안 쓰는 종류는 빈 목록
 *  // (2026-10 — 옛 자리 이름 묶음은 @Deprecated):
 *  IndieKitAds.configure(
 *      context = this,
 *      bannerIDs = listOf("ca-app-pub-.../111", "ca-app-pub-.../222"),  // 첫 ID = 자리 이름 없이 부르는 배너
 *      interstitialIDs = listOf("ca-app-pub-.../333"),
 *      rewardedIDs = emptyList(),                                        // 보상 광고 안 씀
 *      requestConsent = true
 *  )
 *
 *  // 화면 안에서 (Compose):
 *  if (!subscription.isPro) {
 *      BannerAdView(modifier = Modifier.fillMaxWidth())                                    // 목록의 첫 배너 ID
 *      BannerAdView(modifier = Modifier.fillMaxWidth(), placement = "ca-app-pub-.../222")  // 두 번째 ID 는 그 ID 를 자리 이름으로
 *  }
 *
 *  // 운동 마지막 세트에 들어갈 때 (띄울 차례가 가까울 때만 — 앱 시작 때 부르지 않는다):
 *  IndieKitAds.prepareInterstitial()
 *
 *  // 운동 완료 시점 (받아 둔 광고가 없으면 건너뛰고, 다음 차례용으로 받아 둔다):
 *  IndieKitAds.showInterstitial(activity) {
 *      // 광고 닫힘 후 실행
 *  }
 *
 *  // "광고 보고 힌트 받기" 단추가 있는 화면이 열릴 때:
 *  IndieKitAds.prepareRewarded()
 *
 *  // 사용자가 "광고 보고 힌트 받기" 눌렀을 때:
 *  IndieKitAds.showRewarded(activity) { reward ->
 *      if (reward != null) hintCount += reward.amount
 *  }
 *
 *  // (2026-10) 드문 차례에는 미리 받지 말고 누른 순간 받아서 띄운다 — 버려지는 요청이 없다:
 *  IndieKitAds.loadAndShowRewarded(activity) { reward -> … }   // 못 받으면 (8초) null
 *  IndieKitAds.loadAndShowInterstitial(activity) { … }         // 못 받으면 (5초) 건너뜀
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
    // null = 이 앱은 그 종류의 광고를 안 쓴다 (새 configure 에 빈 목록을 넘김) — 디버그에서도 시험 광고조차 안 띄운다.
    // emptyMap = 옛 configure 에서 안 넘긴 것 — 예전처럼 디버그는 시험 광고, 출시는 미표시.
    private var bannerAdUnitIDs: Map<String, AdUnitID>? = emptyMap()
    private var interstitialAdUnitIDs: Map<String, AdUnitID>? = emptyMap()
    private var rewardedAdUnitIDs: Map<String, AdUnitID>? = emptyMap()
    private var nativeAdUnitIDs: Map<String, AdUnitID>? = emptyMap()

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

    /** 광고 SDK 준비 (= 유럽 동의 확인 뒤 시작 완료) 를 화면이 지켜볼 수 있게 담아 두는 자리. */
    private val sdkReadyRevision = mutableIntStateOf(0)

    /**
     * 화면 (Composable) 이 읽는 "광고를 요청해도 되는가".
     * 동의 확인 → SDK 시작이 끝나기 전에는 false — 배너 / Native 는 빈 자리로 기다린다.
     */
    internal val isAdsReadyForCompose: Boolean
        get() = sdkReadyRevision.intValue > 0

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
     *  3. 동의가 광고 요청을 허락하면 (`canRequestAds`) MobileAds.initialize 호출 (AdMob SDK 시작) — **뒷실에서**.
     *     구글 권고 순서 (2026-10). 지난 실행에서 받은 동의가 있으면 2 를 기다리지 않고 곧바로.
     *     배너 / Native 도 SDK 준비 뒤에야 광고를 요청한다 (그 전엔 빈 자리).
     *
     * 전면 / 리워드 광고는 여기서 **미리 받지 않는다** (2026-10 — 안 보일 광고 요청이 일치율 · 노출률을 깎았다).
     * 띄울 차례가 가까우면 [prepareInterstitial] / [prepareRewarded] 를 부르거나,
     * 그냥 [showInterstitial] 을 부르면 처음엔 건너뛰고 다음 차례용으로 받아 둔다.
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
    @Deprecated(
        "configure(context, bannerIDs, interstitialIDs, rewardedIDs) 를 쓰세요 — 광고 종류마다 ID 목록만 넘기고, 안 쓰는 종류는 emptyList()."
    )
    public fun configure(
        context: Context,
        bannerAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        interstitialAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        rewardedAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        nativeAdUnitIDs: Map<String, AdUnitID> = emptyMap(),
        requestConsent: Boolean = true,
        consentTestSettings: ConsentTestSettings? = null
    ) {
        start(
            context, bannerAdUnitIDs, interstitialAdUnitIDs, rewardedAdUnitIDs, nativeAdUnitIDs,
            requestConsent, consentTestSettings
        )
    }

    /**
     * 광고 모듈을 처음 설정한다 — 광고 종류마다 **광고 ID 목록** 을 받는다. (2026-10, 옛 자리 이름 묶음 대신)
     *
     * "기본 광고 ID" 는 따로 없다. 종류마다 목록의 **첫 ID** 가 자리 이름 없이 부르는 광고
     * (`BannerAdView()`, `showInterstitial(activity)` …) 이고, 두 번째부터는 그 ID 글자를 자리 이름으로 넘겨 부른다
     * (`BannerAdView(placement = "ca-app-pub-…/222")`).
     *
     * 안 쓰는 종류는 **빈 목록** — 그 종류는 디버그에서도 아무 광고를 요청하지 않는다.
     * 쓰는데 출시 ID 가 아직 없으면 빈 글자를 넣는다 (`listOf(BuildConfig.AD_BANNER_ID)` 의 값이 "")
     * — 디버그는 시험 광고, 출시는 미표시.
     *
     * 실 규칙 · 부르는 시점 · 동의 → SDK 시작 순서 · 전면 / 리워드를 미리 받지 않는 것은 옛 configure 와 같다.
     *
     * ```kotlin
     * IndieKitAds.configure(
     *     context = app,
     *     bannerIDs = listOf(BuildConfig.AD_BANNER_ID),
     *     interstitialIDs = listOf(BuildConfig.AD_INTERSTITIAL_ID),
     *     rewardedIDs = emptyList(),          // 보상 광고를 안 쓰는 앱
     *     requestConsent = true)
     * ```
     *
     * @param context Application context.
     * @param bannerIDs 배너 광고 ID 목록. 안 쓰면 emptyList().
     * @param interstitialIDs 전면 광고 ID 목록. 안 쓰면 emptyList().
     * @param rewardedIDs 보상 (리워드) 광고 ID 목록. 안 쓰면 emptyList().
     * @param nativeIDs Native 광고 ID 목록. 기본 비어 있음 (안 씀).
     * @param requestConsent true 면 UMP 동의창 자동 시작. EEA 외 사용자엔 영향 없음.
     * @param consentTestSettings 시험용 나라 강제 설정 — 디버그 빌드에서만 반영.
     */
    public fun configure(
        context: Context,
        bannerIDs: List<String>,
        interstitialIDs: List<String>,
        rewardedIDs: List<String>,
        nativeIDs: List<String> = emptyList(),
        requestConsent: Boolean = true,
        consentTestSettings: ConsentTestSettings? = null
    ) {
        start(
            context,
            placementMap(bannerIDs),
            placementMap(interstitialIDs),
            placementMap(rewardedIDs),
            placementMap(nativeIDs),
            requestConsent, consentTestSettings
        )
    }

    /** 두 configure 가 같이 쓰는 몸통. 묶음이 null 이면 그 종류는 안 쓴다. */
    private fun start(
        context: Context,
        bannerAdUnitIDs: Map<String, AdUnitID>?,
        interstitialAdUnitIDs: Map<String, AdUnitID>?,
        rewardedAdUnitIDs: Map<String, AdUnitID>?,
        nativeAdUnitIDs: Map<String, AdUnitID>?,
        requestConsent: Boolean,
        consentTestSettings: ConsentTestSettings?
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
        consentRequested = requestConsent

        // ── 1. 동의 절차를 먼저 건다 ──────────────────────────────────────────
        // 화면이 이미 떠 있으면 그 화면으로 바로, 아직 없으면 첫 화면이 잡히는 순간
        // 진행한다 (tryAutoRequestConsent 안에서 갈라짐). 어느 쪽이든 오래 걸리는 일이
        // 아니라 화면을 붙잡지 않는다. 절차가 끝나면 (창 닫힘 / 창 필요 없음 / 갱신 실패)
        // 동의가 허락하는지 보고 SDK 를 시작한다.
        val consentFlowScheduled = requestConsent && tryAutoRequestConsent(context)

        // ── 2. 동의가 허락하면 SDK 시작 (구글 권고 순서, 2026-10) ────────────────
        // 예전 (0.9.3 ~) 에는 여기서 곧바로 SDK 를 시작해 동의 절차와 나란히 갔다 —
        // 동의 전에 광고 요청이 나갈 틈이 있었다. 이제는 동의가 허락할 때만 시작한다:
        //  · 동의 절차를 끈 앱 → 곧바로 (예전과 같음)
        //  · 지난 실행에서 받은 동의가 있으면 → 절차 결과를 기다리지 않고 곧바로 (다시 켜는 사람은 늦지 않음)
        //  · 아니면 → 절차가 끝난 순간 (onConsentFlowFinished)
        // 동의 절차를 못 거는 별난 context (Application 이 아님) 면 예전처럼 곧바로 시작하고 경고만 남긴다.
        if (requestConsent && !consentFlowScheduled) {
            IKLogger.ads.warning("유럽 동의 절차를 걸 수 없어 동의를 기다리지 않고 광고 SDK 를 시작합니다 — configure 에 Application context 를 넘겨 주세요.")
            consentRequested = false
        }
        // 저장된 동의 읽기도 configure 를 부른 순간 (Application.onCreate 일 수 있음) 이 아니라
        // 화면 그리는 실의 다음 차례로 미뤄, 부른 쪽을 조금도 붙잡지 않는다.
        Handler(Looper.getMainLooper()).post { startSdkIfAllowed(appCtx) }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 유럽 동의 → 광고 SDK 시작
    // ────────────────────────────────────────────────────────────────────────

    /** 앱이 동의 절차를 켰는지 (configure 의 requestConsent). 시작 문지기가 본다. */
    @Volatile
    private var consentRequested: Boolean = true

    /** 광고 SDK 시작 문지기 — 한 번만, 동의가 허락할 때만 연다. */
    private val startGate = AdsStartGate()

    /** 동의 절차가 끝났을 때 (UMPCoordinator 가 부른다). 화면 그리는 실. */
    internal fun onConsentFlowFinished() {
        val ctx = appContext ?: return
        startSdkIfAllowed(ctx)
    }

    /**
     * 동의가 허락하면 (그리고 아직 안 했으면) 광고 SDK 를 시작한다. 화면 그리는 실에서 부른다.
     *
     * SDK 시작 자체는 **뒷실에서** (0.9.3 고침 그대로):
     * MobileAds.initialize 는 Google Play 서비스에 붙고 광고 설정을 그물 너머에서 받아온다.
     * 특히 깔고 나서 첫 실행에서는 수백 밀리초 이상 걸린다. 화면 그리는 실에서 부르면
     * 첫 화면을 그리지 못한 채 폰이 "응답 없음" 으로 앱을 끈다.
     * (매니페스트 OPTIMIZE_INITIALIZATION 표시는 SDK 24.0.0 부터 이미 기본으로 켜져 있지만,
     *  구글 설명대로 시작 일의 *일부* 만 옮길 뿐이라 이 뒷실이 여전히 필요하다.)
     * 출처: https://developers.google.com/admob/android/quick-start
     *
     * 코루틴 대신 Thread 를 쓰는 까닭: 이 모듈은 kotlinx-coroutines 를 자기 의존성으로
     * 선언하지 않는다 (Compose 가 딸려 오는 것에 얹혀 있을 뿐). 한 번 쓰고 끝나는 일에
     * 남의 짐을 기대지 않는다.
     */
    private fun startSdkIfAllowed(appCtx: Context) {
        val requestConsent = consentRequested
        val canRequestAds = !requestConsent || UMPCoordinator.canRequestAds(appCtx)
        if (!startGate.tryStart(requestConsent, canRequestAds)) return

        IKLogger.ads.info("광고 SDK 시작 (동의 확인 뒤)")
        Thread({
            MobileAds.initialize(appCtx) {
                IKLogger.ads.info("MobileAds.initialize 완료")
                // 광고를 불러오는 일은 화면 그리는 실에서 한다 (구글 광고 묶음 규칙).
                // 위에서 뒷실로 옮겼으므로 여기서 화면 그리는 실로 되돌린다.
                Handler(Looper.getMainLooper()).post { onSdkReady(appCtx) }
            }
        }, "indie-kit-ads-init").start()
    }

    // ────────────────────────────────────────────────────────────────────────
    // SDK 시작 뒤로 미루기 (화면 그리는 실에서만 만진다)
    // ────────────────────────────────────────────────────────────────────────

    /** MobileAds.initialize 가 끝났는지. */
    @Volatile
    internal var isSdkReady: Boolean = false
        private set

    /** SDK 시작 전에 들어온 요청 — 시작 뒤 한 번에 처리. */
    private val afterSdkReady = mutableListOf<() -> Unit>()

    /** SDK 가 준비돼 있으면 곧바로, 아니면 준비 뒤에 한 번 실행. 화면 그리는 실에서 부른다. */
    internal fun runAfterSdkReady(work: () -> Unit) {
        if (isSdkReady) { work(); return }
        afterSdkReady += work
    }

    /**
     * SDK 준비 끝. 미뤄 둔 요청을 처리한다.
     *
     * 전면 / 리워드는 켤 때 미리 받지 않는다 ([FullScreenAdPolicy.PRELOADS_ON_CONFIGURE]).
     * 예전에는 여기서 등록한 자리를 모두 받아 두었는데, 대부분의 실행에서 한 번도 안 보여
     * AdMob 의 요청 수만 늘고 노출 / 일치율이 낮게 잡혔다.
     */
    private fun onSdkReady(appCtx: Context) {
        isSdkReady = true
        // 기다리던 배너 / Native 가 이 순간 스스로 다시 그려져 광고를 요청한다.
        sdkReadyRevision.intValue += 1
        val pending = afterSdkReady.toList()
        afterSdkReady.clear()
        pending.forEach { it() }

        if (FullScreenAdPolicy.PRELOADS_ON_CONFIGURE) {
            interstitialAdUnitIDs?.keys?.forEach { InterstitialAdLoader.loader(it).prepare(appCtx) }
            rewardedAdUnitIDs?.keys?.forEach { RewardedAdLoader.loader(it).prepare(appCtx) }
        }
    }

    /** 화면 그리는 실에서 실행 — 이미 그 실이면 곧바로. */
    private fun onMain(work: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) work()
        else Handler(Looper.getMainLooper()).post(work)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 미리 받기 (앱이 고르는 때)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 전면 광고를 곧 띄울 것 같을 때 미리 받아 둔다 — 띄울 차례에 바로 뜨게.
     *
     * 예: 오늘 할 일이 하나 남았을 때, 마지막 단계 화면이 열릴 때.
     * 이미 받아 뒀거나 받는 중이면 아무 일 안 한다 (여러 번 불러도 요청은 한 번).
     * 앱 시작 직후처럼 "띄울지 모를 때" 부르면 안 보일 광고를 요청하게 되어 일치율이 다시 떨어진다.
     * 어느 실에서 불러도 된다.
     *
     * @param placement configure 의 `interstitialAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 자리.
     */
    public fun prepareInterstitial(placement: String? = null) {
        val ctx = appContext
        if (!initializedFlag || ctx == null) {
            IKLogger.ads.warning("IndieKitAds.configure 전에 prepareInterstitial — 무시합니다.")
            return
        }
        onMain { InterstitialAdLoader.loader(placement).prepare(ctx) }
    }

    /**
     * 리워드 광고를 미리 받아 둔다 — "광고 보고 받기" 단추가 있는 화면이 열릴 때 부른다.
     * 어느 실에서 불러도 된다.
     *
     * @param placement configure 의 `rewardedAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 자리.
     */
    public fun prepareRewarded(placement: String? = null) {
        val ctx = appContext
        if (!initializedFlag || ctx == null) {
            IKLogger.ads.warning("IndieKitAds.configure 전에 prepareRewarded — 무시합니다.")
            return
        }
        onMain { RewardedAdLoader.loader(placement).prepare(ctx) }
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
     *
     * 절차가 끝나면 (어느 갈래든, 창을 띄웠든 아니든) [onConsentFlowFinished] 로 SDK 시작 여부를 다시 본다.
     *
     * @return 동의 절차를 걸었으면 true. 걸 수 없는 context 면 false.
     */
    private fun tryAutoRequestConsent(context: Context): Boolean {
        val app = context.applicationContext as? Application ?: run {
            IKLogger.ads.debug("자동 UMP 동의 흐름 건너뜀 — Application context 가 아님.")
            return false
        }

        // 추적자가 자동으로 안 심긴 별난 환경 대비 — 이미 심겼으면 아무 일 안 함.
        CurrentActivityTracker.install(app)

        Handler(Looper.getMainLooper()).post {
            val activityNow = CurrentActivityTracker.current
            if (activityNow != null) {
                // 갈래 1 — 화면이 이미 있다: 지금 바로 진행.
                UMPCoordinator.requestConsentInfoUpdate(app, activityNow, consentTestSettings, ::onConsentFlowFinished)
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
                        UMPCoordinator.requestConsentInfoUpdate(app, activity, consentTestSettings, ::onConsentFlowFinished)
                    }

                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivityStopped(activity: Activity) {}
                    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                })
            }
        }
        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    // 광고 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 전면 광고가 받아져 있으면 표시. 없으면 즉시 onDismiss 호출하고, 다음 차례에 쓸 광고를 그때 요청한다.
     * (첫 차례부터 띄우려면 그 전에 [prepareInterstitial] 을 불러 둔다.)
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
     * 리워드 광고가 받아져 있으면 표시. 사용자가 끝까지 시청하면 보상.
     * 없으면 null 로 즉시 onResult 하고 다음 차례용으로 요청한다 — 단추가 있는 화면에서 [prepareRewarded] 를 먼저 부른다.
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

    // ────────────────────────────────────────────────────────────────────────
    // 받아서 곧바로 띄우기 (2026-10 — 일치율 · 노출률)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 전면 광고를 **지금** 받아서 받는 대로 띄운다. 받아 둔 광고가 있으면 곧바로 띄운다.
     *
     * [showInterstitial] 은 받아 둔 광고가 없으면 그 차례를 건너뛰고 "다음 차례용" 광고를 요청하는데,
     * 다음 차례가 안 오면 (예: 하루 두 번째 레시피를 안 부름) 그 요청은 보여지지 않고 버려진다.
     * 드물게 오는 차례 (유료 기능 앞 · 하루 한두 번) 에는 이 함수를 쓴다 — 요청한 광고는 받으면 곧바로 보여지고,
     * 다음 차례용으로 미리 받지 않는다. [prepareInterstitial] 도 부르지 않는다.
     *
     * 받는 동안 (보통 1~2초) 앱은 흐름을 멈추고 "잠시만요" 같은 모습을 보이다가 onDismiss 에서 이어 간다.
     * [timeoutMillis] 안에 못 받거나 받기에 실패하면 띄우지 않고 onDismiss — 흐름은 막히지 않는다.
     * 방금 받기에 실패했으면 (채울 광고 없음) 잠시 (30초 ~ 10분) 다시 요청하지 않고 곧바로 onDismiss.
     * 어느 실에서 불러도 된다 (콜백은 화면 그리는 실).
     *
     * @param activity 광고를 띄울 Activity.
     * @param placement 전면 광고 자리 이름. null 이면 기본 자리.
     * @param timeoutMillis 광고를 기다리는 가장 긴 시간. 기본 5초.
     * @param onDismiss 광고 닫힘 후 (또는 못 받아 건너뛸 때) 실행할 콜백.
     */
    public fun loadAndShowInterstitial(
        activity: Activity,
        placement: String? = null,
        timeoutMillis: Long = 5_000L,
        onDismiss: () -> Unit = {}
    ) {
        if (!ensureConfigured()) {
            onDismiss()
            return
        }
        onMain { InterstitialAdLoader.loader(placement).loadAndShow(activity, timeoutMillis, onDismiss) }
    }

    /**
     * 리워드 광고를 **지금** 받아서 받는 대로 띄운다 — "광고 보고 받기" 단추를 누른 순간에 부른다.
     *
     * 단추가 있는 화면이 열릴 때 [prepareRewarded] 로 미리 받아 두면 사용자가 단추를 안 누를 때마다
     * 받은 광고가 버려진다. 이 함수를 쓰면 누른 사람에게만 요청하므로 요청한 광고가 (받으면) 모두 보여진다.
     * 받는 동안 앱은 단추를 "불러오는 중" 으로 두고, onResult 에서 결과를 본다.
     *
     * @param activity 광고를 띄울 Activity.
     * @param placement 리워드 광고 자리 이름. null 이면 기본 자리.
     * @param timeoutMillis 광고를 기다리는 가장 긴 시간. 기본 8초.
     * @param onResult 끝까지 봤으면 AdReward, 중간에 닫음 · 못 받음 · 시간 넘김이면 null.
     */
    public fun loadAndShowRewarded(
        activity: Activity,
        placement: String? = null,
        timeoutMillis: Long = 8_000L,
        onResult: (AdReward?) -> Unit
    ) {
        if (!ensureConfigured()) {
            onResult(null)
            return
        }
        onMain { RewardedAdLoader.loader(placement).loadAndShow(activity, timeoutMillis, onResult) }
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
        UMPCoordinator.presentForm(activity) { error ->
            // 이번 실행에서 처음 허락을 받았으면 (예: 첫 창을 놓쳤다가 설정에서 다시 답함) 이제 시작한다.
            if (initializedFlag) onConsentFlowFinished()
            onComplete(error)
        }
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
        bannerAdUnitIDs?.let { selectAdUnitID(it, placement, "배너").resolve(context, AdMobTestUnitID.BANNER) }

    /** 전면 광고 ID. null 이면 광고 미표시 (전면을 안 쓰는 앱이면 언제나 null). */
    public fun resolvedInterstitialAdUnitID(context: Context, placement: String? = null): String? =
        interstitialAdUnitIDs?.let { selectAdUnitID(it, placement, "전면").resolve(context, AdMobTestUnitID.INTERSTITIAL) }

    /** 리워드 광고 ID. null 이면 광고 미표시 (리워드를 안 쓰는 앱이면 언제나 null). */
    public fun resolvedRewardedAdUnitID(context: Context, placement: String? = null): String? =
        rewardedAdUnitIDs?.let { selectAdUnitID(it, placement, "리워드").resolve(context, AdMobTestUnitID.REWARDED) }

    /** Native 광고 ID. null 이면 광고 미표시 (Native 를 안 쓰는 앱이면 언제나 null). */
    public fun resolvedNativeAdUnitID(context: Context, placement: String? = null): String? =
        nativeAdUnitIDs?.let { selectAdUnitID(it, placement, "Native").resolve(context, AdMobTestUnitID.NATIVE) }

    /**
     * 광고 ID 목록 → 자리 이름 묶음. (새 configure 가 쓴다)
     *
     * 규칙
     *  - 빈 목록 → null: 이 종류의 광고는 안 쓴다. 띄우라고 해도 아무것도 안 한다 (디버그 시험 광고도 없음).
     *  - 목록의 첫 ID → 자리 이름 없이 (`placement = null`) 부르는 광고.
     *  - 빈 글자가 아닌 ID 는 그 글자 그대로를 자리 이름으로도 등록 — 두 번째 ID 부터는 `placement = "그 ID"` 로 부른다.
     *  - 빈 글자 ("") 는 "쓰긴 하는데 출시 ID 가 아직 없음" — 디버그는 시험 광고, 출시는 미표시.
     *
     * internal — 단위 검증에서 직접 호출. iOS 자매 (`placementMap(fromIDs:)`) 와 같은 규칙.
     */
    internal fun placementMap(ids: List<String>): Map<String, AdUnitID>? {
        if (ids.isEmpty()) return null
        val map = LinkedHashMap<String, AdUnitID>()
        ids.forEachIndexed { index, raw ->
            val id = raw.trim()
            val unit = AdUnitID(release = id.ifEmpty { null })
            if (index == 0) map[DEFAULT_PLACEMENT] = unit
            if (id.isNotEmpty() && id !in map) map[id] = unit
        }
        return map
    }

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
