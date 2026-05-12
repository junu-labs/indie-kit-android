/*
 * NativeAdView.kt — IndieKitAds
 *
 * 역할
 *  - Native 광고를 Compose 안에서 띄우는 두 가지 진입점.
 *    1) `NativeAdView(modifier)` — 자동 적재 + 라이브러리 기본 UI. 한 줄로 끝.
 *    2) `NativeAdView(nativeAd, modifier, content)` — 사용자가 NativeAd 받아 직접 레이아웃.
 *  - 자식 Composable (NativeAdHeadlineView, NativeAdMediaView, NativeAdBodyView, ...) 로 NativeAd 자산을
 *    Compose 안에서 자유롭게 배치.
 *
 * 주요 개념
 *  - Google 공식 Jetpack Compose 데모 패턴을 그대로 채용:
 *     - AndroidView 안에 NativeAdView (View 시스템) + ComposeView 중첩.
 *     - LocalNativeAdView CompositionLocal 로 자식 Composable 들이 NativeAdView 의 setter 에 접근.
 *  - 라이브러리는 추가로 `loadNativeAd` 도구 + 기본 UI (`DefaultNativeAdContent`) 를 제공해
 *    사용자가 한 줄로도 띄울 수 있게.
 *  - 광고 적재 / 노출 / 클릭 이벤트는 AnalyticsBus 로 흘려보냄.
 *
 * 출처
 *  - Google 공식 Compose 데모 (Apache 2.0): https://github.com/googleads/googleads-mobile-android-examples
 *  - 라이브러리에 옮기면서 한국어 주석 + 라이브러리 진입점 (configure / AnalyticsBus / IKLogger) 결합.
 *
 * iOS 자매: 아직 Native 미구현 — 안드로이드 선행.
 */

package kr.co.junu.indiekit.ads

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.IKLogger
import kr.co.junu.indiekit.core.analyticsParams
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd

// ────────────────────────────────────────────────────────────────────────────
// CompositionLocal — 자식 Composable 이 NativeAdView 에 접근하는 통로
// ────────────────────────────────────────────────────────────────────────────

/**
 * 자식 Composable (`NativeAdHeadlineView` 등) 이 부모 [NativeAdView] (View 시스템) 에
 * 접근해 setter (`headlineView`, `bodyView`, ...) 를 호출하기 위한 통로.
 */
internal val LocalNativeAdView =
    staticCompositionLocalOf<com.google.android.gms.ads.nativead.NativeAdView?> { null }

// ────────────────────────────────────────────────────────────────────────────
// 진입점 1: 자동 적재 + 기본 UI — 사용자가 한 줄로
// ────────────────────────────────────────────────────────────────────────────

/**
 * Native 광고를 자동 적재하고 라이브러리 기본 UI 로 표시한다. 한 줄로 끝.
 *
 * 사용 방법
 *  ```kotlin
 *  NativeAdView(modifier = Modifier.fillMaxWidth())
 *  ```
 *
 * @param modifier Compose Modifier.
 */
@Composable
public fun NativeAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isDisposed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        if (IndieKitAds.isConfigured) {
            loadNativeAd(context) { ad ->
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

/**
 * 사용자 커스텀 레이아웃을 받는 진입점.
 *
 * 사용 방법
 *  ```kotlin
 *  NativeAdView(nativeAd) {
 *      Column {
 *          NativeAdHeadlineView { Text(nativeAd.headline ?: "") }
 *          NativeAdMediaView(modifier = Modifier.fillMaxWidth())
 *      }
 *  }
 *  ```
 */
@Composable
public fun NativeAdView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nativeAdViewRef = remember {
        mutableStateOf<com.google.android.gms.ads.nativead.NativeAdView?>(null)
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // layoutParams 높이 WRAP_CONTENT — 부모 가로는 채우고 세로는 자식 (ComposeView 안의 자산) 측정에 따름.
            //  - MATCH_PARENT 높이로 두면 verticalScroll Column 등 unbounded height 환경에서
            //    무한 / 비정상 측정 → SDK 가 자산 view boundary 검증 실패 → fallback AdMob 로고로 화면 채움 +
            //    "Advertiser assets outside native ad view" validator 경고 (SDK 가 측정 실패를 첫 자산 이름으로 일반화).
            //  - 데모 MainActivity 가 NativeAdView 를 Card / verticalScroll Column 안에서 호출해서 드러난 증상.
            //  - Google 공식 데모는 Scaffold > Surface(fillMaxSize) 환경이라 같은 코드가 통과 — 라이브러리는
            //    어느 환경에서도 동작해야 하므로 MATCH_PARENT × WRAP_CONTENT 가 안전한 기본값.
            val composeView = ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            com.google.android.gms.ads.nativead.NativeAdView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(composeView)
                nativeAdViewRef.value = this
            }
        },
        update = { view ->
            val composeView = view.getChildAt(0) as? ComposeView
            composeView?.setContent {
                CompositionLocalProvider(LocalNativeAdView provides view) { content() }
            }
        }
    )
    val currentNativeAd by rememberUpdatedState(nativeAd)
    SideEffect { nativeAdViewRef.value?.setNativeAd(currentNativeAd) }
}

// ────────────────────────────────────────────────────────────────────────────
// 광고 적재 도구 — 사용자가 NativeAd 를 직접 관리하고 싶을 때
// ────────────────────────────────────────────────────────────────────────────

/**
 * Native 광고 한 개 적재 + 콜백.
 *
 * 직접 호출은 권장하지 않음 — 보통 `NativeAdView(modifier)` 한 줄이면 충분.
 * 여러 개 미리 적재하거나 LazyColumn 안에서 슬롯마다 다른 광고를 쓰려는 경우 등에만 사용.
 */
public fun loadNativeAd(context: Context, onAdLoaded: (NativeAd) -> Unit) {
    val adUnitID = IndieKitAds.resolvedNativeAdUnitID(context)
    val adLoader = AdLoader.Builder(context.applicationContext, adUnitID)
        .forNativeAd { ad -> onAdLoaded(ad) }
        .withAdListener(object : AdListener() {
            override fun onAdLoaded() {
                IKLogger.ads.info("Native 광고 적재 성공")
                AnalyticsBus.record("ad_loaded", analyticsParams("format" to "native"))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                IKLogger.ads.warning("Native 광고 적재 실패: ${error.message}")
            }

            override fun onAdImpression() {
                AnalyticsBus.record("ad_impression", analyticsParams("format" to "native"))
            }

            override fun onAdClicked() {
                AnalyticsBus.record("ad_clicked", analyticsParams("format" to "native"))
            }
        })
        .build()
    adLoader.loadAd(AdRequest.Builder().build())
}

// ────────────────────────────────────────────────────────────────────────────
// 자식 Composable — 사용자 커스텀 UI 안에서 NativeAd 의 자산 자리를 잡아 줌
// ────────────────────────────────────────────────────────────────────────────

@Composable
public fun NativeAdHeadlineView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdHeadlineView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.headlineView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdBodyView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdBodyView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.bodyView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdCallToActionView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdCallToActionView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.callToActionView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdIconView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdIconView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.iconView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdMediaView(
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType? = null
) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdMediaView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> MediaView(context) },
        update = { view ->
            nativeAdView.mediaView = view
            scaleType?.let { view.setImageScaleType(it) }
        }
    )
}

@Composable
public fun NativeAdPriceView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdPriceView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.priceView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdStarRatingView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdStarRatingView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.starRatingView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdStoreView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdStoreView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.storeView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdAdvertiserView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdAdvertiserView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context -> ComposeView(context) },
        update = { view ->
            nativeAdView.advertiserView = view
            view.setContent(content)
        }
    )
}

@Composable
public fun NativeAdChoicesView(modifier: Modifier = Modifier) {
    val nativeAdView = LocalNativeAdView.current
        ?: error("NativeAdChoicesView 는 NativeAdView 안에서만 호출할 수 있습니다.")
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdChoicesView(context).apply {
                minimumWidth = 15
                minimumHeight = 15
            }
        },
        update = { view -> nativeAdView.adChoicesView = view }
    )
}

/**
 * "Ad" 라벨 표시 — Native 광고는 Google 정책상 "광고" 라는 표시 의무가 있다.
 */
@Composable
public fun NativeAdAttribution(
    modifier: Modifier = Modifier,
    text: String = "Ad",
    shape: Shape = ButtonDefaults.shape,
    containerColor: Color = ButtonDefaults.buttonColors().containerColor,
    contentColor: Color = ButtonDefaults.buttonColors().contentColor,
    padding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
) {
    Box(modifier = modifier.background(containerColor, shape).padding(padding)) {
        Text(color = contentColor, text = text)
    }
}

/**
 * Native 광고 안의 "버튼" 모양 표시 자리.
 *
 * 주의: Compose 의 Button 을 직접 쓰면 자체 클릭 핸들러가 NativeAd 클릭 핸들러를 가린다.
 * 그래서 NativeAdButton 은 클릭 핸들러 없이 Box + Text 모양만 그린다.
 * NativeAd 클릭은 NativeAd 의 AdListener.onAdClicked 콜백이 받음.
 */
@Composable
public fun NativeAdButton(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = ButtonDefaults.shape,
    containerColor: Color = ButtonDefaults.buttonColors().containerColor,
    contentColor: Color = ButtonDefaults.buttonColors().contentColor,
    padding: PaddingValues = ButtonDefaults.ContentPadding
) {
    Box(modifier = modifier.background(containerColor, shape).padding(padding)) {
        Text(color = contentColor, text = text)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 라이브러리 기본 UI — `NativeAdView(modifier)` 한 줄로 띄울 때 사용
// ────────────────────────────────────────────────────────────────────────────

/**
 * 라이브러리가 제공하는 Native 광고 기본 레이아웃.
 *
 * 사용자가 자기 디자인을 쓰고 싶으면 `NativeAdView(nativeAd, modifier) { ... }` 진입점 사용.
 *
 * 주의 — Google 공식 Compose 데모 (DisplayNativeAdView) 와 1:1 동일 구조 유지.
 *  - advertiser 자산은 의도적으로 표시하지 않음. 응답에 advertiser 가 있을 때 NativeAdAdvertiserView 가
 *    NativeAdView boundary 안에서 측정 가능한 상태로 항상 등록되어야 validator 가 통과하는데,
 *    조건부 컴포즈 / 빈 콘텐츠 어떤 변형으로 시도해도 boundary 검사가 가끔 실패해 "Advertiser assets
 *    outside native ad view" 경고가 뜬다. 구글 공식 데모도 advertiser 를 표시하지 않으므로,
 *    기본 UI 에서는 빼고 직접 커스텀이 필요한 사용자는 `NativeAdView(nativeAd) { ... }` 안에서
 *    `NativeAdAdvertiserView { Text(...) }` 를 자기 책임으로 사용하면 된다.
 */
@Composable
internal fun DefaultNativeAdContent(nativeAd: NativeAd, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(8.dp)) {
        NativeAdView(nativeAd) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        nativeAd.icon?.let { icon ->
                            NativeAdIconView(Modifier.padding(5.dp)) {
                                icon.drawable?.toBitmap()?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "광고 아이콘"
                                    )
                                }
                            }
                        }
                        Column {
                            nativeAd.headline?.let { headline ->
                                NativeAdHeadlineView {
                                    Text(
                                        text = headline,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                            nativeAd.starRating?.let { rating ->
                                NativeAdStarRatingView {
                                    Text(
                                        text = "별점 $rating",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                    NativeAdAttribution(
                        modifier = Modifier.align(Alignment.TopStart),
                        text = "광고"
                    )
                }

                NativeAdMediaView(modifier = Modifier.fillMaxWidth())

                nativeAd.body?.let { body ->
                    NativeAdBodyView(modifier = Modifier.padding(5.dp)) {
                        Text(text = body)
                    }
                }

                Row(Modifier.align(Alignment.End).padding(5.dp)) {
                    nativeAd.price?.let { price ->
                        NativeAdPriceView(
                            Modifier.padding(5.dp).align(Alignment.CenterVertically)
                        ) {
                            Text(text = price)
                        }
                    }
                    nativeAd.store?.let { store ->
                        NativeAdStoreView(
                            Modifier.padding(5.dp).align(Alignment.CenterVertically)
                        ) {
                            Text(text = store)
                        }
                    }
                    nativeAd.callToAction?.let { cta ->
                        NativeAdCallToActionView(Modifier.padding(5.dp)) {
                            NativeAdButton(text = cta)
                        }
                    }
                }
            }
        }
    }
}
