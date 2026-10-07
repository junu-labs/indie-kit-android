/*
 * FullScreenAdState.kt — IndieKitAds
 *
 * 역할
 *  - 전면 / 리워드 광고 한 자리의 "요청 · 적재 · 오래됨" 판단을 광고 SDK 와 떼어 둔 것.
 *    SDK 없이 단위 시험으로 확인할 수 있게 순수한 값으로 둔다.
 *
 * 왜 따로 두나 (2026-10 광고 일치율 이슈)
 *  - 예전에는 앱을 켤 때마다 전면 · 리워드 광고를 미리 받아 두었다. 하지만 앱은 전면 광고를
 *    "성취 순간" 같은 드문 때에만 띄우므로, 대부분의 실행에서 받아 둔 광고가 한 번도 안 보이고 버려졌다.
 *    AdMob 보고서에 "요청은 많은데 노출은 적은" 꼴 (노출 / 요청 50% 미만, 일치율 60%대) 로 잡혔다.
 *  - 이제는 앱이 띄우려 하거나 (`showInterstitial`) 곧 띄울 것 같다고 알려 줄 때 (`prepareInterstitial`)
 *    만 요청한다. 같은 자리에 요청을 겹쳐 보내지 않고, 오래돼 노출이 안 잡히는 광고는 띄우지 않는다.
 *
 * iOS 자매: `FullScreenAdState.swift` — 같은 규칙.
 */

package kr.co.junu.indiekit.ads

/** 전면 / 리워드 광고 한 자리의 적재 상태. 값이 바뀔 때마다 새 값을 만든다. */
internal data class FullScreenAdState(
    /** 광고를 받은 때 (밀리초). null 이면 쓸 광고가 없다. */
    val loadedAtMillis: Long? = null,
    /** 요청을 보내고 답을 기다리는 중인지. */
    val isLoading: Boolean = false,
    /** 지금 화면에 떠 있는지 (닫힐 때까지 새 요청을 보내지 않는다). */
    val isPresenting: Boolean = false,
) {
    companion object {
        /**
         * 받은 광고를 띄울 수 있는 시간.
         * 구글: 전면 · 리워드 광고는 받은 뒤 1시간이 지나면 띄워도 노출로 잡히지 않는다.
         * 시계 오차를 생각해 5분 여유를 둔다.
         */
        const val MAX_AGE_MILLIS: Long = 55L * 60L * 1000L
    }

    /** 받은 광고가 있고, 아직 오래되지 않았는지. */
    fun isReady(nowMillis: Long): Boolean {
        val at = loadedAtMillis ?: return false
        return nowMillis - at < MAX_AGE_MILLIS
    }

    /** 받은 광고가 있지만 오래돼 쓸 수 없는지. */
    fun isExpired(nowMillis: Long): Boolean = loadedAtMillis != null && !isReady(nowMillis)

    /** 새 요청을 보내야 하는지 — 요청 중 · 표시 중 · 쓸 광고가 이미 있으면 보내지 않는다. */
    fun shouldRequest(nowMillis: Long): Boolean = !isLoading && !isPresenting && !isReady(nowMillis)

    fun requested(): FullScreenAdState = copy(isLoading = true, loadedAtMillis = null)
    fun loaded(atMillis: Long): FullScreenAdState = copy(isLoading = false, loadedAtMillis = atMillis)
    fun failed(): FullScreenAdState = copy(isLoading = false, loadedAtMillis = null)

    /** 띄우기 시작 — 같은 광고를 두 번 띄우지 않게 비운다. */
    fun presenting(): FullScreenAdState = copy(loadedAtMillis = null, isPresenting = true)

    /** 닫힘 또는 표시 실패. */
    fun dismissed(): FullScreenAdState = copy(isPresenting = false)

    /** 오래된 광고를 버린다. */
    fun discarded(): FullScreenAdState = copy(loadedAtMillis = null)
}

/** 전면 / 리워드 광고를 언제 요청하는지의 규칙. */
internal object FullScreenAdPolicy {
    /** 앱 시작 (`configure`) 때 미리 받지 않는다 — 안 보일 광고를 요청하지 않는다. */
    const val PRELOADS_ON_CONFIGURE: Boolean = false

    /**
     * 광고가 닫힌 뒤 다음 광고를 곧바로 받지 않는다 — 다음 차례가 올지 모르므로.
     * 다음 `showInterstitial` / `prepareInterstitial` 때 받는다.
     */
    const val RELOADS_AFTER_DISMISS: Boolean = false
}
