/*
 * AuthError.kt — indie-kit-auth
 *
 * 역할
 *  - IndieKitAuth 의 모든 실패 경로를 한 가지 sealed class 로 묶는다.
 *  - iOS 자매의 `enum AuthError` 와 같은 케이스 / 같은 의미.
 *
 * 주요 개념
 *  - providerNotConfigured: configure 에 해당 제공자 키를 안 넘긴 채 그 제공자로 signIn 시도.
 *  - userCancelled: 사용자가 시트를 닫거나 동의 거부.
 *  - missingCredential: 제공자 SDK 가 토큰을 안 돌려줌 (드물지만 가능).
 *  - backendExchange: 우리 서버 세션 발급 호출 중 실패. 자세한 underlying 첨부.
 *  - underlying: 그 외 SDK 측 알 수 없는 오류. underlying 첨부.
 */

package kr.co.junu.indiekit.auth

/**
 * IndieKitAuth 의 모든 실패 경로.
 *
 * Exception 을 상속해 try / catch 또는 Result.failure 로 받을 수 있게 함.
 * iOS 자매의 `LocalizedError` 채택과 같은 의도 — 사용자 친화 한국어 메시지.
 */
public sealed class IndieKitAuthError(message: String) : Exception(message) {

    /**
     * 해당 제공자의 키 (googleServerClientId / kakaoNativeAppKey) 가 configure 에 안 들어옴.
     * 또는 configure 자체를 안 했음.
     */
    public class ProviderNotConfigured(public val provider: AuthProvider)
        : IndieKitAuthError("${provider.raw} 제공자가 처음 설정되지 않았습니다")

    /** 사용자가 로그인 시트 / 동의를 닫거나 거부했음. */
    public class UserCancelled
        : IndieKitAuthError("사용자가 로그인을 취소했습니다")

    /** 제공자 SDK 가 토큰을 안 돌려줌 (예: OpenID Connect OFF + ID Token 요청). */
    public class MissingCredential(public val provider: AuthProvider)
        : IndieKitAuthError("${provider.raw} 신원값을 받지 못했습니다")

    /** 우리 서버 세션 발급 호출 중 실패. underlying 에 진짜 원인. */
    public class BackendExchange(public val underlying: Throwable)
        : IndieKitAuthError("백엔드 세션 발급 실패: ${underlying.localizedMessage ?: underlying.message ?: underlying.javaClass.simpleName}")

    /** 위 분류에 안 잡히는 SDK / 시스템 오류. */
    public class Underlying(public val underlying: Throwable)
        : IndieKitAuthError("로그인 실패: ${underlying.localizedMessage ?: underlying.message ?: underlying.javaClass.simpleName}")
}
