/*
 * IndieKitNetworkTests.kt — IndieKitNetwork 의 핵심 흐름 검증.
 *
 * 검증 방식
 *  - OkHttp 의 Interceptor 로 fake response 시퀀스를 주입한다 (실제 네트워크 없이).
 *  - 시나리오마다 응답 큐를 미리 채워 두고, 호출 순서대로 꺼내 응답.
 *
 * iOS 자매 (IndieKitNetworkTests.swift) 의 8개 테스트와 1:1 대응.
 */

package kr.co.junu.indiekit.network

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class IndieKitNetworkTests {

    // ────────────────────────────────────────────────────────────────────
    // 1. 단계 3 진입 표식
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `isPlaceholder 가 false 다`() {
        assertFalse(IndieKitNetwork.isPlaceholder)
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. GET — 응답 JSON 풀기
    // ────────────────────────────────────────────────────────────────────

    @Serializable
    data class Item(val name: String, val age: Int)

    @Test
    fun `GET 가 응답 JSON 을 자료 클래스로 푼다`() = runBlocking {
        val capturedMethod = AtomicReference<String>()
        val capturedPath = AtomicReference<String>()

        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            client = fakeClient(
                listOf(
                    FakeResponse(
                        statusCode = 200,
                        bodyJson = """{"name":"abc","age":7}"""
                    )
                ),
                onRequest = { request ->
                    capturedMethod.set(request.method)
                    capturedPath.set(request.url.encodedPath)
                }
            )
        )

        val item: Item = net.get("/items")
        assertEquals("GET", capturedMethod.get())
        assertEquals("/items", capturedPath.get())
        assertEquals("abc", item.name)
        assertEquals(7, item.age)
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. POST — body 인코딩 + 응답 디코딩
    // ────────────────────────────────────────────────────────────────────

    @Serializable
    data class NewUser(val name: String)

    @Serializable
    data class CreatedUser(val id: String, val name: String)

    @Test
    fun `POST 가 body 를 보내고 응답 자료 클래스를 푼다`() = runBlocking {
        val capturedBody = AtomicReference<String>()

        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            client = fakeClient(
                listOf(
                    FakeResponse(
                        statusCode = 200,
                        bodyJson = """{"id":"u_1","name":"Alice"}"""
                    )
                ),
                onRequest = { request ->
                    val buffer = okio.Buffer()
                    request.body?.writeTo(buffer)
                    capturedBody.set(buffer.readUtf8())
                }
            )
        )

        val created: CreatedUser = net.post("/users", body = NewUser(name = "Alice"))

        assertNotNull(capturedBody.get())
        // 보낸 body 가 JSON 으로 정확히 인코딩됐는지.
        assertTrue(
            "body 안에 \"name\":\"Alice\" 가 있어야 함 (실제: ${capturedBody.get()})",
            capturedBody.get().contains("\"name\":\"Alice\"")
        )
        assertEquals("u_1", created.id)
        assertEquals("Alice", created.name)
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. HTTP 4xx → Http 에러 (401 자동 갱신 외)
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `2xx 가 아니면 Http 에러를 던진다`() = runBlocking {
        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            client = fakeClient(
                listOf(FakeResponse(statusCode = 404, bodyJson = "not found"))
            )
        )

        try {
            net.get<Map<String, String>>("/missing")
            fail("404 인데 에러 안 던짐")
        } catch (e: IndieKitNetworkError.Http) {
            assertEquals(404, e.statusCode)
            assertEquals("not found", e.body.toString(Charsets.UTF_8))
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 5. 401 → tokenRefresher 호출 → 두번째 200 → 정상 통과
    // ────────────────────────────────────────────────────────────────────

    @Serializable
    data class OkResp(val ok: Boolean)

    @Test
    fun `401 이면 갱신 후 같은 요청을 한 번 더 시도한다`() = runBlocking {
        val refreshCount = AtomicInteger(0)

        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            tokenProvider = { "current_token" },
            tokenRefresher = { refreshCount.incrementAndGet() },
            client = fakeClient(
                listOf(
                    FakeResponse(statusCode = 401, bodyJson = ""),
                    FakeResponse(statusCode = 200, bodyJson = """{"ok":true}""")
                )
            )
        )

        val resp: OkResp = net.get("/protected")
        assertTrue(resp.ok)
        assertEquals(1, refreshCount.get())
    }

    // ────────────────────────────────────────────────────────────────────
    // 6. 두 번 연속 401 → Unauthorized
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `두 번 연속 401 이면 Unauthorized 를 던진다`() = runBlocking {
        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            tokenProvider = { "current_token" },
            tokenRefresher = { /* 갱신 시도해도 안 통한 상태 */ },
            client = fakeClient(
                listOf(
                    FakeResponse(statusCode = 401, bodyJson = ""),
                    FakeResponse(statusCode = 401, bodyJson = "")
                )
            )
        )

        try {
            net.get<Map<String, String>>("/protected")
            fail("두 번 401 인데 에러 안 던짐")
        } catch (e: IndieKitNetworkError.Unauthorized) {
            // 정상.
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 7. tokenRefresher 가 null 이면 첫 401 에 즉시 Unauthorized
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `refresher 가 없으면 첫 401 에 바로 Unauthorized 다`() = runBlocking {
        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            client = fakeClient(
                listOf(FakeResponse(statusCode = 401, bodyJson = ""))
            )
        )

        try {
            net.get<Map<String, String>>("/protected")
            fail("401 인데 에러 안 던짐")
        } catch (e: IndieKitNetworkError.Unauthorized) {
            // 정상.
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 8. logger 가 호출 한 건당 한 번 통지받는지
    // ────────────────────────────────────────────────────────────────────

    @Test
    fun `logger 가 호출 한 건당 한 번 통지받는다`() = runBlocking {
        val entries = mutableListOf<HTTPLogEntry>()

        val net = IndieKitNetwork(
            baseURL = "https://api.test",
            client = fakeClient(
                listOf(FakeResponse(statusCode = 200, bodyJson = """{"ok":true}"""))
            ),
            logger = { entry -> entries.add(entry) }
        )

        net.get<OkResp>("/items")

        assertEquals(1, entries.size)
        assertEquals("GET", entries[0].method)
        assertEquals(200, entries[0].statusCode)
        assertFalse(entries[0].didRefreshToken)
    }

    // ════════════════════════════════════════════════════════════════════
    // 헬퍼 — OkHttp Interceptor 로 응답 시퀀스 주입
    // ════════════════════════════════════════════════════════════════════

    /** 가짜 응답 한 건의 자료. */
    data class FakeResponse(
        val statusCode: Int,
        val bodyJson: String,
        val contentType: String = "application/json"
    )

    /**
     * 응답 큐를 순서대로 돌려주는 가짜 OkHttpClient.
     *
     * @param responses 호출 순서대로 사용될 응답 목록.
     * @param onRequest 매 요청을 가로채는 콜백 (검증용 — request 자체를 들여다볼 때).
     */
    private fun fakeClient(
        responses: List<FakeResponse>,
        onRequest: ((okhttp3.Request) -> Unit)? = null
    ): OkHttpClient {
        val index = AtomicInteger(0)
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            onRequest?.invoke(request)

            val pos = index.getAndIncrement()
            if (pos >= responses.size) {
                error("fakeClient 응답 큐가 비었음 — 호출 ${pos + 1}회 째에 응답 없음.")
            }
            val mock = responses[pos]

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(mock.statusCode)
                .message(if (mock.statusCode in 200..299) "OK" else "ERR")
                .body(mock.bodyJson.toResponseBody(mock.contentType.toMediaType()))
                .build()
        }
        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }
}
