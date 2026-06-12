/*
 * BannerAdView.kt — IndieKitAds
 *
 * 역할
 *  - 표준 배너 광고 (320×50) 를 띄우는 Compose Composable.
 *  - 사용처가 `BannerAdView(modifier = Modifier.fillMaxWidth())` 한 줄로 띄움.
 *
 * 주요 개념
 *  - AndroidView 로 AdMob 의 AdView 를 wrapping.
 *  - 광고 ID 는 `IndieKitAds.resolvedBannerAdUnitID(context)` 에서 자동 가져옴 (configure 결과 / 테스트 ID).
 *  - 광고 적재 / 표시 이벤트는 `AnalyticsBus` 로 흘려보내기 — 통계 모듈을 깐 앱이면 자동으로 Firebase 로 흘러감.
 *
 * 사용 방법
 *  ```kotlin
 *  if (!subscription.isPro) {
 *      BannerAdView(modifier = Modifier.fillMaxWidth().height(50.dp))                      // 기본 자리
 *      BannerAdView(modifier = Modifier.fillMaxWidth().height(50.dp), placement = "home")  // 자리별 ID
 *  }
 *  ```
 *
 * 주의사항
 *  - configure 안 한 상태에서 호출되면 placeholder Box 만 그려짐 (광고는 안 뜸, 크래시는 없음).
 *  - DisposableEffect 로 화면 떠날 때 AdView.destroy() — 메모리 누수 방지.
 *
 * iOS 자매 (`IndieKitAds.BannerView`) 와 같은 역할.
 */

package kr.co.junu.indiekit.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * 표준 배너 광고 (320×50) Composable.
 *
 * @param modifier Compose Modifier. 보통 `Modifier.fillMaxWidth()` 로 가로 가득.
 *                 높이는 안 적으면 AdView 가 자기 광고 크기 (50dp) 만큼 차지.
 * @param placement configure 의 `bannerAdUnitIDs` 에 등록한 자리 이름 (예: "home").
 *                  생략하면 기본 배너 ID 사용.
 */
@Composable
public fun BannerAdView(modifier: Modifier = Modifier, placement: String? = null) {
    val context = LocalContext.current

    // configure 안 한 상태면 placeholder Box — 광고 미표시.
    if (!IndieKitAds.isConfigured) {
        Box(modifier = modifier.height(50.dp))
        return
    }

    // 자리 이름이 바뀌면 key 가 subtree 를 통째로 새로 만들어,
    // 새 AdView 가 그 자리의 ID 로 다시 적재되고 옛 AdView 는 onDispose 에서 정리됨.
    key(placement) {
        val adView = remember {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = IndieKitAds.resolvedBannerAdUnitID(context, placement)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        IKLogger.ads.info("배너 광고 적재 성공 (자리: ${placement ?: "기본"})")
                        AnalyticsBus.record("ad_loaded", adAnalyticsParams("banner", placement))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        IKLogger.ads.warning("배너 광고 적재 실패: ${error.message}")
                    }

                    override fun onAdImpression() {
                        AnalyticsBus.record("ad_impression", adAnalyticsParams("banner", placement))
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { adView },
            update = {
                // 갱신할 게 없음 — 광고 ID 는 AdView 를 만들 때 한 번 결정.
            }
        )

        // 화면을 떠날 때 AdView 정리 — 메모리 누수 방지.
        DisposableEffect(adView) {
            onDispose {
                adView.destroy()
            }
        }
    }
}
