/*
 * InterstitialAdLoader.kt — IndieKitAds
 *
 * 역할
 *  - 전면 광고 (interstitial) 의 미리 적재 + 표시 + 닫힘 후 자동 다음 적재.
 *  - iOS 자매 (`InterstitialAdLoader`) 와 같은 흐름 — preload → showIfLoaded → dismiss 콜백 → 다음 preload.
 *
 * 주요 개념
 *  - 자리 (placement) 마다 적재기 한 개 — `loader(placement)` 보관소에서 꺼내 쓴다. null 은 기본 자리.
 *  - `preload()` 후 `showIfLoaded(...)` 패턴: 미리 적재해 두고 결정적인 순간에 즉시 표시.
 *  - 광고 닫힘 → 자동으로 다음 광고 적재 → 다음 표시 시점에 또 즉시.
 *  - 적재 안 된 상태에서 `showIfLoaded` 호출 시: 즉시 onDismiss 호출 (사용자 흐름 막지 않음).
 *
 * 주의사항
 *  - AdMob 콜백은 메인 스레드 보장 — 추가 Handler 진입 불필요.
 *  - external 노출은 IndieKitAds.showInterstitial 한 곳을 통해서만. 이 class 는 internal.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.content.Context
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

        /** 자리 이름에 해당하는 적재기를 꺼낸다. 없으면 만들어 보관. */
        fun loader(placement: String?): InterstitialAdLoader =
            loaders.getOrPut(placement ?: DEFAULT_KEY) { InterstitialAdLoader(placement) }
    }

    /** 적재된 전면 광고 인스턴스. */
    @Volatile
    private var ad: InterstitialAd? = null

    /** 광고가 적재 완료 상태인지. */
    val isLoaded: Boolean
        get() = ad != null

    /** 광고 닫힘 이후 호출할 콜백. */
    private var dismissCallback: (() -> Unit)? = null

    // ────────────────────────────────────────────────────────────────────────
    // 미리 적재
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 이 자리의 전면 광고 한 개를 미리 적재.
     * 앱 시작 시 (`IndieKitAds.configure` 안에서) 기본 자리 + 등록한 모든 자리가 자동 호출됨.
     * 이후 광고 닫힘 콜백에서도 자동 호출되어 다음 광고를 항상 준비된 상태로 유지.
     */
    fun preload(context: Context) {
        // 출시 빌드인데 운영 ID 가 없으면 null — 적재하지 않는다. isLoaded 가 false 라 showIfLoaded 는 즉시 통과.
        val adUnitID = IndieKitAds.resolvedInterstitialAdUnitID(context, placement)
        if (adUnitID == null) {
            IKLogger.ads.debug("전면 광고 ID 없음 — 적재 안 함")
            ad = null
            return
        }
        InterstitialAd.load(
            context.applicationContext,
            adUnitID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loaded.fullScreenContentCallback = makeFullScreenCallback(context)
                    IKLogger.ads.info("전면 광고 적재 성공 (자리: ${placement ?: "기본"})")
                    AnalyticsBus.record("ad_loaded", adAnalyticsParams("interstitial", placement))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    IKLogger.ads.warning("전면 광고 적재 실패: ${error.message}")
                }
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고가 적재되어 있으면 표시. 없으면 즉시 onDismiss 호출.
     *
     * @param activity 광고를 띄울 Activity. iOS 와 달리 안드로이드는 자동 탐색 없음 — 명시 필수.
     * @param onDismiss 광고 닫힘 후 (또는 광고 없음 시 즉시) 실행할 콜백.
     */
    fun showIfLoaded(activity: Activity, onDismiss: () -> Unit) {
        val current = ad
        if (current == null) {
            IKLogger.ads.debug("전면 광고 미적재 — 흐름 통과")
            onDismiss()
            return
        }
        dismissCallback = onDismiss
        ad = null  // 같은 광고를 두 번 띄우지 않도록 즉시 비우기.
        current.show(activity)
        IKLogger.ads.info("전면 광고 표시")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 콜백
    // ────────────────────────────────────────────────────────────────────────

    private fun makeFullScreenCallback(context: Context): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", adAnalyticsParams("interstitial", placement))
            }

            override fun onAdDismissedFullScreenContent() {
                AnalyticsBus.record("ad_dismissed", adAnalyticsParams("interstitial", placement))
                val callback = dismissCallback
                dismissCallback = null
                // 다음 광고 미리 적재.
                preload(context)
                callback?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                IKLogger.ads.warning("전면 광고 표시 실패: ${error.message}")
                val callback = dismissCallback
                dismissCallback = null
                preload(context)
                callback?.invoke()
            }
        }
    }
}
