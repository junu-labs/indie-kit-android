/*
 * IndieKitBillingTests.kt — IndieKitBilling 의 Play Billing SDK 의존 없는 부분 검증.
 *
 * 주의
 *  - Play Billing 자체 동작 (구매 / 갱신 / 복원) 은 실 폰 + Play Console 의 라이선스 테스터 통합 테스트에서.
 *  - 여기서는 ProductDescriptor / IndieKitBillingError / 초기 상태 / isoPeriodToMs 등 라이브러리 표면만 검증.
 *
 * iOS 자매 (IndieKitBillingTests.swift) 의 5개 테스트 + Android 고유 헬퍼 (isoPeriodToMs) 1개 추가 — 총 6개.
 */

package kr.co.junu.indiekit.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndieKitBillingTests {

    // ─────────────────────────────────────────────────────────────────
    // 1. 단계 4 진입 표식
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `isPlaceholder 가 false 다`() {
        assertFalse(IndieKitBilling.isPlaceholder)
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. ProductDescriptor 등호
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `ProductDescriptor 등호 비교`() {
        val a = ProductDescriptor("x.y.z", BillingProductType.AutoRenewableSubscription)
        val b = ProductDescriptor("x.y.z", BillingProductType.AutoRenewableSubscription)
        val c = ProductDescriptor("x.y.z", BillingProductType.NonConsumable)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. IndieKitBillingError 메시지가 한국어로
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `IndieKitBillingError 메시지가 한국어로 채워진다`() {
        val notConfigured = IndieKitBillingError.NotConfigured
        assertNotNull(notConfigured.message)
        assertTrue(
            "configure 단어가 메시지에 있어야 함 (실제: ${notConfigured.message})",
            notConfigured.message?.contains("configure") ?: false
        )

        val notFound = IndieKitBillingError.ProductNotFound("x.y.z")
        assertTrue(
            "productID 가 메시지에 들어가야 함 (실제: ${notFound.message})",
            notFound.message?.contains("x.y.z") ?: false
        )

        val verifyFail = IndieKitBillingError.VerificationFailed
        assertNotNull(verifyFail.message)

        val alreadyInProgress = IndieKitBillingError.AlreadyInProgress
        assertNotNull(alreadyInProgress.message)
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. PurchaseResult 세 케이스 — 컴파일 게이트
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `PurchaseResult 세 케이스가 모두 존재한다`() {
        val cases: List<PurchaseResult> = listOf(
            PurchaseResult.Success,
            PurchaseResult.UserCancelled,
            PurchaseResult.Pending
        )
        assertEquals(3, cases.size)
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. 싱글턴 (object) 접근 + 초기 상태가 안전한 기본값
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `configure 전이라도 owns hasLifetime 등 헬퍼가 안전한 기본값을 돌려준다`() {
        // IndieKitBilling 은 object — 다른 테스트에서 상태가 오염됐을 수 있어
        // entitlements / hasLifetime 의 "기본값 안전성" 만 검증 (절대값 단언 X).
        assertFalse(IndieKitBilling.owns("kr.co.junu.nonexistent.product"))

        // entitlements StateFlow 가 null 이 아닌 Set 을 항상 노출.
        assertNotNull(IndieKitBilling.entitlements.value)

        // products StateFlow 가 null 이 아닌 List 를 항상 노출.
        assertNotNull(IndieKitBilling.products.value)
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. isoPeriodToMs — Android 고유 헬퍼 (구독 만료 근사)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `isoPeriodToMs 가 P1M P1Y P1W P7D 를 ms 로 변환한다`() {
        val dayMs = 24L * 60L * 60L * 1000L

        assertEquals(7L * dayMs, IndieKitBilling.isoPeriodToMs("P7D"))
        assertEquals(2L * 7L * dayMs, IndieKitBilling.isoPeriodToMs("P2W"))
        assertEquals(30L * dayMs, IndieKitBilling.isoPeriodToMs("P1M"))
        assertEquals(365L * dayMs, IndieKitBilling.isoPeriodToMs("P1Y"))

        // 형식 깨진 입력은 0 반환.
        assertEquals(0L, IndieKitBilling.isoPeriodToMs("XYZ"))
        assertEquals(0L, IndieKitBilling.isoPeriodToMs(""))
    }
}
