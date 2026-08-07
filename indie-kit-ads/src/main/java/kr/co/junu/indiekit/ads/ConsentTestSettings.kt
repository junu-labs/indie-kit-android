/*
 * ConsentTestSettings.kt — IndieKitAds
 *
 * 역할
 *  - 유럽 광고 동의창 (UMP) 을 *시험* 하기 위한 나라 강제 설정.
 *  - 한국에서 개발하면 동의창이 원래 안 뜨므로, "유럽에 있는 것처럼 꾸며" 동의창이
 *    실제로 뜨는 모습을 에뮬레이터 / 시험 기기에서 확인할 수 있게 한다.
 *
 * 사용 방법 (앱 측 — 시험할 때만)
 *  ```kotlin
 *  IndieKitAds.configure(
 *      context = this,
 *      requestConsent = true,
 *      consentTestSettings = ConsentTestSettings(geography = ConsentTestGeography.EEA)
 *  )
 *  // 동의창에 한 번 답하면 다음 실행부터 안 뜨므로, 다시 보려면:
 *  IndieKitAds.resetConsentForTesting(context)   // 그리고 앱 재시작
 *  ```
 *
 * 주의사항
 *  - **디버그 빌드에서만 동작한다.** 출시 빌드에서는 값이 들어와도 무시하고 경고만
 *    남긴다 — 시험 설정이 실사용자에게 새어 나가는 사고를 모듈이 막는다.
 *  - 에뮬레이터는 자동으로 시험 기기로 인정된다. 실기기에서 시험하려면 Logcat 에
 *    UMP 가 찍어 주는 기기 번호를 testDeviceHashedIds 에 넣어야 한다.
 */

package kr.co.junu.indiekit.ads

/**
 * 시험용으로 강제할 나라.
 *
 * - [EEA] 유럽 (동의창이 뜨는 지역) 에 있는 것처럼 꾸민다 — 동의창 시험용.
 * - [NOT_EEA] 유럽 밖에 있는 것처럼 꾸민다 — 동의창이 안 뜨는 경로 시험용.
 */
public enum class ConsentTestGeography {
    /** 유럽에 있는 것처럼 — 동의창이 뜬다. */
    EEA,

    /** 유럽 밖에 있는 것처럼 — 동의창이 안 뜬다. */
    NOT_EEA
}

/**
 * 동의창 시험 설정 묶음. configure 의 `consentTestSettings` 로 넘긴다.
 *
 * @property geography 강제할 나라.
 * @property testDeviceHashedIds 시험 기기 번호 목록. 에뮬레이터는 비워 둬도 된다
 *           (자동 인정). 실기기는 Logcat 의 UMP 안내 줄에서 번호를 복사해 넣는다.
 */
public data class ConsentTestSettings(
    public val geography: ConsentTestGeography,
    public val testDeviceHashedIds: List<String> = emptyList()
)
