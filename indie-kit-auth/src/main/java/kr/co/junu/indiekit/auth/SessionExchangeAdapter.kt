/*
 * SessionExchangeAdapter.kt — indie-kit-auth
 *
 * 역할
 *  - 제공자 (구글/카카오) 가 준 신원 확인값을 우리 서버에 보내 세션 토큰을 받아오는 어댑터.
 *  - 백엔드 끝점 / 요청 형식 / 응답 형식이 앱마다 다르므로 두 람다로 풀어 둔다.
 *
 * 주요 개념
 *  - translateRequest: 어떤 JSON 을 우리 서버로 보낼지 결정. 앱이 자기 백엔드에 맞춰 작성.
 *  - translateResponse: 우리 서버 응답 (ByteArray) 을 SessionTokens 로 변환.
 *
 * iOS 자매 의 `SessionExchangeAdapter` struct 와 같은 모양.
 *
 * 사용 방법
 *  ```kotlin
 *  val backend = SessionExchangeAdapter(
 *      url = "https://api.example.com/auth/exchange",
 *      translateRequest = { provider, idToken, _ ->
 *          json.encodeToString(MyRequest.serializer(), MyRequest(provider.raw, idToken ?: ""))
 *      },
 *      translateResponse = { body ->
 *          val resp = json.decodeFromString<MyResponse>(body.decodeToString())
 *          SessionTokens(access = resp.accessToken, refresh = resp.refreshToken)
 *      }
 *  )
 *  ```
 *
 * 주의사항
 *  - 어댑터는 1회 POST 만 처리. OAuth code exchange 같은 복잡한 흐름이 필요하면 앱이 직접 만들어
 *    SessionExchangeAdapter 자리에 결과를 넣을 수도 있다 (이 경우 url 은 안 호출되지만 둬야 함).
 */

package kr.co.junu.indiekit.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/**
 * 신원 확인값 → 우리 서버 세션 어댑터.
 *
 * @property url 우리 서버 끝점 URL (예: "https://www.junu.co.kr/auth/social-login/").
 * @property method HTTP 메서드. 기본 "POST".
 * @property headers 추가 헤더 (예: API key 등).
 * @property translateRequest 신원 확인값 → 요청 본문 (JSON 문자열) 변환 람다.
 *   - 매개변수: provider (어느 제공자), idToken (제공자가 준 ID Token), accessToken (제공자가 준 액세스 토큰).
 *   - 반환: 우리 서버로 보낼 JSON 문자열.
 * @property translateResponse 우리 서버 응답 (응답 본문 ByteArray) → SessionTokens 변환 람다.
 * @property client HTTP 클라이언트. 기본 OkHttpClient — 라이브러리가 직접 만든 인스턴스.
 *   IndieKitNetwork 와 같은 인스턴스를 공유할 필요 없음 (인증 호출이라 별도 흐름).
 */
public class SessionExchangeAdapter(
    public val url: String,
    public val method: String = "POST",
    public val headers: Map<String, String> = emptyMap(),
    public val translateRequest: (provider: AuthProvider, idToken: String?, accessToken: String?) -> String,
    public val translateResponse: (body: ByteArray) -> SessionTokens,
    public val client: OkHttpClient = OkHttpClient(),
) {

    /**
     * 어댑터를 사용해 실제 우리 서버 호출 수행.
     * 내부 헬퍼 — IndieKitAuth.signIn 안에서 호출.
     *
     * 실패 시 IndieKitAuthError.BackendExchange 던짐.
     */
    internal suspend fun exchange(
        provider: AuthProvider,
        idToken: String?,
        accessToken: String?,
    ): SessionTokens = withContext(Dispatchers.IO) {
        try {
            val body = translateRequest(provider, idToken, accessToken)
                .toRequestBody(JSON_MEDIA)

            val requestBuilder = Request.Builder()
                .url(url)
                .method(method, body)
                .header("Content-Type", "application/json")

            for ((k, v) in headers) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IndieKitAuthError.BackendExchange(
                        underlying = RuntimeException("서버 응답 코드 ${response.code}")
                    )
                }
                val data = response.body?.bytes() ?: ByteArray(0)
                translateResponse(data)
            }
        } catch (e: IndieKitAuthError.BackendExchange) {
            throw e
        } catch (e: Throwable) {
            throw IndieKitAuthError.BackendExchange(underlying = e)
        }
    }
}
