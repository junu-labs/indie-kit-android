/*
 * BannerAdView.kt — IndieKitAds
 *
 * 역할
 *  - 폭 맞춤 (적응형) 배너 광고를 띄우는 Compose Composable.
 *  - 사용처가 `BannerAdView(modifier = Modifier.fillMaxWidth())` 한 줄로 띄움.
 *
 * 주요 개념
 *  - 폭 맞춤 (적응형) 배너: 놓인 자리의 실제 폭을 재서 그 폭에 꼭 맞는 광고를 요청한다.
 *    높이는 50 을 넘지 않게 요청 (`getInlineAdaptiveBannerAdSize(width, maxHeight = 50)`) —
 *    사용하는 앱들이 배너 띠를 `.height(50.dp)` 로 두는 규칙과 그대로 맞물려,
 *    광고가 띠보다 커져 잘리는 일이 없다.
 *  - 회전 대응: 가로 / 세로 회전으로 폭이 바뀌면 *새 폭 기준으로 광고를 다시 적재* 한다.
 *    AdView 는 광고 크기를 한 번 정하면 바꿀 수 없으므로, 폭이 바뀌면 `key` 로
 *    AdView 자체를 새로 만들고 옛것은 onDispose 에서 정리한다. 같은 폭이면 그대로 둔다.
 *    (크기를 안 바꾸면 가로에서 받은 넓은 광고가 세로로 돌아와도 그 폭 그대로 남아
 *    화면 밖으로 튀어나간다 — iOS 에서 실기기로 재현됐던 문제.)
 *  - 회전 애니메이션 중 폭이 여러 번 통지될 수 있어, 바뀐 폭은 0.2초 잠잠해진 뒤에만
 *    반영한다 (첫 폭은 바로 반영 — 첫 광고가 늦지 않게).
 *  - 광고 ID 는 `IndieKitAds.resolvedBannerAdUnitID(context)` 에서 자동 가져옴 (configure 결과 / 테스트 ID).
 *  - 광고 적재 / 표시 이벤트는 `AnalyticsBus` 로 흘려보내기 — 통계 모듈을 깐 앱이면 자동으로 Firebase 로 흘러감.
 *  - 둘레 투명 (2026-10): AdView 는 받은 광고 크기만큼만, 자리 가운데에 둔다. 남는 곳은 투명이라
 *    앱이 칠한 광고 자리 바탕색이 보인다 — 검은 띠 없음 (`BannerContainerLayout`, `BannerLayout`).
 *  - 동의 먼저 (2026-10): 광고 SDK 가 유럽 동의 확인 뒤 시작될 때까지 광고를 요청하지 않는다.
 *  - 기본은 표준 320×50 고정 (2026-10 뒤): 폭 맞춤으로 요청하면 칸보다 좁은 소재가 올 때 AdView 안쪽 WebView 가
 *    남는 곳을 검게 칠했다 (터치카트 3.3.2 시험, 유튜브 시험 소재). 고정 크기 요청엔 꼭 맞는 소재만 온다.
 *    자리가 320dp 보다 좁을 때와 `adaptive = true` 를 고른 앱만 위의 폭 맞춤을 쓴다 (`BannerRequest`).
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
 * iOS 자매 (`IndieKitAds.BannerView`) 와 같은 역할 — 폭 맞춤 / 높이 50 상한 / 회전 재적재까지 대칭.
 */

package kr.co.junu.indiekit.ads

import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** 배너가 요청할 최대 높이 (dp). 사용하는 앱들의 배너 띠 높이 (`.height(50.dp)`) 와 같다. iOS 자매와 같은 값. */
private const val BANNER_MAX_HEIGHT_DP = 50

/** 바뀐 폭을 반영하기 전 기다리는 시간 — 회전 애니메이션 중간 값으로 광고를 여러 번 적재하지 않게. iOS 자매와 같은 값. */
private const val WIDTH_SETTLE_MS = 200L

/**
 * 폭 맞춤 (적응형) 배너 광고 Composable. 놓인 자리의 폭을 채우고, 높이는 50 이하.
 *
 * 회전으로 폭이 바뀌면 새 폭 기준으로 광고를 자동으로 다시 적재한다.
 *
 * @param modifier Compose Modifier. 보통 `Modifier.fillMaxWidth()` 로 가로 가득.
 *                 높이는 안 적으면 AdView 가 자기 광고 크기 (50dp 이하) 만큼 차지.
 * @param placement configure 의 `bannerAdUnitIDs` 에 등록한 자리 이름 (예: "home").
 *                  생략하면 기본 배너 ID 사용.
 * @param adaptive false (기본, 2026-10 뒤) = 표준 320×50 고정 — 어떤 소재가 와도 광고 안쪽 검은 띠가 없다.
 *                 true = 자리 폭 그대로 폭 맞춤 (예전 동작 — 좁은 소재가 오면 광고 안쪽에 검은 띠가 생길 수 있다).
 *                 자리가 320dp 보다 좁으면 어느 쪽이든 그 폭의 폭 맞춤.
 */
@Composable
public fun BannerAdView(modifier: Modifier = Modifier, placement: String? = null, adaptive: Boolean = false) {
    val context = LocalContext.current

    // configure 안 한 상태면 placeholder Box — 광고 미표시.
    // isConfigured (평범한 Boolean) 대신 화면이 지켜볼 수 있는 자리를 읽는다.
    // 그래야 configure 가 이 배너보다 늦게 끝나도 그 순간 스스로 다시 그려진다 — 빈 칸으로 굳지 않는다.
    // 광고 SDK 시작 (= 유럽 동의 확인, 2026-10) 전에도 빈 자리 — 동의 전에 광고를 요청하지 않는다.
    // 이 값도 화면이 지켜볼 수 있어, 시작되는 순간 스스로 다시 그려져 광고를 요청한다.
    if (!IndieKitAds.isConfiguredForCompose || !IndieKitAds.isAdsReadyForCompose) {
        Box(modifier = modifier.height(50.dp))
        return
    }

    // 출시 빌드인데 운영 ID 가 없으면 null — 광고를 적재하지 않고 빈 Box 로 둔다 (테스트 광고 노출 방지).
    val adUnitID = IndieKitAds.resolvedBannerAdUnitID(context, placement)
    if (adUnitID == null) {
        IKLogger.ads.debug("배너 광고 ID 없음 — 표시 안 함")
        Box(modifier = modifier.height(50.dp))
        return
    }

    // BoxWithConstraints 로 이 Composable 에 주어진 실제 폭을 잰다.
    // 회전 등으로 폭이 바뀌면 이 블록이 새 폭으로 다시 그려진다.
    // contentAlignment = Center: 광고가 자리보다 작으면 자리 가운데에 (둘레는 투명 — 앱 바탕색이 보인다).
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // 폭이 무제한 (스크롤 가로 등 특수한 자리) 이면 잴 수 없으므로 0 으로 두고 광고를 만들지 않는다.
        val measuredWidthDp = if (maxWidth != Dp.Infinity) maxWidth.value.toInt() else 0

        // 광고를 실제로 적재할 폭. 첫 폭은 바로 쓰고, 바뀐 폭은 0.2초 잠잠해진 뒤에만 반영한다.
        var adWidthDp by remember { mutableIntStateOf(0) }
        LaunchedEffect(measuredWidthDp) {
            when {
                measuredWidthDp <= 0 -> Unit               // 아직 폭이 안 정해짐 — 기다린다.
                adWidthDp == 0 -> adWidthDp = measuredWidthDp   // 첫 폭 — 바로 적재 (지연 없음).
                adWidthDp != measuredWidthDp -> {          // 회전 등으로 폭이 바뀜 — 잠잠해진 뒤 반영.
                    delay(WIDTH_SETTLE_MS)
                    adWidthDp = measuredWidthDp
                }
            }
        }

        // 폭이 아직 정해지기 전에는 배너를 만들지 않는다 —
        // 0 폭으로 광고를 적재했다가 곧바로 다시 적재하는 낭비 방지.
        if (adWidthDp > 0) {
            // 요청 크기 — 기본은 표준 320×50 고정 (좁은 소재가 와도 안쪽 검은 띠 없음, BannerRequest 머리말).
            val request = BannerRequest.forSlot(adWidthDp, adaptive)
            // 자리 이름 또는 요청 크기가 바뀌면 key 가 subtree 를 통째로 새로 만들어,
            // 새 AdView 가 새 크기 / 새 ID 로 다시 적재되고 옛 AdView 는 onDispose 에서 정리됨.
            // (AdView 는 광고 크기를 한 번 정하면 바꿀 수 없어, 크기 변경 = AdView 재생성이다.)
            // 고정 320×50 이면 회전해 폭이 바뀌어도 같은 요청이라 다시 적재하지 않는다.
            key(placement, request) {
                val adView = remember {
                    AdView(context).apply {
                        // 바탕 투명 — 광고가 AdView 보다 작게 그려져도 남는 곳이 검게 보이지 않게.
                        setBackgroundColor(Color.TRANSPARENT)
                        setAdSize(
                            if (request.adaptive) AdSize.getInlineAdaptiveBannerAdSize(request.widthDp, BANNER_MAX_HEIGHT_DP)
                            else AdSize.BANNER
                        )
                        adUnitId = adUnitID
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                IKLogger.ads.info("배너 광고 적재 성공 (자리: ${placement ?: "기본"}, 요청: ${request.widthDp}dp${if (request.adaptive) " 폭 맞춤" else " 고정"})")
                                // 받은 광고의 실제 크기로 다시 재고 가운데 자리를 잡는다.
                                requestLayout()
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

                // AdView 를 자리 전체로 늘리지 않고, 투명한 그릇 안에 광고 크기만큼만 가운데에 둔다
                // (예전엔 AdView 가 자리 폭 전체를 차지해 작은 광고 양옆이 검은 띠로 보였다).
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        BannerContainerLayout(ctx).apply {
                            addView(
                                adView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            )
                        }
                    },
                    update = {
                        // 갱신할 게 없음 — 크기 / ID 변경은 위 key 가 AdView 재생성으로 처리.
                    }
                )

                // 화면을 떠날 때 (또는 key 가 바뀌어 옛 subtree 가 버려질 때) AdView 정리 — 메모리 누수 방지.
                DisposableEffect(adView) {
                    onDispose {
                        adView.destroy()
                    }
                }
            }
        }
    }
}
