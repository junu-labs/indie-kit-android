/*
 * InterstitialAdLoader.kt — IndieKitAds
 *
 * 역할
 *  - 전면 광고 (interstitial) 의 미리 적재 + 표시 + 닫힘 후 자동 다음 적재.
 *  - iOS 자매 (`InterstitialAdLoader`) 와 같은 흐름 — preload → showIfLoaded → dismiss 콜백 → 다음 preload.
 *
 * 주요 개념
 *  - object 싱글턴 — 광고 한 개 인스턴스를 앱 전반에서 공유.
 *  - `preload()` 후 `showIfLoaded(...)` 패턴: 미리 적재해 두고 결정적인 순간에 즉시 표시.
 *  - 광고 닫힘 → 자동으로 다음 광고 적재 → 다음 표시 시점에 또 즉시.
 *  - 적재 안 된 상태에서 `showIfLoaded` 호출 시: 즉시 onDismiss 호출 (사용자 흐름 막지 않음).
 *
 * 주의사항
 *  - AdMob 콜백은 메인 스레드 보장 — 추가 Handler 진입 불필요.
 *  - external 노출은 IndieKitAds.showInterstitial 한 곳을 통해서만. 이 object 는 internal.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.content.Context
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import kr.co.junu.indiekit.core.analyticsParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/** 전면 광고 매니저. object 싱글턴. */
internal object InterstitialAdLoader {

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
     * 전면 광고 한 개를 미리 적재.
     * 앱 시작 시 (`IndieKitAds.configure` 안에서) 자동 호출.
     * 이후 광고 닫힘 콜백에서도 자동 호출되어 다음 광고를 항상 준비된 상태로 유지.
     */
    fun preload(context: Context) {
        val adUnitID = IndieKitAds.resolvedInterstitialAdUnitID(context)
        InterstitialAd.load(
            context.applicationContext,
            adUnitID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loaded.fullScreenContentCallback = makeFullScreenCallback(context)
                    IKLogger.ads.info("전면 광고 적재 성공")
                    AnalyticsBus.record("ad_loaded", analyticsParams("format" to "interstitial"))
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
                AnalyticsBus.record("ad_impression", analyticsParams("format" to "interstitial"))
            }

            override fun onAdDismissedFullScreenContent() {
                AnalyticsBus.record("ad_dismissed", analyticsParams("format" to "interstitial"))
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
