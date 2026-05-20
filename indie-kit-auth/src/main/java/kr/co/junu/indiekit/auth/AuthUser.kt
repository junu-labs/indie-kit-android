/*
 * AuthUser.kt — indie-kit-auth
 *
 * 역할
 *  - 로그인 결과를 표현하는 자료. 제공자(구글/카카오) 신원 + 우리 서버 세션 토큰을 함께.
 *
 * iOS 자매 와 1:1 대칭
 *  - AuthProvider — iOS 의 `enum AuthProvider` 와 같은 케이스 (안드로이드는 APPLE 빠짐 — GOOGLE / KAKAO 둘만).
 *  - SessionTokens — 우리 서버 세션 토큰. iOS 의 같은 이름 struct 와 같은 필드.
 *  - AuthUser — 신원 + 세션. iOS 의 같은 이름 struct 와 같은 필드.
 */

package kr.co.junu.indiekit.auth

/**
 * 로그인 제공자.
 *
 * Android 는 Apple 로그인을 다루지 않음 (iOS 자매에는 APPLE 케이스도 있음).
 * Apple 로그인을 안드로이드에 붙이려면 Custom Tabs + OAuth 2.0 흐름을 직접 구현해야 하는데,
 * 별도 단계로 분리. 이번 차례는 GOOGLE / KAKAO 만.
 */
public enum class AuthProvider(public val raw: String) {
    GOOGLE("google"),
    KAKAO("kakao"),
}

/**
 * 우리 서버에서 발급받은 세션 토큰 모음.
 *
 * SessionExchangeAdapter 가 클라이언트 신원값 (idToken) 을 우리 서버로 보내 받아온 결과.
 * IndieKitAuth.accessToken 으로 IndieKitNetwork 에 그대로 꽂아 인증 호출에 사용.
 */
public data class SessionTokens(
    /** 우리 서버 호출 시 Authorization 헤더에 들어갈 액세스 토큰. */
    val access: String,
    /** 액세스 토큰 갱신용 리프레시 토큰. 백엔드가 발급 안 하면 null. */
    val refresh: String? = null,
    /** 액세스 토큰 만료 시각 (epoch milliseconds). 백엔드가 안 알려주면 null. */
    val expiresAtMillis: Long? = null,
)

/**
 * 로그인이 끝난 사용자 정보.
 *
 * 제공자가 알려준 신원 (id, email, displayName, idToken) +
 * 우리 서버가 발급한 세션 (serverSession) 한 묶음.
 */
public data class AuthUser(
    /** 제공자별 고유 ID (Google sub, Kakao id). */
    val id: String,

    /** 어느 제공자로 로그인했는지. */
    val provider: AuthProvider,

    /** 이메일 주소. 사용자가 동의 안 했거나 제공 안 하면 null. */
    val email: String? = null,

    /** 표시 이름. 제공자가 안 알려주면 null. */
    val displayName: String? = null,

    /** 우리 서버로 보낼 신원 확인값 (ID Token). 백엔드가 검증해서 사용자 식별. */
    val idToken: String? = null,

    /** 액세스 토큰 (제공자 API 직접 호출용 — 카카오 API 등). 안 받으면 null. */
    val accessToken: String? = null,

    /** 우리 서버에서 발급받은 세션. SessionExchangeAdapter 가 채움. 백엔드 안 쓰면 null. */
    val serverSession: SessionTokens? = null,
)
