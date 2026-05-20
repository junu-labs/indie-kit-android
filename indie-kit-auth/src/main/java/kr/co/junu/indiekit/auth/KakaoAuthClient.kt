/*
 * KakaoAuthClient.kt — indie-kit-auth
 *
 * 역할
 *  - 카카오 로그인 흐름.
 *  - kakao-sdk (v2-user) 사용 — 카카오톡 앱 우선, 없으면 카카오계정 (웹) 폴백.
 *
 * 주요 개념
 *  - KakaoSdk.init(context, appKey) — Application.onCreate 같은 시점에 한 번. configure 에서 처리.
 *  - UserApiClient.instance.loginWithKakaoTalk(...) — 카카오톡 앱으로 로그인.
 *  - UserApiClient.instance.loginWithKakaoAccount(...) — 웹 로그인 폴백.
 *  - 결과로 OAuthToken (idToken / accessToken) 받음. 추가로 me() 호출해 프로필 (nickname, email) 수신.
 *
 * iOS 자매 KakaoAuthClient.swift 와 같은 흐름.
 *
 * 주의사항
 *  - 카카오 콘솔에 안드로이드 플랫폼 등록 (패키지 + 키 해시) + 카카오 로그인 활성화 + OpenID Connect 활성화 (idToken 받으려면) 필요.
 *  - LSApplicationQueriesSchemes 같은 iOS 의 자리는 안드로이드엔 없음 (안드로이드는 카카오톡 앱 검사 API 가 자체적).
 */

package kr.co.junu.indiekit.auth

import android.app.Activity
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User
import kotlinx.coroutines.suspendCancellableCoroutine
import kr.co.junu.indiekit.core.IKLogger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object KakaoAuthClient {

    /**
     * 카카오 로그인 — 카카오톡 앱 우선, 없으면 웹.
     *
     * UserApiClient.instance.isKakaoTalkLoginAvailable(context) 가 true 면 앱 흐름.
     * false 면 (앱 미설치 / 비활성) 웹 폴백.
     *
     * @param activity 사용처 Activity (카카오 SDK 가 시트를 띄울 anchor).
     */
    suspend fun signIn(activity: Activity): AuthUser {
        val token = if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
            try {
                loginWithKakaoTalk(activity)
            } catch (e: Throwable) {
                // 카카오톡 앱이 깔려 있지만 실패 (앱 미동의 등) — 웹 폴백.
                IKLogger.auth.warning("카카오톡 앱 로그인 실패 → 웹 폴백: ${e.message}")
                loginWithKakaoAccount(activity)
            }
        } else {
            loginWithKakaoAccount(activity)
        }

        val me = fetchMe()
        val id = me.id?.toString() ?: ""
        val email = me.kakaoAccount?.email
        val displayName = me.kakaoAccount?.profile?.nickname

        IKLogger.auth.info("Kakao 신원값 수신 완료")
        return AuthUser(
            id = id,
            provider = AuthProvider.KAKAO,
            email = email,
            displayName = displayName,
            idToken = token.idToken,
            accessToken = token.accessToken,
        )
    }

    /**
     * 카카오 로그아웃.
     * UserApiClient.instance.logout — 카카오 측 토큰 비움 + 로컬 캐시 비움.
     */
    suspend fun signOut(): Unit = suspendCancellableCoroutine { continuation ->
        UserApiClient.instance.logout { error ->
            if (error != null) {
                IKLogger.auth.warning("Kakao 로그아웃 실패 (무시): ${error.message}")
            }
            // 로그아웃 실패도 사용자 흐름 차단 안 함.
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    /**
     * 카카오 회원 탈퇴 (unlink).
     * UserApiClient.instance.unlink — 카카오 측에서 이 사용자가 우리 앱과 연결 해제.
     * 다음 로그인 때 동의 화면 다시 표시.
     */
    suspend fun unlink(): Unit = suspendCancellableCoroutine { continuation ->
        UserApiClient.instance.unlink { error ->
            if (error != null) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IndieKitAuthError.Underlying(underlying = error))
                }
            } else {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    // MARK: - 내부 헬퍼

    /** 카카오톡 앱으로 로그인. */
    private suspend fun loginWithKakaoTalk(activity: Activity): OAuthToken =
        suspendCancellableCoroutine { continuation ->
            UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                handleLoginResult(token, error, continuation)
            }
        }

    /** 웹 폴백 로그인. */
    private suspend fun loginWithKakaoAccount(activity: Activity): OAuthToken =
        suspendCancellableCoroutine { continuation ->
            UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
                handleLoginResult(token, error, continuation)
            }
        }

    /** 로그인 결과 콜백 — token + error 둘 중 하나는 채워짐. */
    private fun handleLoginResult(
        token: OAuthToken?,
        error: Throwable?,
        continuation: kotlinx.coroutines.CancellableContinuation<OAuthToken>,
    ) {
        if (!continuation.isActive) return
        if (error != null) {
            // 사용자 취소는 별도 분류.
            val message = error.message ?: ""
            if (message.contains("cancel", ignoreCase = true) || message.contains("Cancelled", ignoreCase = true)) {
                continuation.resumeWithException(IndieKitAuthError.UserCancelled())
            } else {
                continuation.resumeWithException(IndieKitAuthError.Underlying(underlying = error))
            }
            return
        }
        if (token == null) {
            continuation.resumeWithException(IndieKitAuthError.MissingCredential(provider = AuthProvider.KAKAO))
            return
        }
        continuation.resume(token)
    }

    /** 카카오 사용자 정보 (me) 받기. */
    private suspend fun fetchMe(): User = suspendCancellableCoroutine { continuation ->
        UserApiClient.instance.me { user, error ->
            if (!continuation.isActive) return@me
            if (error != null) {
                continuation.resumeWithException(IndieKitAuthError.Underlying(underlying = error))
            } else if (user == null) {
                continuation.resumeWithException(IndieKitAuthError.MissingCredential(provider = AuthProvider.KAKAO))
            } else {
                continuation.resume(user)
            }
        }
    }
}
