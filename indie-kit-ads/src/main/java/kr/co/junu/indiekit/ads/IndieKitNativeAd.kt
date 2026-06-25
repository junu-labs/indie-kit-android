/*
 * IndieKitNativeAd.kt — IndieKitAds 라이브러리 진입점 (Native 광고)
 *
 * 역할
 *  - 같은 패키지의 `NativeAdView.kt` (Google 공식 compose_utils/NativeAdView.kt 와 한 글자도 안 다른 파일)
 *    위에 올린 라이브러리 추가물.
 *  - 두 가지 사용 흐름 제공:
 *    1) `NativeAdView(modifier)` — 자동 적재 + 라이브러리 기본 UI. 한 줄로 끝.
 *    2) `loadNativeAd(context, onAdLoaded)` — 사용자가 NativeAd 객체를 직접 받아 자기 레이아웃 (NativeAdView(nativeAd, modifier, content))
 *       에 끼워 넣고 싶을 때.
 *  - 광고 적재 / 노출 / 클릭 이벤트는 AnalyticsBus 로 흘려보냄.
 *
 * 주의
 *  - Google 공식 compose_utils 의 함수 (`NativeAdView(nativeAd, modifier, content)`, 자식 자산 Composable 들 등) 는
 *    `NativeAdView.kt` 에 있다. 그 파일은 Google 원본과 한 글자도 안 다르게 유지하므로 손대지 않는다.
 *  - 라이브러리 식별성 (한국어 주석 / 라이브러리 진입점 / AnalyticsBus 통합) 은 모두 이 파일 안에서만 다룬다.
 */

package kr.co.junu.indiekit.ads

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger

// ────────────────────────────────────────────────────────────────────────────
// 진입점 1: 자동 적재 + 기본 UI — 사용자가 한 줄로
// ────────────────────────────────────────────────────────────────────────────

/**
 * Native 광고를 자동 적재하고 라이브러리 기본 UI 로 표시한다. 한 줄로 끝.
 *
 * 사용 방법
 *  ```kotlin
 *  NativeAdView(modifier = Modifier.fillMaxWidth())                      // 기본 자리
 *  NativeAdView(modifier = Modifier.fillMaxWidth(), placement = "feed")  // 자리별 ID
 *  ```
 *
 * 직접 레이아웃을 그리고 싶으면 `loadNativeAd(context, onAdLoaded)` 로 NativeAd 를 받고,
 * 같은 패키지의 `NativeAdView(nativeAd, modifier, content)` (Google 공식) 에 자기 content 를 넘긴다.
 *
 * @param placement configure 의 `nativeAdUnitIDs` 에 등록한 자리 이름. 생략하면 기본 Native ID.
 */
@Composable
public fun NativeAdView(modifier: Modifier = Modifier, placement: String? = null) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isDisposed by remember { mutableStateOf(false) }

    DisposableEffect(placement) {
        if (IndieKitAds.isConfigured) {
            loadNativeAd(context, placement) { ad ->
                if (!isDisposed) {
                    nativeAd = ad
                } else {
                    ad.destroy()
                }
            }
        }
        onDispose {
            isDisposed = true
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    nativeAd?.let { ad ->
        DefaultNativeAdContent(nativeAd = ad, modifier = modifier)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 광고 적재 도구 — 사용자가 NativeAd 를 직접 관리하고 싶을 때
// ────────────────────────────────────────────────────────────────────────────

/**
 * Native 광고 한 개 적재 + 콜백.
 *
 * 직접 호출은 권장하지 않음 — 보통 `NativeAdView(modifier)` 한 줄이면 충분.
 * 여러 개 미리 적재하거나 LazyColumn 안에서 슬롯마다 다른 광고를 쓰려는 경우 등에만 사용.
 *
 * @param placement configure 의 `nativeAdUnitIDs` 에 등록한 자리 이름. null 이면 기본 Native ID.
 */
public fun loadNativeAd(
    context: Context,
    placement: String? = null,
    onAdLoaded: (NativeAd) -> Unit
) {
    // 출시 빌드인데 운영 ID 가 없으면 null — 광고를 적재하지 않는다 (테스트 광고 노출 방지).
    val adUnitID = IndieKitAds.resolvedNativeAdUnitID(context, placement)
    if (adUnitID == null) {
        IKLogger.ads.debug("Native 광고 ID 없음 — 표시 안 함")
        return
    }
    val adLoader = AdLoader.Builder(context.applicationContext, adUnitID)
        .forNativeAd { ad -> onAdLoaded(ad) }
        .withAdListener(object : AdListener() {
            override fun onAdLoaded() {
                IKLogger.ads.info("Native 광고 적재 성공 (자리: ${placement ?: "기본"})")
                AnalyticsBus.record("ad_loaded", adAnalyticsParams("native", placement))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                IKLogger.ads.warning("Native 광고 적재 실패: ${error.message}")
            }

            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", adAnalyticsParams("native", placement))
            }

            override fun onAdClicked() {
                AnalyticsBus.record("ad_clicked", adAnalyticsParams("native", placement))
            }
        })
        // AdChoices 아이콘을 우상단에 명시적으로 배치 — Google 정책 권장 위치.
        // 명시 안 하면 SDK 가 임의 위치를 잡고 우리 카드 layout 과 어긋날 수 있다.
        .withNativeAdOptions(
            NativeAdOptions.Builder()
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .build()
        )
        .build()
    adLoader.loadAd(AdRequest.Builder().build())
}

// ────────────────────────────────────────────────────────────────────────────
// 라이브러리 기본 UI — `NativeAdView(modifier)` 한 줄로 띄울 때 사용
// ────────────────────────────────────────────────────────────────────────────

/**
 * 라이브러리가 제공하는 Native 광고 기본 레이아웃.
 *
 * 검증 통과한 출처: TouchCart (`Apps/TouchCart/Android` 의 `NativeAdRow.AdLoadedCard`) — AdMob native ad validator 가
 * "No implementation issues found" 통과한 패턴. Google 공식 데모와 다른 핵심 3가지를 흡수:
 *  1. NativeAdView modifier 에 `height(IntrinsicSize.Min)` 강제 — verticalScroll Column / Card 등 unbounded
 *     height 환경에서도 NativeAdView 가 자식 측정만큼만 차지하도록. Google 공식 데모는 `Scaffold > Surface(fillMaxSize)`
 *     환경이라 IntrinsicSize.Min 없이도 통과했지만, 라이브러리는 어느 환경에서도 동작해야 함.
 *  2. `if (nativeAd.mediaContent != null)` 체크 + `sizeIn(maxHeight = 120.dp)` — MediaView 가 응답에 있을 때만
 *     그리고 높이 120dp 안으로 제한. 응답에 mediaContent 없는데 무조건 NativeAdMediaView 호출하면 SDK 의
 *     자산-view 매칭이 어긋날 수 있음. (AdMob 정책 ≥120dp 도 만족.)
 *  3. NativeAdAttribution 은 NativeAdView 안의 자식 (단, SDK 등록은 안 함, 순수 Compose 라벨).
 *
 * 사용자가 자기 디자인을 쓰고 싶으면 `NativeAdView(nativeAd, modifier) { ... }` (Google 공식 wrapper) 로 직접 그림.
 */
@Composable
internal fun DefaultNativeAdContent(nativeAd: NativeAd, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        NativeAdView(
            nativeAd = nativeAd,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // "광고" 라벨 — 순수 Compose, SDK 등록 안 함.
                NativeAdAttribution(text = "광고")

                // 광고주 아이콘 + 헤드라인 한 줄.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    nativeAd.icon?.let { icon ->
                        NativeAdIconView(modifier = Modifier.size(32.dp)) {
                            icon.drawable?.toBitmap()?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "광고 아이콘",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    nativeAd.headline?.let { headline ->
                        NativeAdHeadlineView(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headline,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 본문 — 응답에 있을 때만, 두 줄까지.
                nativeAd.body?.let { body ->
                    NativeAdBodyView {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // MediaView — mediaContent 가 응답에 있을 때만, 높이 120dp 안으로 제한.
                if (nativeAd.mediaContent != null) {
                    NativeAdMediaView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxHeight = 120.dp),
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    )
                }

                // CTA — 우측 정렬.
                nativeAd.callToAction?.let { cta ->
                    NativeAdCallToActionView(modifier = Modifier.align(Alignment.End)) {
                        NativeAdButton(text = cta)
                    }
                }
            }
        }
    }
}
