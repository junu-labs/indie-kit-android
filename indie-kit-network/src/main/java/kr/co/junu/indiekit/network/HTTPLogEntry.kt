/*
 * HTTPLogEntry.kt — IndieKitNetwork
 *
 * 역할
 *  - 네트워크 한 건의 호출 결과를 한 줄로 요약하는 자료.
 *  - IndieKitNetwork 의 logger 람다를 통해 호출 측 (앱) 으로 전달.
 *
 * 사용 예
 *  ```kotlin
 *  val net = IndieKitNetwork(
 *      logger = { entry ->
 *          IKLogger.network.info("[${'$'}{entry.method}] ${'$'}{entry.url} → ${'$'}{entry.statusCode ?: 0} (${'$'}{entry.durationMs}ms)")
 *      }
 *  )
 *  ```
 *
 * iOS 자매 (HTTPLogEntry struct) 와 필드 1:1 대칭.
 */

package kr.co.junu.indiekit.network

/**
 * 네트워크 호출 한 건의 요약.
 */
public data class HTTPLogEntry(

    /** HTTP 메서드 (GET / POST / PUT / DELETE). */
    public val method: String,

    /** 호출한 전체 URL (문자열 형태). */
    public val url: String,

    /** 응답 상태 코드. 응답을 못 받은 경우 (네트워크 오류) 는 null. */
    public val statusCode: Int?,

    /** 호출 시작부터 응답 도착까지 걸린 시간 (ms). */
    public val durationMs: Int,

    /** 응답 본문 크기 (bytes). 응답 없으면 0. */
    public val bodySize: Int,

    /** 인증값 자동 갱신이 일어났는지 (401 → 갱신 → 재시도). */
    public val didRefreshToken: Boolean
)
