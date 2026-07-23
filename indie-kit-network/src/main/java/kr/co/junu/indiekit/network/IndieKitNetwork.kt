/*
 * IndieKitNetwork.kt — IndieKitNetwork
 *
 * 역할
 *  - OkHttp 위에 얇은 호출 묶음. JSON 자동 풀기 (kotlinx.serialization), 인증값 자동 첨부, 401 자동 갱신까지.
 *
 * 주요 개념
 *  - class — 인스턴스를 앱이 직접 만들어 의존성 주입처럼 쓴다 (object 싱글톤 아님).
 *  - tokenProvider: 매 요청 직전에 호출되어 인증값을 받는 람다 (suspend). 없으면 인증 헤더 안 붙음.
 *  - tokenRefresher: 401 응답을 받으면 한 번 호출되는 람다 (suspend). 호출 후 같은 요청을 한 번 더 시도.
 *                    두 번째에도 401 이면 IndieKitNetworkError.Unauthorized 던짐 → 앱이 로그인 화면으로.
 *  - logger: 모든 호출의 요약 (메서드 / URL / 상태코드 / 소요 / 크기 / 갱신여부) 을 한 건씩 통지.
 *
 * 사용 방법
 *  ```kotlin
 *  val net = IndieKitNetwork(
 *      baseURL = "https://api.example.com",
 *      defaultHeaders = mapOf("Accept-Language" to "ko"),
 *      tokenProvider  = { session.accessToken },
 *      tokenRefresher = { session.refresh() },
 *      logger = { entry -> IKLogger.network.info("${'$'}{entry.method} ${'$'}{entry.url} → ${'$'}{entry.statusCode ?: 0}") }
 *  )
 *
 *  val info: AppInfo  = net.get("/appInfo")
 *  val user: User     = net.post("/users", body = NewUser(name = "Alice"))
 *  net.delete("/items/42")
 *
 *  // 사진 / 파일 올리기 (multipart/form-data):
 *  val poll: Poll = net.postMultipart(
 *      "/polls/",
 *      fields = mapOf("content" to "제목", "deadline_preset" to "24h"),
 *      files = listOf(MultipartFile(field = "image", bytes = jpegBytes, fileName = "photo.jpg", mimeType = "image/jpeg"))
 *  )
 *  ```
 *
 * 주의사항
 *  - 멀티파트 업로드는 `postMultipart` 로 지원 (사진 / 파일 올리기). 인증 첨부·401 갱신 그대로 작동.
 *  - 진행률 콜백, 자동 재시도 (네트워크 일시 단절) 는 범위 밖.
 *    필요하면 `send(Request)` raw 출구를 통해 직접 처리.
 *  - inline reified 함수가 호출하는 internal 헬퍼는 @PublishedApi internal — 사용처에서 직접 호출은 안 되지만
 *    inline 함수 본문에서는 보인다.
 */

package kr.co.junu.indiekit.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * raw send 출구의 응답.
 *
 * OkHttp 의 `Response` 는 본문을 한 번만 읽을 수 있고 닫아야 하므로,
 * 라이브러리가 본문을 미리 읽어 ByteArray 로 박아 돌려준다. 사용처는 close 책임 없음.
 *
 * iOS 자매의 `(Data, HTTPURLResponse)` 와 같은 위치.
 */
public class RawResponse(
    public val statusCode: Int,
    public val headers: Map<String, String>,
    public val body: ByteArray
)

/**
 * IndieKitNetwork — OkHttp 위 얇은 호출 묶음.
 *
 * @param baseURL 모든 호출의 base. null 이면 path 자리에 절대 URL 을 직접 넣는다.
 * @param defaultHeaders 모든 요청에 자동 첨부될 헤더. 호출별 headers 가 우선.
 * @param timeoutMs 한 호출의 전체 timeout (callTimeout). 0 이면 무한.
 * @param tokenProvider 매 요청 직전 인증값을 가져오는 람다. null 이면 인증 헤더 안 붙음.
 * @param tokenRefresher 401 응답 시 한 번 호출되는 인증값 갱신 람다. null 이면 401 즉시 Unauthorized.
 * @param client OkHttpClient. 기본은 timeoutMs 적용된 새 인스턴스. 사용자가 자기 client (interceptor, cookie jar 등) 를 주입 가능.
 * @param json kotlinx.serialization Json 설정. 기본은 unknownKeys 무시 + isLenient.
 * @param logger 호출 한 건마다 통지되는 람다. null 이면 통지 안 함.
 * @param authorizationHeader 인증 헤더 이름. 기본 "Authorization", 값 형식 "Bearer {token}".
 */
public class IndieKitNetwork(
    public val baseURL: String? = null,
    public val defaultHeaders: Map<String, String> = emptyMap(),
    public val timeoutMs: Long = 30_000L,
    public val tokenProvider: (suspend () -> String?)? = null,
    public val tokenRefresher: (suspend () -> Unit)? = null,
    @PublishedApi internal val client: OkHttpClient = defaultClient(timeoutMs),
    @PublishedApi internal val json: Json = defaultJson,
    @PublishedApi internal val logger: ((HTTPLogEntry) -> Unit)? = null,
    public val authorizationHeader: String = "Authorization"
) {

    public companion object {

        /** 단계 3 진입 표식 — placeholder 가 실 구현으로 교체됨. */
        public const val isPlaceholder: Boolean = false

        /** 기본 Json — 모르는 키 무시 + 표준 외 형식 허용. */
        private val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * 기본 OkHttpClient — callTimeout 적용.
         * 사용자가 자기 client 를 주입하면 이 함수는 호출되지 않는다.
         */
        private fun defaultClient(timeoutMs: Long): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }

    // ────────────────────────────────────────────────────────────────────
    // 핵심 호출 (GET / POST / PUT / DELETE)
    // ────────────────────────────────────────────────────────────────────

    /**
     * GET — 응답 JSON 을 R 로 풀어 돌려준다.
     *
     * `inline reified` — 호출 측에서 R 의 KSerializer 를 자동으로 얻기 위해.
     */
    public suspend inline fun <reified R> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): R {
        val request = buildRequest("GET", path, query, body = null, contentType = null, headers = headers)
        val response = send(request)
        return decode(response.body, serializer<R>())
    }

    /**
     * POST — body 를 JSON 으로 보내고 응답 JSON 을 R 로 풀어 돌려준다.
     *
     * B / R 모두 @Serializable 이어야 한다 (kotlinx.serialization).
     */
    public suspend inline fun <reified B, reified R> post(
        path: String,
        body: B,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): R {
        val encoded = encodeJson(body, serializer<B>())
        val request = buildRequest("POST", path, query, body = encoded, contentType = JSON_MEDIA, headers = headers)
        val response = send(request)
        return decode(response.body, serializer<R>())
    }

    /**
     * PUT — body 를 JSON 으로 보내고 응답 JSON 을 R 로 풀어 돌려준다.
     */
    public suspend inline fun <reified B, reified R> put(
        path: String,
        body: B,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): R {
        val encoded = encodeJson(body, serializer<B>())
        val request = buildRequest("PUT", path, query, body = encoded, contentType = JSON_MEDIA, headers = headers)
        val response = send(request)
        return decode(response.body, serializer<R>())
    }

    /**
     * DELETE — 응답 본문을 무시.
     */
    public suspend fun delete(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ) {
        val request = buildRequest("DELETE", path, query, body = null, contentType = null, headers = headers)
        send(request)
    }

    /**
     * multipart/form-data 로 글자 필드 + 파일을 함께 보내고 응답 JSON 을 R 로 풀어 돌려준다.
     *
     * 서버가 "사진 있으면 multipart, 없으면 JSON" 규칙일 때 사진 있는 쪽 담당.
     * 인증 헤더 자동 첨부 + 401 자동 갱신은 다른 호출과 똑같이 작동한다.
     *
     * iOS 자매의 `postMultipart` 와 같은 위치.
     *
     * @param fields 글자 값 필드들. JSON 배열을 문자열로 넣어야 하면 미리 문자열로 만들어 넣는다.
     * @param files 함께 올릴 파일들. 비우면 파일 없는 multipart.
     */
    public suspend inline fun <reified R> postMultipart(
        path: String,
        fields: Map<String, String> = emptyMap(),
        files: List<MultipartFile> = emptyList(),
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): R {
        val request = buildMultipartRequest(path, query, fields, files, headers)
        val response = send(request)
        return decode(response.body, serializer<R>())
    }

    /**
     * raw 출구 — Request 를 직접 만들어 보낼 때.
     * 인증 헤더 자동 첨부 + 401 자동 갱신은 그대로 작동.
     *
     * iOS 자매의 `send(URLRequest)` 와 같은 위치.
     */
    public suspend fun send(request: Request): RawResponse =
        performWithRefresh(request)

    // ────────────────────────────────────────────────────────────────────
    // 401 자동 갱신 흐름
    // ────────────────────────────────────────────────────────────────────

    /**
     * 1) tokenProvider 로 인증값 받아 헤더에 첨부 → 호출.
     * 2) 401 이고 tokenRefresher 가 있으면 → 갱신 → 같은 요청 한 번 더.
     * 3) 두 번째에도 401 이면 Unauthorized.
     */
    private suspend fun performWithRefresh(request: Request): RawResponse {
        val started = System.currentTimeMillis()

        val firstAttempt = withAuthHeader(request)
        val first = executeRaw(firstAttempt)

        if (first.statusCode != 401) {
            // 보통 흐름.
            log(method = request.method, url = request.url, response = first, started = started, didRefresh = false)
            checkStatus(first)
            return first
        }

        // 401 — 갱신 가능하면 한 번 시도.
        val refresher = tokenRefresher
        if (refresher == null) {
            log(method = request.method, url = request.url, response = first, started = started, didRefresh = false)
            throw IndieKitNetworkError.Unauthorized
        }

        refresher.invoke()

        val secondAttempt = withAuthHeader(request)
        val second = executeRaw(secondAttempt)
        log(method = request.method, url = request.url, response = second, started = started, didRefresh = true)

        if (second.statusCode == 401) {
            throw IndieKitNetworkError.Unauthorized
        }
        checkStatus(second)
        return second
    }

    /**
     * tokenProvider 가 있으면 인증 헤더를 붙인 새 Request 를 돌려준다.
     */
    private suspend fun withAuthHeader(request: Request): Request {
        val provider = tokenProvider ?: return request
        val token = provider.invoke() ?: return request
        return request.newBuilder()
            .header(authorizationHeader, "Bearer $token")
            .build()
    }

    /**
     * OkHttp 직접 호출 + IOException 잡아서 Transport 에러로 감쌈.
     *
     * `withContext(Dispatchers.IO)` — OkHttp 의 동기 execute() 는 blocking 이라 IO 디스패처에서.
     */
    private suspend fun executeRaw(request: Request): RawResponse =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    val headers = response.headers.toMultimap()
                        .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
                    RawResponse(response.code, headers, bytes)
                }
            } catch (e: IOException) {
                throw IndieKitNetworkError.Transport(e)
            }
        }

    /**
     * 상태 코드가 2xx 가 아니면 Http 에러.
     */
    private fun checkStatus(response: RawResponse) {
        if (response.statusCode !in 200..299) {
            throw IndieKitNetworkError.Http(response.statusCode, response.body)
        }
    }

    /**
     * 로그 한 건 통지.
     */
    private fun log(
        method: String,
        url: HttpUrl,
        response: RawResponse?,
        started: Long,
        didRefresh: Boolean
    ) {
        val logger = logger ?: return
        val durationMs = (System.currentTimeMillis() - started).toInt()
        val entry = HTTPLogEntry(
            method = method,
            url = url.toString(),
            statusCode = response?.statusCode,
            durationMs = durationMs,
            bodySize = response?.body?.size ?: 0,
            didRefreshToken = didRefresh
        )
        logger.invoke(entry)
    }

    // ────────────────────────────────────────────────────────────────────
    // 빌드 헬퍼 (inline reified 함수에서 호출되므로 @PublishedApi internal)
    // ────────────────────────────────────────────────────────────────────

    /**
     * Request 를 빌드한다. baseURL + path + query + body + headers + 기본 헤더 합쳐서.
     */
    @PublishedApi
    internal fun buildRequest(
        method: String,
        path: String,
        query: Map<String, String>,
        body: ByteArray?,
        contentType: String?,
        headers: Map<String, String>
    ): Request {
        val url = resolveUrl(path, query)

        val builder = Request.Builder().url(url)

        // 기본 헤더 → 호출별 헤더 순으로 적용 (호출별이 우선).
        for ((k, v) in defaultHeaders) builder.header(k, v)
        for ((k, v) in headers) builder.header(k, v)

        val requestBody = body?.let {
            it.toRequestBody((contentType ?: JSON_MEDIA).toMediaType())
        }

        when (method) {
            "GET" -> builder.get()
            "DELETE" -> {
                if (requestBody != null) builder.delete(requestBody) else builder.delete()
            }
            "POST" -> builder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> builder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            else -> builder.method(method, requestBody)
        }

        return builder.build()
    }

    /**
     * multipart/form-data 요청을 빌드한다. OkHttp 의 MultipartBody 로 글자 필드 → 파일 순으로 담는다.
     * boundary / Content-Type 은 OkHttp 가 알아서 채운다.
     */
    @PublishedApi
    internal fun buildMultipartRequest(
        path: String,
        query: Map<String, String>,
        fields: Map<String, String>,
        files: List<MultipartFile>,
        headers: Map<String, String>
    ): Request {
        val url = resolveUrl(path, query)

        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        for ((k, v) in fields) bodyBuilder.addFormDataPart(k, v)
        for (f in files) {
            bodyBuilder.addFormDataPart(
                f.field,
                f.fileName,
                f.bytes.toRequestBody(f.mimeType.toMediaType())
            )
        }

        val builder = Request.Builder().url(url)
        // 기본 헤더 → 호출별 헤더 순 (호출별 우선). Content-Type 은 MultipartBody 가 boundary 와 함께 지정.
        for ((k, v) in defaultHeaders) builder.header(k, v)
        for ((k, v) in headers) builder.header(k, v)
        builder.post(bodyBuilder.build())

        return builder.build()
    }

    /**
     * JSON 인코딩 — kotlinx.serialization Json 으로 ByteArray.
     */
    @PublishedApi
    internal fun <T> encodeJson(value: T, serializer: KSerializer<T>): ByteArray =
        json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)

    /**
     * JSON 디코딩 — 실패 시 Decoding 에러로 감쌈.
     */
    @PublishedApi
    internal fun <T> decode(bytes: ByteArray, serializer: KSerializer<T>): T {
        return try {
            json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
        } catch (e: Throwable) {
            throw IndieKitNetworkError.Decoding(e)
        }
    }

    /**
     * baseURL + path + query 를 합쳐 HttpUrl 로.
     *
     * - baseURL 이 있으면: base 의 path 끝과 새 path 사이 슬래시 정리, 그 뒤 query 추가.
     * - baseURL 이 없으면: path 가 절대 URL 이어야.
     */
    private fun resolveUrl(path: String, query: Map<String, String>): HttpUrl {
        val combined: HttpUrl = if (baseURL != null) {
            val baseHttpUrl = baseURL.toHttpUrlOrNull() ?: throw IndieKitNetworkError.InvalidUrl

            // base 의 path 가 있으면 trailing slash 제거 후 새 path 이어 붙임.
            val basePath = baseHttpUrl.encodedPath.removeSuffix("/")
            val leadingSlashPath = if (path.startsWith("/")) path else "/$path"
            val fullPath = basePath + leadingSlashPath

            baseHttpUrl.newBuilder().encodedPath(fullPath).build()
        } else {
            path.toHttpUrlOrNull() ?: throw IndieKitNetworkError.InvalidUrl
        }

        if (query.isEmpty()) return combined

        val builder = combined.newBuilder()
        for ((k, v) in query) builder.addQueryParameter(k, v)
        return builder.build()
    }
}

/** body 가 있을 때의 기본 Content-Type. */
@PublishedApi
internal const val JSON_MEDIA: String = "application/json; charset=utf-8"

/**
 * multipart/form-data 로 올릴 파일 한 개.
 *
 * `postMultipart` 의 `files` 에 담아 보낸다. 사진 한 장이면 원소 하나짜리 리스트.
 *
 * iOS 자매의 `MultipartFile` 과 1:1 대응.
 *
 * @param field 서버가 받는 폼 필드 이름 (예: "image").
 * @param bytes 파일 내용.
 * @param fileName 서버에 알려 줄 파일 이름. 서버가 확장자로 종류를 볼 수 있으니 실제 확장자에 맞춘다.
 * @param mimeType 파일 종류 (예: "image/jpeg", "image/png"). 모르면 "application/octet-stream".
 */
public class MultipartFile(
    public val field: String,
    public val bytes: ByteArray,
    public val fileName: String = "file",
    public val mimeType: String = "application/octet-stream"
)
