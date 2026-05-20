/*
 * IndieKitAuthTests.kt — indie-kit-auth
 *
 * 단위 테스트 — 라이브러리의 자료 타입과 오류 타입 동작 확인.
 * 실제 Google / Kakao SDK 호출은 단위 테스트에서 못 함 (Android Activity / Context 필요).
 * 그 부분은 데모 앱 실 폰 검증에서 확인.
 */

package kr.co.junu.indiekit.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndieKitAuthTests {

    @Test
    fun `AuthProvider 의 raw 값이 안정 식별자다`() {
        // Java/Kotlin enum 이름 바뀌면 raw 값으로 사용처 깨지지 않게 raw 를 따로 노출.
        assertEquals("google", AuthProvider.GOOGLE.raw)
        assertEquals("kakao", AuthProvider.KAKAO.raw)
        // Apple 케이스 없음 — 안드로이드 자매 라이브러리는 Apple 미지원.
        assertEquals(2, AuthProvider.entries.size)
    }

    @Test
    fun `AuthUser 가 같은 필드면 equals true`() {
        val a = AuthUser(
            id = "abc",
            provider = AuthProvider.GOOGLE,
            email = "a@b.com",
            displayName = "이름",
            idToken = "tok",
        )
        val b = AuthUser(
            id = "abc",
            provider = AuthProvider.GOOGLE,
            email = "a@b.com",
            displayName = "이름",
            idToken = "tok",
        )
        assertEquals(a, b)
    }

    @Test
    fun `AuthUser 기본 nullable 필드는 null`() {
        val u = AuthUser(id = "abc", provider = AuthProvider.KAKAO)
        assertNull(u.email)
        assertNull(u.displayName)
        assertNull(u.idToken)
        assertNull(u.accessToken)
        assertNull(u.serverSession)
    }

    @Test
    fun `SessionTokens refresh 와 expiresAt 은 옵션`() {
        val t = SessionTokens(access = "a")
        assertEquals("a", t.access)
        assertNull(t.refresh)
        assertNull(t.expiresAtMillis)
    }

    @Test
    fun `ProviderNotConfigured 메시지에 제공자 raw 포함`() {
        val e = IndieKitAuthError.ProviderNotConfigured(provider = AuthProvider.GOOGLE)
        assertTrue(e.message!!.contains("google"))
        assertEquals(AuthProvider.GOOGLE, e.provider)
    }

    @Test
    fun `UserCancelled 는 사용자 친화 메시지`() {
        val e = IndieKitAuthError.UserCancelled()
        assertNotNull(e.message)
        assertTrue(e.message!!.contains("취소"))
    }

    @Test
    fun `BackendExchange 가 underlying 의 메시지를 풀어 담는다`() {
        val cause = RuntimeException("서버 응답 코드 502")
        val e = IndieKitAuthError.BackendExchange(underlying = cause)
        assertTrue(e.message!!.contains("502"))
        assertEquals(cause, e.underlying)
    }

    @Test
    fun `Underlying 도 메시지를 풀어 담는다`() {
        val cause = IllegalStateException("뭔가 잘못됨")
        val e = IndieKitAuthError.Underlying(underlying = cause)
        assertTrue(e.message!!.contains("잘못됨"))
        assertEquals(cause, e.underlying)
    }

    @Test
    fun `SessionExchangeAdapter 가 두 람다를 갖춘다`() {
        var translateCallCount = 0
        val adapter = SessionExchangeAdapter(
            url = "https://example.com/auth",
            translateRequest = { _, _, _ ->
                translateCallCount++
                "{}"
            },
            translateResponse = { _ -> SessionTokens(access = "x") },
        )
        assertEquals("https://example.com/auth", adapter.url)
        assertEquals("POST", adapter.method)
        assertEquals("{}", adapter.translateRequest(AuthProvider.GOOGLE, "idtok", null))
        assertEquals(1, translateCallCount)
    }

    @Test
    fun `IndieKitAuth object 가 싱글턴`() {
        // 두 번 참조해도 같은 인스턴스.
        val a = IndieKitAuth
        val b = IndieKitAuth
        assertTrue(a === b)
        // currentUser StateFlow 가 비었음 (configure 안 한 상태에선 null).
        // (다른 테스트에서 configure 호출했을 수 있어 강한 단언은 안 함.)
        assertNotNull(a.currentUser)
        assertNotNull(a.isReady)
    }
}
