/*
 * IndieKitAnalyticsTests.kt
 *
 * 1단계 — IndieKitAnalytics 실 구현 동작 확인.
 *
 * 주의
 *  - Firebase Analytics 자체 호출 (logEvent 등) 은 단위 테스트로 검증하기 까다로움.
 *    Firebase 는 정적 메서드 + 글로벌 상태 + 외부 네트워크에 의존.
 *  - 그래서 여기서는 라이브러리가 책임지는 *경계* 만 검증:
 *    · placeholder 표식이 실 구현으로 교체되었는지
 *    · FirebaseAnalyticsRecorder 가 AnalyticsBus 에 등록되었을 때 이벤트가 크래시 없이 처리되는지
 *  - 진짜 이벤트가 Firebase 로 도착하는지는 SolTi 통합 검증 (Firebase Console DebugView) 으로.
 *
 * iOS 자매 (`IndieKitAnalyticsTests.swift`) 와 같은 시나리오.
 */

package kr.co.junu.indiekit.analytics

import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.analyticsParams
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IndieKitAnalyticsTests {

    @Before
    fun setUp() {
        // 이전 테스트 영향 차단
        AnalyticsBus.unregister()
    }

    @After
    fun tearDown() {
        AnalyticsBus.unregister()
    }

    /** 1단계 진입 표식 — placeholder 가 실 구현으로 교체되었음. */
    @Test
    fun `is no longer placeholder`() {
        assertFalse(
            "1단계에서는 placeholder 표식이 false 여야 한다",
            IndieKitAnalytics.isPlaceholder
        )
    }

    /**
     * FirebaseAnalyticsRecorder 직접 등록 시 AnalyticsBus 가 인지하는지.
     * IndieKitAnalytics.configure 는 Context (Application) 가 필요해 단위 테스트로 직접 못 부르지만,
     * 등록 자체는 같은 결과 — 어댑터가 슬롯에 들어 있는 상태.
     */
    @Test
    fun `recorder registers on analytics bus`() {
        assertFalse(AnalyticsBus.isRegistered)

        AnalyticsBus.register(FirebaseAnalyticsRecorder)
        assertTrue("Recorder 등록 후엔 AnalyticsBus.isRegistered 가 true 여야 한다", AnalyticsBus.isRegistered)
    }

    /**
     * 다른 모듈 (광고 등) 이 AnalyticsBus 로 흘려보낸 이벤트가 크래시 없이 처리되는지.
     *
     * IndieKitAnalytics 가 configure 안 된 상태라 내부 ensureConfigured 가 null 을 반환,
     * 실제 Firebase 호출은 일어나지 않는다 — Logcat warning 만 남고 끝.
     */
    @Test
    fun `event via analytics bus does not crash`() {
        AnalyticsBus.register(FirebaseAnalyticsRecorder)

        // 광고 모듈이 흘려보낼 만한 이벤트 모양으로
        AnalyticsBus.record(
            event = "ad_impression",
            parameters = analyticsParams(
                "format" to "banner",
                "ad_unit" to "ca-app-pub-test",
                "loaded_ms" to 320,
                "is_pro" to false
            )
        )
        // 단순히 크래시 없이 도착하면 통과
    }

    /**
     * 같은 어댑터를 두 번 등록해도 AnalyticsBus 는 안전.
     * IndieKitAnalytics.configure 멱등성에 대응되는 시나리오.
     */
    @Test
    fun `duplicate register is safe`() {
        AnalyticsBus.register(FirebaseAnalyticsRecorder)
        AnalyticsBus.register(FirebaseAnalyticsRecorder)
        assertTrue(AnalyticsBus.isRegistered)
    }
}
