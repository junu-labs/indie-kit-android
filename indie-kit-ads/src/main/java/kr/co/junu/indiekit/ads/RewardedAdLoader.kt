/*
 * RewardedAdLoader.kt — IndieKitAds
 *
 * 역할
 *  - 리워드 광고 (rewarded) 의 요청 + 표시 + 보상 콜백.
 *  - 사용자가 자발적으로 광고 보고 보상 받는 시나리오.
 *
 * 주요 개념
 *  - 리워드 광고 = 사용자가 끝까지 보면 보상 (코인 / 힌트 / 일시 잠금 해제 등) 을 주는 광고.
 *  - `showRewarded` 시 `onResult(AdReward?)` 형태로 결과 통지:
 *     - 사용자가 끝까지 봄 → AdReward 전달 (보상 지급)
 *     - 중단 / 광고 미적재 → null 전달 (보상 없음, 흐름은 계속)
 *  - **필요할 때만 요청** (2026-10, FullScreenAdState.kt 머리말): 앱 시작 때 미리 받지 않는다.
 *    "광고 보고 받기" 단추가 있는 화면이 열릴 때 앱이 `IndieKitAds.prepareRewarded` 를 부르면
 *    그때 받아 둔다. 미적재로 `showRewarded` 가 불리면 null 을 주고 다음 차례용으로 요청한다.
 *  - 요청 중이면 겹쳐 보내지 않고, 받은 지 55분이 지난 광고는 띄우지 않고 버린다.
 *  - 보상 콜백 (OnUserEarnedRewardListener) 과 닫힘 콜백 (FullScreenContentCallback.onAdDismissed) 의
 *    순서가 분리되어 있어 둘 다 본 다음 onResult 를 호출한다 (iOS 자매와 같은 방식).
 *
 * iOS 자매 (`RewardedAdLoader`) 와 같은 흐름.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import kr.co.junu.indiekit.core.analyticsParams
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** 리워드 광고 매니저. 자리 (placement) 마다 한 개. */
internal class RewardedAdLoader private constructor(
    /** 이 적재기가 담당하는 자리 이름. null = 기본 자리. */
    private val placement: String?
) {

    companion object {
        /** 기본 자리 (placement null) 의 보관 키. */
        private const val DEFAULT_KEY = "__default__"

        /** 자리 이름 → 적재기 보관소. */
        private val loaders = java.util.concurrent.ConcurrentHashMap<String, RewardedAdLoader>()

        /**
         * 자리 이름에 해당하는 적재기를 꺼낸다. 없으면 만들어 보관.
         *
         * 자리 이름을 안 넘긴 것 (null) 과 [IndieKitAds.DEFAULT_PLACEMENT] ("default") 은
         * **같은 자리** 라 한 칸을 같이 쓴다. 칸을 나누면 같은 광고 번호로 요청을 두 번 보내고
         * 그중 하나는 화면에서 부를 길이 없어 영영 안 쓰인다.
         */
        fun loader(placement: String?): RewardedAdLoader {
            val key = if (placement == null || placement == IndieKitAds.DEFAULT_PLACEMENT) {
                DEFAULT_KEY
            } else {
                placement
            }
            return loaders.getOrPut(key) { RewardedAdLoader(placement) }
        }

        /** 기기가 켜진 뒤 흐른 시간 — 사용자가 시계를 바꿔도 흔들리지 않는다. */
        private fun now(): Long = SystemClock.elapsedRealtime()
    }

    /** 적재된 리워드 광고 인스턴스. */
    @Volatile
    private var ad: RewardedAd? = null

    /** 요청 · 적재 · 오래됨 상태 (SDK 와 떨어진 순수 판단). */
    @Volatile
    private var state = FullScreenAdState()

    /** 띄울 수 있는 광고가 있는지 (오래된 광고는 아님). */
    val isLoaded: Boolean
        get() = ad != null && state.isReady(now())

    /** 광고 종료 후 호출할 콜백 (보상 또는 null). */
    private var resultCallback: ((AdReward?) -> Unit)? = null

    /**
     * 사용자가 보상받았는지 표식. didEarnReward 콜백과 onAdDismissed 의 순서가
     * 분리되어 있어 둘 다 본 다음 resultCallback 을 호출해야 한다.
     */
    private var earnedReward: AdReward? = null

    // ────────────────────────────────────────────────────────────────────────
    // 요청
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 쓸 광고가 없으면 한 개 요청한다. 요청 중 · 표시 중 · 이미 있으면 아무 일 안 함.
     * 광고 SDK 시작이 아직 안 끝났으면 끝난 뒤로 미룬다. 메인 스레드에서 부른다.
     */
    fun prepare(context: Context) {
        if (!state.shouldRequest(now())) return
        if (!IndieKitAds.isSdkReady) {
            IndieKitAds.runAfterSdkReady { prepare(context) }
            return
        }
        // 출시 빌드인데 운영 ID 가 없으면 null — 요청하지 않는다. isLoaded 가 false 라 showIfLoaded 는 보상 없이 통과.
        val adUnitID = IndieKitAds.resolvedRewardedAdUnitID(context, placement)
        if (adUnitID == null) {
            IKLogger.ads.debug("리워드 광고 ID 없음 — 요청 안 함")
            return
        }
        ad = null
        state = state.requested()
        RewardedAd.load(
            context.applicationContext,
            adUnitID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    ad = loaded
                    loaded.fullScreenContentCallback = makeFullScreenCallback(context)
                    state = state.loaded(now())
                    IKLogger.ads.info("리워드 광고 적재 성공 (자리: ${placement ?: "기본"})")
                    AnalyticsBus.record("ad_loaded", adAnalyticsParams("rewarded", placement))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    state = state.failed()
                    IKLogger.ads.warning("리워드 광고 적재 실패: ${error.message}")
                }
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 표시
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 광고가 적재되어 있으면 표시. 없으면 null 보상으로 즉시 onResult 하고, 다음 차례에 쓸 광고를 요청.
     *
     * @param activity 광고를 띄울 Activity.
     * @param onResult 광고 종료 후 호출. 보상 받았으면 AdReward, 아니면 null.
     */
    fun showIfLoaded(activity: Activity, onResult: (AdReward?) -> Unit) {
        val current = ad
        val nowMillis = now()
        if (current == null || !state.isReady(nowMillis)) {
            if (state.isExpired(nowMillis)) {
                IKLogger.ads.info("리워드 광고가 오래됨 (55분 넘음) — 버리고 새로 요청")
                state = state.discarded()
                ad = null
            } else {
                IKLogger.ads.debug("리워드 광고 미적재 — 보상 없이 흐름 통과, 다음 차례용 요청")
            }
            prepare(activity.applicationContext)
            onResult(null)
            return
        }
        resultCallback = onResult
        earnedReward = null
        state = state.presenting()
        ad = null

        current.show(activity) { rewardItem ->
            // 사용자가 보상 조건 충족 — 광고 끝까지 시청.
            val reward = AdReward(amount = rewardItem.amount, type = rewardItem.type)
            earnedReward = reward
            IKLogger.ads.info("리워드 광고 보상 지급: ${reward.type} × ${reward.amount}")
            AnalyticsBus.record(
                "ad_reward_earned",
                adAnalyticsParams("rewarded", placement) + analyticsParams("reward_type" to reward.type)
            )
        }
        IKLogger.ads.info("리워드 광고 표시")
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 콜백
    // ────────────────────────────────────────────────────────────────────────

    /** 닫힘 / 표시 실패 뒤 정리 — 사용자 콜백을 부른다. 다음 광고는 다음 차례에 (FullScreenAdPolicy). */
    private fun finishPresentation(context: Context, reward: AdReward?) {
        state = state.dismissed()
        val callback = resultCallback
        earnedReward = null
        resultCallback = null
        if (FullScreenAdPolicy.RELOADS_AFTER_DISMISS) prepare(context)
        callback?.invoke(reward)
    }

    private fun makeFullScreenCallback(context: Context): FullScreenContentCallback {
        return object : FullScreenContentCallback() {
            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", adAnalyticsParams("rewarded", placement))
            }

            override fun onAdDismissedFullScreenContent() {
                AnalyticsBus.record("ad_dismissed", adAnalyticsParams("rewarded", placement))
                finishPresentation(context, earnedReward)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                IKLogger.ads.warning("리워드 광고 표시 실패: ${error.message}")
                finishPresentation(context, null)
            }
        }
    }
}
