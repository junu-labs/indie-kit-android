/*
 * RewardedAdLoader.kt — IndieKitAds
 *
 * 역할
 *  - 리워드 광고 (rewarded) 의 미리 적재 + 표시 + 보상 콜백.
 *  - 사용자가 자발적으로 광고 보고 보상 받는 시나리오.
 *
 * 주요 개념
 *  - 리워드 광고 = 사용자가 끝까지 보면 보상 (코인 / 힌트 / 일시 잠금 해제 등) 을 주는 광고.
 *  - `showRewarded` 시 `onResult(AdReward?)` 형태로 결과 통지:
 *     - 사용자가 끝까지 봄 → AdReward 전달 (보상 지급)
 *     - 중단 / 광고 미적재 → null 전달 (보상 없음, 흐름은 계속)
 *  - 광고 닫힘 → 자동 다음 광고 적재.
 *  - 보상 콜백 (OnUserEarnedRewardListener) 과 닫힘 콜백 (FullScreenContentCallback.onAdDismissed) 의
 *    순서가 분리되어 있어 둘 다 본 다음 onResult 를 호출한다 (iOS 자매와 같은 방식).
 *
 * iOS 자매 (`RewardedAdLoader`) 와 같은 흐름.
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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** 리워드 광고 매니저. object 싱글턴. */
internal object RewardedAdLoader {

    /** 적재된 리워드 광고 인스턴스. */
    @Volatile
    private var ad: RewardedAd? = null

    /** 광고가 적재 완료 상태인지. */
    val isLoaded: Boolean
        get() = ad != null

    /** 광고 종료 후 호출할 콜백 (보상 또는 null). */
    private var resultCallback: ((AdReward?) -> Unit)? = null

    /**
     * 사용자가 보상받았는지 표식. didEarnReward 콜백과 onAdDismissed 의 순서가
     * 분리되어 있어 둘 다 본 다음 resultCallback 을 호출해야 한다.
     */
    private var earnedReward: AdReward? = null

    // ────────────────────────────────────────────────────────────────────────
    // 미리 적재
    // ────────────────────────────────────────────────────────────────────────

    fun preload(context: Context) {
        val adUnitID = IndieKitAds.resolvedRewardedAdUnitID(context)
        RewardedAd.load(
            context.applicationContext,
            adUnitID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loaded.fullScreenContentCallback = makeFullScreenCallback(context)
                    IKLogger.ads.info("리워드 광고 적재 성공")
                    AnalyticsBus.record("ad_loaded", analyticsParams("format" to "rewarded"))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    IKLogger.ads.warning("리워드 광고 적재 실패: ${error.message}")
                }
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고가 적재되어 있으면 표시. 없으면 null 보상으로 즉시 onResult.
     *
     * @param activity 광고를 띄울 Activity.
     * @param onResult 광고 종료 후 호출. 보상 받았으면 AdReward, 아니면 null.
     */
    fun showIfLoaded(activity: Activity, onResult: (AdReward?) -> Unit) {
        val current = ad
        if (current == null) {
            IKLogger.ads.debug("리워드 광고 미적재 — 보상 없이 흐름 통과")
            onResult(null)
            return
        }
        resultCallback = onResult
        earnedReward = null
        ad = null

        current.show(activity) { rewardItem ->
            // 사용자가 보상 조건 충족 — 광고 끝까지 시청.
            val reward = AdReward(amount = rewardItem.amount, type = rewardItem.type)
            earnedReward = reward
            IKLogger.ads.info("리워드 광고 보상 지급: ${reward.type} × ${reward.amount}")
            AnalyticsBus.record(
                "ad_reward_earned",
                analyticsParams("format" to "rewarded", "reward_type" to reward.type)
            )
        }
        IKLogger.ads.info("리워드 광고 표시")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 콜백
    // ────────────────────────────────────────────────────────────────────────

    private fun makeFullScreenCallback(context: Context): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", analyticsParams("format" to "rewarded"))
            }

            override fun onAdDismissedFullScreenContent() {
                AnalyticsBus.record("ad_dismissed", analyticsParams("format" to "rewarded"))
                val reward = earnedReward
                val callback = resultCallback
                earnedReward = null
                resultCallback = null
                preload(context)
                callback?.invoke(reward)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                IKLogger.ads.warning("리워드 광고 표시 실패: ${error.message}")
                val callback = resultCallback
                resultCallback = null
                earnedReward = null
                preload(context)
                callback?.invoke(null)
            }
        }
    }
}
