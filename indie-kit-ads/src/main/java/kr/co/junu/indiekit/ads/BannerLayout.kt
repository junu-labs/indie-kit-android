/*
 * BannerLayout.kt — IndieKitAds
 *
 * 역할
 *  - 배너 광고를 광고 자리 안 어디에 둘지 정하는 순수한 계산 + 그 계산대로 배너를 놓는 투명한 그릇.
 *
 * 왜 (2026-10 — 배너 둘레 검은 띠)
 *  - 예전에는 AdView 가 자리 전체 폭을 차지했고, 광고가 자리보다 작게 오면 (320×50, 468×60 시험 광고 등)
 *    남는 곳이 검은 띠로 채워졌다 — 광고 규칙 "검은 띠 금지" 위반.
 *  - 이제 AdView 는 **광고 크기만큼만** 차지하고 자리 **가운데** 에 놓인다. 그릇과 AdView 바탕은 투명이라
 *    앱이 칠한 광고 자리 바탕색이 둘레에 그대로 보인다. 광고가 자리보다 크면 자리 안으로 줄여 잡는다.
 *
 * iOS 자매: `BannerLayout.swift` + `BannerContainerView` — 같은 규칙.
 */

package kr.co.junu.indiekit.ads

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout

/**
 * 배너를 어떤 크기로 요청할지 (2026-10 — 좁은 소재일 때 광고 안쪽 검은 띠, 터치카트 시험 38).
 *
 * 폭 맞춤 (적응형) 으로 자리 폭 그대로 요청하면, 구글이 그 칸보다 좁은 소재 (예: 시험 광고의 유튜브 소재
 * "AdMob has a YouTube channel") 를 보낼 때 AdView 안쪽 구글 WebView 가 남는 곳을 검게 칠한다 — 칠하는 것이
 * WebView 안의 HTML 이라 바탕색 (AdView · 그릇 투명) 으로는 못 막았다 (0.9.5 의 고침은 AdView *바깥* 둘레만).
 * 그래서 기본은 **표준 320×50 고정 크기** ([AdSize.BANNER]) 로 요청한다: 고정 크기 요청엔 그 크기에 꼭 맞는 소재만 오므로
 * 안쪽에 남는 곳이 없다. 칸이 320 보다 좁을 때만 그 폭의 폭 맞춤으로 (예전과 같음).
 *
 * @property widthDp 요청 폭. 고정이면 320.
 * @property adaptive true = 폭 맞춤 (getInlineAdaptiveBannerAdSize), false = 표준 320×50 고정.
 */
internal data class BannerRequest(val widthDp: Int, val adaptive: Boolean) {
    companion object {
        const val STANDARD_WIDTH_DP = 320
        const val STANDARD_HEIGHT_DP = 50
        val STANDARD = BannerRequest(STANDARD_WIDTH_DP, adaptive = false)

        /**
         * 자리 폭과 앱이 고른 방식으로 요청 크기를 정한다. iOS 자매 (`BannerRequest.forSlot`) 와 같은 규칙.
         *  - adaptive 를 고른 앱 → 자리 폭 그대로 폭 맞춤 (예전 동작).
         *  - 아니면 자리가 320 이상이면 320×50 고정, 더 좁으면 자리 폭의 폭 맞춤 (320 고정은 넘쳐 잘린다).
         */
        fun forSlot(slotWidthDp: Int, adaptive: Boolean): BannerRequest =
            if (!adaptive && slotWidthDp >= STANDARD_WIDTH_DP) STANDARD
            else BannerRequest(slotWidthDp, adaptive = true)
    }
}

/** 광고 자리 안에서 광고가 놓일 자리 (픽셀). */
internal data class BannerFrame(val left: Int, val top: Int, val width: Int, val height: Int)

internal object BannerLayout {

    /**
     * 광고 자리 (containerW × containerH) 안에서 광고 (adW × adH) 가 놓일 자리 — 가운데, 자리를 넘지 않게.
     * 광고 크기를 아직 모르면 (0) 자리 전체.
     */
    fun adFrame(adW: Int, adH: Int, containerW: Int, containerH: Int): BannerFrame {
        if (adW <= 0 || adH <= 0) return BannerFrame(0, 0, containerW, containerH)
        val w = minOf(adW, containerW)
        val h = minOf(adH, containerH)
        return BannerFrame((containerW - w) / 2, (containerH - h) / 2, w, h)
    }
}

/**
 * 배너를 담는 투명한 그릇. 자식 (AdView) 을 자기 광고 크기만큼 재고, 자리 가운데에 놓는다.
 * 바탕을 투명으로 두어 남는 곳에 앱의 광고 자리 바탕색이 보이게 한다.
 */
internal class BannerContainerLayout(context: Context) : FrameLayout(context) {

    init {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val containerW = right - left
        val containerH = bottom - top
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val f = BannerLayout.adFrame(child.measuredWidth, child.measuredHeight, containerW, containerH)
            child.layout(f.left, f.top, f.left + f.width, f.top + f.height)
        }
    }
}
