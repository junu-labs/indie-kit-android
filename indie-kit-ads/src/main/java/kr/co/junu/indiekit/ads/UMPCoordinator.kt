/*
 * UMPCoordinator.kt — IndieKitAds
 *
 * 역할
 *  - 유럽 사용자를 위한 광고 동의창 (UMP — User Messaging Platform) 흐름.
 *  - configure 시점에 자동으로 한 번, 또 사용자가 "광고 설정 변경" 누르면 수동 재호출 가능.
 *
 * 주요 개념
 *  - UMP 는 GDPR / CCPA 준수용 동의 SDK. 비EEA 사용자에겐 동의창이 안 뜸 (UMP 가 자체 판단).
 *  - `requestConsentInfoUpdate` → 동의창 form 적재 → 필요하면 표시.
 *  - 한 번 동의하면 결과는 SDK 가 보관. 다음 실행부터는 다시 안 뜸.
 *  - 사용자가 "광고 설정 변경" 누르면 `presentForm(activity)` 으로 수동 재호출.
 *
 * iOS 자매 (`UMPCoordinator`) 와 같은 흐름.
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.content.Context
import kr.co.junu.indiekit.core.IKLogger
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** UMP 동의창 흐름 관리자. object 싱글턴. */
internal object UMPCoordinator {

    /**
     * 동의 정보 갱신 → 필요하면 동의창 자동 표시.
     * EEA 외 사용자에겐 아무 일도 안 일어남 (UMP 가 알아서 판단).
     *
     * @param activity 동의창을 띄울 수 있는 Activity. configure 시점엔 onCreate 후가 자연스럽지만,
     *                 Application.onCreate 에서 호출하면 Activity 가 없어 form 표시는 다음 Activity 시점으로 미뤄진다.
     *                 안드로이드 UMP 는 form 표시에 Activity 가 필수라, configure 의 자동 호출은
     *                 IndieKitAds 가 ActivityLifecycleCallbacks 로 첫 Activity 가 잡히는 시점까지 대기한다.
     */
    fun requestConsentInfoUpdate(context: Context, activity: Activity?) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val info = UserMessagingPlatform.getConsentInformation(context.applicationContext)
        info.requestConsentInfoUpdate(
            activity ?: return logActivityRequiredForUpdate(),
            params,
            {
                if (info.isConsentFormAvailable) {
                    loadAndShowFormIfRequired(activity, info)
                }
            },
            { formError ->
                IKLogger.ads.warning("UMP 동의 정보 갱신 실패: ${formError.message}")
            }
        )
    }

    /**
     * 사용자가 "광고 설정 변경" 등을 눌렀을 때.
     *
     * @param activity 동의창을 띄울 Activity.
     * @param onComplete 동의창 닫힘 후 (또는 form 미적재 시 즉시) 호출되는 콜백.
     */
    fun presentForm(activity: Activity, onComplete: (Throwable?) -> Unit = {}) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { form ->
                form.show(activity) { dismissError ->
                    if (dismissError != null) {
                        IKLogger.ads.warning("UMP 동의창 닫힘 에러: ${dismissError.message}")
                        onComplete(IllegalStateException(dismissError.message))
                    } else {
                        IKLogger.ads.info("UMP 동의창 표시 완료")
                        onComplete(null)
                    }
                }
            },
            { loadError ->
                IKLogger.ads.warning("UMP 동의창 적재 실패: ${loadError.message}")
                onComplete(IllegalStateException(loadError.message))
            }
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ────────────────────────────────────────────────────────────────────────

    private fun loadAndShowFormIfRequired(activity: Activity, info: ConsentInformation) {
        if (info.consentStatus != ConsentInformation.ConsentStatus.REQUIRED) {
            // EEA 외 사용자 또는 이미 동의 완료 — 동의창 표시 안 함.
            return
        }
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
                IKLogger.ads.warning("UMP 동의창 자동 표시 실패: ${formError.message}")
            }
        }
    }

    private fun logActivityRequiredForUpdate() {
        IKLogger.ads.warning(
            "UMP 동의 정보 갱신 — Activity 가 없어 건너뜀. " +
            "configure 시점이 Application.onCreate 라면 첫 Activity 가 잡힌 후 다시 호출하세요."
        )
    }
}
