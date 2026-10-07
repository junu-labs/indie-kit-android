/*
 * AdsStartGate.kt — IndieKitAds
 *
 * 역할
 *  - "광고 SDK 를 지금 시작해도 되는가" 판단을 SDK 와 떼어 둔 작은 문지기. 단위 시험으로 순서를 확인한다.
 *
 * 왜 따로 두나 (2026-10 — 유럽 동의를 받은 뒤에 광고 SDK 시작)
 *  - 구글 권고: 유럽 사용자 동의 (UMP) 를 받은 뒤 광고 SDK 를 시작하고 광고를 요청한다.
 *    예전 (0.9.3 ~) 에는 동의 예약과 SDK 시작이 나란히 가서, 동의 전에 광고 요청이 나갈 틈이 있었다.
 *  - 이제는 동의 절차를 켠 앱 (`requestConsent = true`) 이면 UMP 가 "광고를 요청해도 된다"
 *    (`canRequestAds`) 고 답할 때만 시작한다. 그 답은 두 번 볼 수 있다:
 *      ① 동의 절차를 거는 순간 — 지난 실행에서 받아 둔 동의로 바로 시작 (구글 예제와 같음)
 *      ② 동의 절차가 끝난 순간 — 창을 닫았거나, 창이 필요 없다고 나왔을 때
 *    어느 쪽이 먼저든 시작은 **한 번만**.
 *
 * iOS 자매: `AdsStartGate.swift` — 같은 규칙.
 */

package kr.co.junu.indiekit.ads

/** 광고 SDK 시작 문지기 — 한 번만, 동의가 허락할 때만 연다. 어느 실에서 불러도 된다. */
internal class AdsStartGate {

    /** 이미 시작했는지. */
    @Volatile
    var isStarted: Boolean = false
        private set

    /**
     * 지금 시작해야 하는지 판단하고, 시작해야 하면 시작한 것으로 표시한다.
     *
     * @param requestConsent 앱이 동의 절차를 켰는지 (`configure(requestConsent = …)`).
     *                       끈 앱은 동의를 기다리지 않고 곧바로 시작한다 (예전과 같음).
     * @param canRequestAds UMP 가 "광고를 요청해도 된다" 고 답했는지.
     * @return true 면 지금 SDK 를 시작한다. 같은 문지기에서 true 는 한 번만 나온다.
     */
    @Synchronized
    fun tryStart(requestConsent: Boolean, canRequestAds: Boolean): Boolean {
        if (isStarted) return false
        if (requestConsent && !canRequestAds) return false
        isStarted = true
        return true
    }
}
