/*
 * NetworkError.kt — IndieKitNetwork
 *
 * 역할
 *  - IndieKitNetwork 호출이 실패할 때 던져지는 예외 종류 모음.
 *  - sealed class — `when` 분기에서 컴파일러가 모든 경우를 강제.
 *
 * iOS 자매 (IndieKitNetworkError enum) 와 1:1 대응.
 *  - invalidUrl       ↔ InvalidUrl
 *  - http(code, body) ↔ Http(statusCode, body)
 *  - decoding(cause)  ↔ Decoding(cause)
 *  - transport(cause) ↔ Transport(cause)
 *  - unauthorized     ↔ Unauthorized
 */

package kr.co.junu.indiekit.network

import java.io.IOException

/**
 * 네트워크 호출 중 발생할 수 있는 예외.
 *
 * `Exception` 을 상속 — 호출 측이 try / catch 로 잡거나 코루틴 안에서 자연스럽게 전파된다.
 *
 * @param message 사용자 표시용 메시지 (LocalizedError 와 같은 역할).
 */
public sealed class IndieKitNetworkError(message: String) : Exception(message) {

    /** path 와 baseURL 을 합쳐 URL 을 만들 수 없을 때. */
    public data object InvalidUrl : IndieKitNetworkError(
        "잘못된 URL — baseURL 과 path 를 합칠 수 없습니다."
    )

    /**
     * HTTP 응답 코드가 2xx 가 아닐 때 (단, 401 자동 갱신은 별도).
     * `body` 에는 응답 본문 그대로. 사용자가 에러 메시지 파싱하는 데 사용.
     */
    public class Http(
        public val statusCode: Int,
        public val body: ByteArray
    ) : IndieKitNetworkError("HTTP $statusCode 응답.")

    /** 응답 JSON 풀기 실패. */
    public class Decoding(
        public val underlying: Throwable
    ) : IndieKitNetworkError("응답 본문 풀기 실패: ${underlying.message}")

    /** 네트워크 자체 오류 (전송 실패, 타임아웃 등). */
    public class Transport(
        public val underlying: IOException
    ) : IndieKitNetworkError("네트워크 오류: ${underlying.message}")

    /**
     * 401 응답이 두 번째에도 — 인증값 재발급 후 재시도해도 실패.
     * 앱은 이 에러를 받으면 사용자를 로그인 화면으로.
     */
    public data object Unauthorized : IndieKitNetworkError(
        "인증 만료 — 다시 로그인이 필요합니다."
    )
}
