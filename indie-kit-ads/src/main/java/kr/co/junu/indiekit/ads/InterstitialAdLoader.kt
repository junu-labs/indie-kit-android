/*
 * InterstitialAdLoader.kt — IndieKitAds
 *
 * 역할
 *  - 전면 광고 (interstitial) 의 요청 + 표시.
 *  - iOS 자매 (`InterstitialAdLoader`) 와 같은 흐름 — prepare → showIfLoaded → dismiss 콜백.
 *
 * 주요 개념
 *  - 자리 (placement) 마다 적재기 한 개 — `loader(placement)` 보관소에서 꺼내 쓴다. null 은 기본 자리.
 *  - **필요할 때만 요청** (2026-10, FullScreenAdState.kt 머리말):
 *    앱 시작 때 미리 받지 않는다. `prepare()` (앱이 "곧 띄울 것 같다" 고 알려 줌) 또는
 *    `showIfLoaded` 때 쓸 광고가 없으면 그때 한 개 요청해 다음 차례에 쓴다.
 *  - 요청 중이면 겹쳐 보내지 않고, 받은 지 55분이 지난 광고는 띄우지 않고 버린다 (노출이 안 잡힘).
 *  - 적재 안 된 상태에서 `showIfLoaded` 호출 시: 즉시 onDismiss 호출 (사용자 흐름 막지 않음).
 *
 * 주의사항
 *  - AdMob 콜백은 메인 스레드 보장. prepare / showIfLoaded 도 메인 스레드에서 부른다
 *    (IndieKitAds 의 공개 함수가 맞춰 준다).
 *  - external 노출은 IndieKitAds 를 통해서만. 이 class 는 internal.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** 전면 광고 매니저. 자리 (placement) 마다 한 개. */
internal class InterstitialAdLoader private constructor(
    /** 이 적재기가 담당하는 자리 이름. null = 기본 자리. */
    private val placement: String?
) {

    companion object {
        /** 기본 자리 (placement null) 의 보관 키. */
        private const val DEFAULT_KEY = "__default__"

        /** 자리 이름 → 적재기 보관소. */
        private val loaders = java.util.concurrent.ConcurrentHashMap<String, InterstitialAdLoader>()

        /**
         * 자리 이름에 해당하는 적재기를 꺼낸다. 없으면 만들어 보관.
         *
         * 자리 이름을 안 넘긴 것 (null) 과 [IndieKitAds.DEFAULT_PLACEMENT] ("default") 은
         * **같은 자리** 라 한 칸을 같이 쓴다. 칸을 나누면 광고 번호를 고르는 자리가 둘 다
         * "default" 키를 보므로, 같은 광고 번호로 요청을 두 번 보내고 그중 하나는
         * 화면에서 부를 길이 없어 영영 안 쓰인다.
         */
        fun loader(placement: String?): InterstitialAdLoader {
            val key = if (placement == null || placement == IndieKitAds.DEFAULT_PLACEMENT) {
                DEFAULT_KEY
            } else {
                placement
            }
            return loaders.getOrPut(key) { InterstitialAdLoader(placement) }
        }

        /** 기기가 켜진 뒤 흐른 시간 — 사용자가 시계를 바꿔도 흔들리지 않는다. */
        private fun now(): Long = SystemClock.elapsedRealtime()
    }

    /** 적재된 (또는 지금 떠 있는) 전면 광고 인스턴스. */
    @Volatile
    private var ad: InterstitialAd? = null

    /** 요청 · 적재 · 오래됨 상태 (SDK 와 떨어진 순수 판단). */
    @Volatile
    private var state = FullScreenAdState()

    /** 띄울 수 있는 광고가 있는지 (오래된 광고는 아님). */
    val isLoaded: Boolean
        get() = ad != null && state.isReady(now())

    /** 광고 닫힘 이후 호출할 콜백. */
    private var dismissCallback: (() -> Unit)? = null

    // ────────────────────────────────────────────────────────────────────────
    // 요청
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 쓸 광고가 없으면 한 개 요청한다. 요청 중 · 표시 중 · 이미 있으면 아무 일 안 함.
     * 광고 SDK 시작이 아직 안 끝났으면 끝난 뒤로 미룬다. 메인 스레드에서 부른다.
     */
    fun prepare(context: Context) = prepare(context, null)

    /** 받기 결과를 기다리는 쪽 (`loadAndShow`). true = 받음, false = 못 받음. 화면 그리는 실에서만 만진다. */
    private val loadWaiters = mutableListOf<(Boolean) -> Unit>()

    /** [prepare] + 결과 알림. 이미 받아 둔 광고가 있으면 곧바로 true, 요청을 못 보내면 곧바로 false. */
    private fun prepare(context: Context, waiter: ((Boolean) -> Unit)?) {
        if (isLoaded) { waiter?.invoke(true); return }
        if (state.isLoading) { waiter?.let { loadWaiters += it }; return }
        val nowMillis = now()
        if (!state.shouldRequest(nowMillis)) {
            if (state.isBackingOff(nowMillis)) IKLogger.ads.debug("전면 광고 — 방금 실패해 잠시 다시 요청하지 않음")
            waiter?.invoke(false)
            return
        }
        if (!IndieKitAds.isSdkReady) {
            waiter?.let { loadWaiters += it }
            IndieKitAds.runAfterSdkReady { prepare(context, null) }
            return
        }
        // 출시 빌드인데 운영 ID 가 없으면 null — 요청하지 않는다. isLoaded 가 false 라 showIfLoaded 는 즉시 통과.
        val adUnitID = IndieKitAds.resolvedInterstitialAdUnitID(context, placement)
        if (adUnitID == null) {
            IKLogger.ads.debug("전면 광고 ID 없음 — 요청 안 함")
            waiter?.invoke(false)
            flushWaiters(false)
            return
        }
        waiter?.let { loadWaiters += it }
        ad = null
        state = state.requested()
        InterstitialAd.load(
            context.applicationContext,
            adUnitID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loaded.fullScreenContentCallback = makeFullScreenCallback(context)
                    state = state.loaded(now())
                    IKLogger.ads.info("전면 광고 적재 성공 (자리: ${placement ?: "기본"})")
                    AnalyticsBus.record("ad_loaded", adAnalyticsParams("interstitial", placement))
                    flushWaiters(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    state = state.failed(now())
                    IKLogger.ads.warning("전면 광고 적재 실패: ${error.message}")
                    flushWaiters(false)
                }
            }
        )
    }

    private fun flushWaiters(ok: Boolean) {
        val waiters = loadWaiters.toList()
        loadWaiters.clear()
        waiters.forEach { it(ok) }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 받아서 곧바로 띄우기
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 받아 둔 광고가 있으면 곧바로 띄우고, 없으면 지금 요청해 받는 대로 띄운다. 화면 그리는 실에서 부른다.
     * [timeoutMillis] 안에 못 받거나 받기에 실패하면 띄우지 않고 onDismiss. 늦게 온 광고는 다음 차례에 쓴다.
     * 다음 차례용으로 미리 받지 않는다 — 요청한 광고는 (받으면) 곧바로 보여지므로 버려지는 요청이 없다.
     */
    fun loadAndShow(activity: Activity, timeoutMillis: Long, onDismiss: () -> Unit) {
        if (isLoaded) { showIfLoaded(activity, onDismiss); return }
        val wait = LoadAndShowWait()
        prepare(activity.applicationContext) { ok ->
            when (if (ok) wait.adLoaded() else wait.adFailed()) {
                LoadAndShowWait.Outcome.SHOW ->
                    if (isLoaded && !activity.isFinishing && !activity.isDestroyed) showIfLoaded(activity, onDismiss)
                    else onDismiss()
                LoadAndShowWait.Outcome.GIVE_UP -> onDismiss()
                null -> Unit
            }
        }
        if (wait.isFinished) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (wait.timedOut() != null) {
                IKLogger.ads.info("전면 광고 — ${timeoutMillis}ms 안에 못 받아 이번엔 건너뜀")
                onDismiss()
            }
        }, timeoutMillis.coerceAtLeast(0L))
    }

    // ────────────────────────────────────────────────────────────────────────
    // 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고가 적재되어 있으면 표시. 없으면 즉시 onDismiss 호출하고, 다음 차례에 쓸 광고를 요청.
     *
     * @param activity 광고를 띄울 Activity. iOS 와 달리 안드로이드는 자동 탐색 없음 — 명시 필수.
     * @param onDismiss 광고 닫힘 후 (또는 광고 없음 시 즉시) 실행할 콜백.
     */
    fun showIfLoaded(activity: Activity, onDismiss: () -> Unit) {
        val current = ad
        val nowMillis = now()
        if (current == null || !state.isReady(nowMillis)) {
            if (state.isExpired(nowMillis)) {
                IKLogger.ads.info("전면 광고가 오래됨 (55분 넘음) — 버리고 새로 요청")
                state = state.discarded()
                ad = null
            } else {
                IKLogger.ads.debug("전면 광고 미적재 — 흐름 통과, 다음 차례용 요청")
            }
            prepare(activity.applicationContext)
            onDismiss()
            return
        }
        dismissCallback = onDismiss
        state = state.presenting()  // 같은 광고를 두 번 띄우지 않도록 즉시 비우기.
        ad = null
        current.show(activity)
        IKLogger.ads.info("전면 광고 표시")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 콜백
    // ────────────────────────────────────────────────────────────────────────

    /** 닫힘 / 표시 실패 뒤 정리 — 사용자 콜백을 부른다. 다음 광고는 다음 차례에 (FullScreenAdPolicy). */
    private fun finishPresentation(context: Context) {
        state = state.dismissed()
        val callback = dismissCallback
        dismissCallback = null
        if (FullScreenAdPolicy.RELOADS_AFTER_DISMISS) prepare(context)
        callback?.invoke()
    }

    private fun makeFullScreenCallback(context: Context): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", adAnalyticsParams("interstitial", placement))
            }

            override fun onAdDismissedFullScreenContent() {
                AnalyticsBus.record("ad_dismissed", adAnalyticsParams("interstitial", placement))
                finishPresentation(context)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                IKLogger.ads.warning("전면 광고 표시 실패: ${error.message}")
                finishPresentation(context)
            }
        }
    }
}
