/*
 * Errors.kt — IndieKitCore
 *
 * 역할
 *  - 5개 모듈이 공유하는 "처음 설정이 안 됨 / 필수 키 누락" 같은 기본 에러 묶음.
 *  - 각 모듈은 자기 도메인 에러 (예: BillingError, AuthError) 를 별도로 정의하되,
 *    공통 부분은 여기 IndieKitConfigurationError 를 그대로 던지거나 wrap 한다.
 *
 * 주요 개념
 *  - sealed class 채택: 가능한 케이스가 닫혀 있어 when 문이 빠짐없이 처리되었는지 컴파일 시 보장.
 *  - 메시지: 한국어 — 인디 앱 1차 사용자가 한국어 사용자라.
 *  - iOS 자매 (IndieKitConfigurationError / LocalizedError) 와 진입점 이름 1:1 대칭.
 *
 * 사용 방법
 *  ```kotlin
 *  if (!isConfigured) {
 *      throw IndieKitConfigurationError.NotConfigured(module = "IndieKitAds")
 *  }
 *  ```
 */

package kr.co.junu.indiekit.core

/**
 * 모든 IndieKit 모듈이 공유하는 "처음 설정 / 키 / 환경" 관련 에러.
 *
 * 모듈별 도메인 에러와 다음 점이 다르다.
 *  - 도메인 에러 (예: 결제 거절, 인증 거절): 사용자에게 의미 있는 결과.
 *  - Configuration 에러: 개발자에게 의미 있는 결과 — "configure 안 했음", "키 빠짐" 등.
 *
 * RuntimeException 을 상속해 throws 선언 없이 던질 수 있게 — 사용자 설정 오류는 처음 한 번 잡으면 끝.
 */
public sealed class IndieKitConfigurationError(message: String) : RuntimeException(message) {

    /** 해당 모듈의 `configure(...)` 가 한 번도 호출되지 않은 상태. */
    public class NotConfigured(public val module: String) : IndieKitConfigurationError(
        "$module 이 아직 처음 설정되지 않았습니다. 앱 시작 시 $module.configure(...) 을 호출하세요."
    )

    /**
     * `configure(...)` 는 호출됐지만 필수 설정값이 누락된 상태.
     * 예: `IndieKitAuth.configure(googleServerClientID = null)` 후 `signIn(provider = GOOGLE)` 시도.
     */
    public class MissingRequiredValue(
        public val module: String,
        public val key: String
    ) : IndieKitConfigurationError(
        "$module 의 필수 설정값 '$key' 이 없습니다. configure 호출에 값을 채워 주세요."
    )

    /** 같은 모듈을 두 번 이상 `configure(...)` 한 경우 (대부분 모듈은 1회 호출만 허용). */
    public class AlreadyConfigured(public val module: String) : IndieKitConfigurationError(
        "$module 은 이미 처음 설정되어 있습니다. configure 는 앱 생애 동안 한 번만 호출하세요."
    )
}
