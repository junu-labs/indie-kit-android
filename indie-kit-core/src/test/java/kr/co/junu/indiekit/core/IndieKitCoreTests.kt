/*
 * IndieKitCoreTests.kt
 *
 * IndieKitCore 의 기본 동작 확인 — 로거 카테고리, 에러 메시지, 약한 연결 통로, AnalyticsValue 헬퍼.
 *
 * iOS 자매 (`IndieKitCoreTests.swift`) 와 같은 4개 시나리오를 JUnit 4 로 옮김.
 */

package kr.co.junu.indiekit.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.locks.ReentrantLock

class IndieKitCoreTests {

    @Before
    fun setUp() {
        // 테스트 간 영향 차단 — 어느 테스트가 먼저 돌든 빈 상태에서 시작.
        AnalyticsBus.unregister()
    }

    @After
    fun tearDown() {
        AnalyticsBus.unregister()
    }

    /**
     * IKLogger 의 subsystem 이 모든 카테고리에서 동일한지.
     * Logcat 에서 단일 필터로 IndieKit 전체 로그를 모아 보기 위한 약속.
     * iOS 자매와도 같은 값 — 양쪽 폰 동일 식별.
     */
    @Test
    fun `logger subsystem is unified across modules`() {
        assertEquals("kr.kr.co.junu.indiekit", IKLogger.subsystem)
    }

    /**
     * IndieKitConfigurationError 의 사용자 친화 메시지가 한국어로 잘 나오는지.
     */
    @Test
    fun `configuration error messages are in Korean and contain context`() {
        val notConfigured = IndieKitConfigurationError.NotConfigured(module = "IndieKitAds")
        assertNotNull(notConfigured.message)
        assertTrue(notConfigured.message!!.contains("IndieKitAds"))
        assertTrue(notConfigured.message!!.contains("configure"))

        val missing = IndieKitConfigurationError.MissingRequiredValue(
            module = "IndieKitAuth",
            key = "googleServerClientID"
        )
        assertTrue(missing.message!!.contains("googleServerClientID"))
        assertTrue(missing.message!!.contains("IndieKitAuth"))

        val already = IndieKitConfigurationError.AlreadyConfigured(module = "IndieKitBilling")
        assertTrue(already.message!!.contains("IndieKitBilling"))
        assertTrue(already.message!!.contains("이미"))
    }

    /**
     * 약한 연결 통로 — 등록 / 기록 / 해제 흐름.
     */
    @Test
    fun `analytics bus registration and recording flow`() {
        // 시작은 비어 있어야 함 (setUp 에서 unregister 했음)
        assertFalse(AnalyticsBus.isRegistered)

        // 가짜 기록자 등록
        val recorder = SpyRecorder()
        AnalyticsBus.register(recorder)
        assertTrue(AnalyticsBus.isRegistered)

        // 기록 → 가짜 기록자에 도착
        val params = analyticsParams("mode" to "hiit", "duration" to 60)
        AnalyticsBus.record("test_event", params)

        assertEquals(1, recorder.events.size)
        assertEquals("test_event", recorder.events.first().first)
        assertEquals(params, recorder.events.first().second)

        // 정리
        AnalyticsBus.unregister()
        assertFalse(AnalyticsBus.isRegistered)
    }

    /**
     * AnalyticsBus.record 호출 시 등록된 기록자가 없어도 크래시 없음.
     * 통계 모듈을 안 쓰는 앱이 광고 모듈만 깐 시나리오 — 그래도 안전해야 함.
     */
    @Test
    fun `record without registered recorder does not crash`() {
        assertFalse(AnalyticsBus.isRegistered)
        // 크래시 없이 통과해야 함 — 내부에서 Logcat debug 로 흘러감
        AnalyticsBus.record("ad_impression", analyticsParams("format" to "banner"))
    }

    /**
     * AnalyticsValue 헬퍼 (analyticsParams) — 호출 측에서 자연스럽게 쓰이는지.
     * iOS 의 ExpressibleByLiteral 패턴 대응.
     */
    @Test
    fun `analytics params helper wraps primitive types`() {
        val map = analyticsParams(
            "mode" to "hiit",
            "duration" to 60,
            "ratio" to 0.85,
            "is_pro" to false
        )

        assertEquals(AnalyticsValue.StringValue("hiit"), map["mode"])
        assertEquals(AnalyticsValue.IntValue(60), map["duration"])
        assertEquals(AnalyticsValue.DoubleValue(0.85), map["ratio"])
        assertEquals(AnalyticsValue.BoolValue(false), map["is_pro"])
    }

    /**
     * AnalyticsValue 가 받지 않는 타입은 호출자에게 명확한 예외로.
     */
    @Test(expected = IllegalArgumentException::class)
    fun `analytics params helper rejects unsupported type`() {
        analyticsParams("date" to System.currentTimeMillis().toString().toCharArray())
        // CharArray 는 지원 타입이 아님 — IllegalArgumentException
    }
}

// MARK: - 테스트용 가짜 기록자

/**
 * 등록된 record 호출을 그대로 모아 두는 단순 기록자.
 * 동시 호출 검증이 필요한 테스트가 추가되면 lock 으로 보호된 형태로 확장.
 */
private class SpyRecorder : AnalyticsRecorder {
    private val lock = ReentrantLock()
    private val mutableEvents: MutableList<Pair<String, Map<String, AnalyticsValue>?>> = mutableListOf()

    val events: List<Pair<String, Map<String, AnalyticsValue>?>>
        get() {
            lock.lock()
            try {
                return mutableEvents.toList()
            } finally {
                lock.unlock()
            }
        }

    override fun record(event: String, parameters: Map<String, AnalyticsValue>?) {
        lock.lock()
        try {
            mutableEvents.add(event to parameters)
        } finally {
            lock.unlock()
        }
    }
}
