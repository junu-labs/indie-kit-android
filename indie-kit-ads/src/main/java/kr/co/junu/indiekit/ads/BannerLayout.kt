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
