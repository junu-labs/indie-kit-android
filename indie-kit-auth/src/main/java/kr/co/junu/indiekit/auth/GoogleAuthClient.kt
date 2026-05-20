/*
 * GoogleAuthClient.kt — indie-kit-auth
 *
 * 역할
 *  - Google 로그인 흐름.
 *  - androidx.credentials (Credential Manager) + GetGoogleIdOption 사용.
 *  - 옛 com.google.android.gms:play-services-auth 의 GoogleSignIn 은 deprecated — Credential Manager 가 표준.
 *
 * 주요 개념
 *  - Credential Manager 가 사용자 계정 선택 시트를 띄움 (시스템 시트 — 카카오 / 페북 / 구글 통합).
 *  - GetGoogleIdOption 으로 Google 계정만 필터링. setServerClientId 에 Google Cloud OAuth Web Client ID.
 *    (안드로이드 클라이언트 ID 가 아니라 Web Client ID. iOS 와 다름.)
 *  - 결과로 GoogleIdTokenCredential 받음 — id, displayName, email, idToken 포함.
 *
 * 주의사항
 *  - Google Cloud 콘솔 Android OAuth 클라이언트가 등록되어 있어야 함 (패키지 + SHA-1).
 *  - 추가로 동일 프로젝트의 Web Application OAuth 클라이언트도 필요 — serverClientId 자리에 들어감.
 *    Web 클라이언트는 Android 가 받은 ID Token 의 audience.
 *  - 시뮬레이터 에뮬레이터엔 구글 계정 동기화 (Settings → Accounts) 가 되어 있어야 시트가 뜸.
 */

package kr.co.junu.indiekit.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kr.co.junu.indiekit.core.IKLogger

internal object GoogleAuthClient {

    /**
     * Google 로그인.
     *
     * Credential Manager 가 시스템 시트를 띄워 사용자가 Google 계정 선택 후 동의.
     * GetGoogleIdOption 의 setServerClientId 에 Web Client ID 를 넣어야 idToken 의 audience 가 맞아진다.
     *
     * @param activity 사용처 Activity. CredentialManager.getCredential 의 context.
     * @param serverClientId Google Cloud OAuth Web Client ID — Google Console 의 Web Application 클라이언트.
     *   (참고: Android Client ID 가 아니다. Web 클라이언트도 별도로 만들어야 함.)
     */
    suspend fun signIn(activity: Activity, serverClientId: String): AuthUser {
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            // filterByAuthorizedAccounts=false: 처음 로그인 가능. true 면 이미 권한 준 계정만 보임.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            // autoSelectEnabled=true: 계정이 하나면 자동 선택 (시트 안 뜸).
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = try {
            credentialManager.getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw IndieKitAuthError.UserCancelled()
        } catch (e: NoCredentialException) {
            // 사용자 폰에 구글 계정이 없거나 시트에서 "취소" 누름.
            throw IndieKitAuthError.UserCancelled()
        } catch (e: GetCredentialException) {
            throw IndieKitAuthError.Underlying(underlying = e)
        }

        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            // 다른 종류의 Credential 받았음 — 우리가 요청한 GoogleIdToken 이 아님.
            throw IndieKitAuthError.MissingCredential(provider = AuthProvider.GOOGLE)
        }

        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: Throwable) {
            throw IndieKitAuthError.Underlying(underlying = e)
        }

        IKLogger.auth.info("Google 신원값 수신 완료")
        return AuthUser(
            id = googleCredential.id,
            provider = AuthProvider.GOOGLE,
            email = googleCredential.id, // GoogleIdTokenCredential.id 가 보통 이메일 (sub 아님). 일관성 위해 둘 다 둠.
            displayName = googleCredential.displayName,
            idToken = googleCredential.idToken,
            accessToken = null, // Credential Manager 는 access token 안 줌 — Google API 직접 호출은 별도 흐름.
        )
    }

    /**
     * Google 로그아웃.
     * Credential Manager 의 clearCredentialState 호출 — 이 앱이 가진 자격 증명 상태 비움.
     * 사용자 측 Google 계정 자체 로그아웃은 아님.
     */
    suspend fun signOut(activity: Activity) {
        try {
            val credentialManager = CredentialManager.create(activity)
            credentialManager.clearCredentialState(
                androidx.credentials.ClearCredentialStateRequest()
            )
            IKLogger.auth.info("Google 자격 증명 상태 비움")
        } catch (e: Throwable) {
            // 로그아웃 실패는 사용자 흐름 차단 안 함.
            IKLogger.auth.warning("Google 로그아웃 실패 (무시): ${e.message}")
        }
    }
}
