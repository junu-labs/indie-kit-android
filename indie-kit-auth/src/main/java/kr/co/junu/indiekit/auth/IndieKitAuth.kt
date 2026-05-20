/*
 * IndieKitAuth.kt — indie-kit-auth
 *
 * 역할
 *  - Google + Kakao 로그인을 한 길로 묶고, 끝에 우리 서버 세션까지 발급받는 단일 진입점.
 *  - iOS 자매 `IndieKitAuth.shared` 와 1:1 대칭 API.
 *
 * 주요 개념
 *  - object 싱글턴 — Kotlin 의 자연스러운 형태. 프로세스 전역 로그인 상태.
 *  - currentUser: StateFlow<AuthUser?> — Compose 가 collectAsState 로 자동 갱신 받음.
 *  - 처음 설정: 사용 안 할 제공자는 null 로 두면 됨 — 그 제공자로 signIn 시도하면 ProviderNotConfigured.
 *
 * 사용 방법
 *  ```kotlin
 *  // Application.onCreate
 *  IndieKitAuth.configure(
 *      context = this,
 *      googleServerClientId = "WEB_CLIENT_ID.apps.googleusercontent.com",
 *      kakaoNativeAppKey = "31de...",
 *      backend = SessionExchangeAdapter(...)
 *  )
 *
 *  // Activity 안 코루틴
 *  val user = IndieKitAuth.signIn(activity, AuthProvider.GOOGLE)
 *  // user.serverSession?.access — 우리 서버 액세스 토큰
 *
 *  IndieKitAuth.signOut(activity)
 *  ```
 *
 * IndieKitNetwork 와 자연스러운 연결
 *  ```kotlin
 *  val net = IndieKitNetwork(
 *      baseURL = "https://api.example.com",
 *      tokenProvider = { IndieKitAuth.accessToken },
 *      tokenRefresher = { /* 401 시 refresh 토큰 사용 */ }
 *  )
 *  ```
 *
 * 주의사항
 *  - Google: Google Cloud Web Client ID (Android Client ID 아님) 가 setServerClientId 에 들어감.
 *  - Kakao: 안드로이드 콘솔 등록 (패키지 + 키 해시) + 카카오 로그인 활성화 + OpenID Connect ON 필요.
 */

package kr.co.junu.indiekit.auth

import android.app.Activity
import android.content.Context
import com.kakao.sdk.common.KakaoSdk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.co.junu.indiekit.core.IKLogger

/**
 * 로그인 모듈의 단일 진입점.
 *
 * Kotlin object — 프로세스 전역 싱글턴. iOS 의 `IndieKitAuth.shared` 와 같은 의미.
 */
public object IndieKitAuth {

    // MARK: - 외부 공개 상태

    private val _currentUser = MutableStateFlow<AuthUser?>(null)

    /** 현재 로그인된 사용자. null 이면 비로그인. Compose 에서 collectAsState 로 구독. */
    public val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _isReady = MutableStateFlow(false)

    /** 처음 설정이 끝났는지. configure 호출 후 true. */
    public val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // MARK: - 설정값

    private var googleServerClientId: String? = null
    private var kakaoNativeAppKey: String? = null
    private var backend: SessionExchangeAdapter? = null

    // MARK: - 처음 설정

    /**
     * 사용할 로그인 제공자의 키 + 우리 서버 세션 어댑터를 등록한다.
     *
     * @param context Application 컨텍스트 (KakaoSdk.init 에 사용).
     * @param googleServerClientId Google Cloud OAuth **Web Client ID** — Credential Manager 가 idToken 의 audience 로 사용.
     *   Android Client ID 가 아니다. Web Application 클라이언트도 별도로 만들어야 함.
     *   null 이면 Google 로그인 비활성화.
     * @param kakaoNativeAppKey Kakao Developers 의 네이티브 앱 키. null 이면 Kakao 로그인 비활성화.
     * @param backend 우리 서버 세션 발급 어댑터. null 이면 signIn 후 currentUser.serverSession 은 null.
     */
    public fun configure(
        context: Context,
        googleServerClientId: String? = null,
        kakaoNativeAppKey: String? = null,
        backend: SessionExchangeAdapter? = null,
    ) {
        // 빈 문자열도 미설정으로 — 사용처가 "" 또는 null 둘 다 익숙하게 다룰 수 있게.
        this.googleServerClientId = googleServerClientId?.takeIf { it.isNotBlank() }
        this.kakaoNativeAppKey = kakaoNativeAppKey?.takeIf { it.isNotBlank() }
        this.backend = backend

        // 카카오 SDK 시작 호출 — 한 번만 필요. 키가 있을 때만.
        if (kakaoNativeAppKey != null) {
            try {
                KakaoSdk.init(context, kakaoNativeAppKey)
                IKLogger.auth.info("KakaoSdk 시작 호출 완료")
            } catch (e: Throwable) {
                IKLogger.auth.error("KakaoSdk 시작 실패: ${e.message}", e)
            }
        }

        _isReady.value = true
        IKLogger.auth.info("IndieKitAuth 처음 설정 완료")
    }

    // MARK: - 로그인

    /**
     * 지정한 제공자로 로그인 흐름 시작. 우리 서버 어댑터가 등록되어 있으면 세션도 발급.
     *
     * @param activity 사용처 Activity — SDK 가 시트를 띄울 anchor.
     * @param provider GOOGLE / KAKAO.
     * @return 신원 + (선택적) 서버 세션이 담긴 [AuthUser].
     * @throws IndieKitAuthError 모든 실패 경로.
     */
    public suspend fun signIn(activity: Activity, provider: AuthProvider): AuthUser {
        val providerResult = rawSignIn(activity, provider)

        // 백엔드 어댑터가 있으면 우리 서버 세션도 발급.
        val finalUser = if (backend != null) {
            val tokens = backend!!.exchange(
                provider = provider,
                idToken = providerResult.idToken,
                accessToken = providerResult.accessToken,
            )
            providerResult.copy(serverSession = tokens)
        } else {
            providerResult
        }

        _currentUser.value = finalUser
        IKLogger.auth.info("로그인 완료: ${provider.raw}")
        return finalUser
    }

    // MARK: - 로그아웃 / 회원 탈퇴

    /**
     * 로그아웃. 제공자별 로그아웃 호출 + currentUser 초기화.
     */
    public suspend fun signOut(activity: Activity) {
        val provider = _currentUser.value?.provider
        when (provider) {
            AuthProvider.GOOGLE -> GoogleAuthClient.signOut(activity)
            AuthProvider.KAKAO -> KakaoAuthClient.signOut()
            null -> { /* 비로그인 상태 — 아무것도 안 함. */ }
        }
        _currentUser.value = null
        IKLogger.auth.info("로그아웃 완료")
    }

    /**
     * 회원 탈퇴 — 제공자별 unlink / clearCredentialState 호출 + currentUser 초기화.
     *
     * 주의 — 라이브러리는 제공자 측 연결 해제까지만 한다.
     * 우리 서버의 User row 삭제는 별도로 백엔드 끝점 (예: DELETE /auth/delete/) 호출 필요.
     */
    public suspend fun unlink(activity: Activity) {
        val provider = _currentUser.value?.provider
        when (provider) {
            AuthProvider.GOOGLE -> GoogleAuthClient.signOut(activity) // Google 은 별도 unlink 없음 — 자격 증명만 비움.
            AuthProvider.KAKAO -> KakaoAuthClient.unlink()
            null -> { /* 비로그인 상태 — 아무것도 안 함. */ }
        }
        _currentUser.value = null
        IKLogger.auth.info("회원 탈퇴 완료")
    }

    // MARK: - IndieKitNetwork 와 연결하기 좋은 헬퍼

    /** 우리 서버 액세스 토큰 — `IndieKitNetwork.tokenProvider` 자리에 그대로 넣기 좋음. */
    public val accessToken: String?
        get() = _currentUser.value?.serverSession?.access

    // MARK: - 내부: 제공자 분기

    private suspend fun rawSignIn(activity: Activity, provider: AuthProvider): AuthUser {
        return when (provider) {
            AuthProvider.GOOGLE -> {
                val clientId = googleServerClientId
                    ?: throw IndieKitAuthError.ProviderNotConfigured(provider = AuthProvider.GOOGLE)
                GoogleAuthClient.signIn(activity, clientId)
            }
            AuthProvider.KAKAO -> {
                if (kakaoNativeAppKey == null) {
                    throw IndieKitAuthError.ProviderNotConfigured(provider = AuthProvider.KAKAO)
                }
                KakaoAuthClient.signIn(activity)
            }
        }
    }
}
