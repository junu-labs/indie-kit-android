/*
 * IndieKitAdsTests.kt
 *
 * 2단계 — IndieKitAds 라이브러리 경계 검증.
 *
 * 주의
 *  - AdMob / UMP 자체 호출 (MobileAds.initialize, AdView.loadAd 등) 은 단위 테스트로 검증 까다로움.
 *    AdMob SDK 는 ContentProvider 기반 자동 초기화 + 외부 네트워크 호출 + Activity 의존.
 *  - 그래서 여기서는 라이브러리가 책임지는 *경계* 만 검증:
 *    · placeholder 표식이 실 구현으로 교체되었는지
 *    · 테스트 광고 ID 상수가 모두 채워져 있는지 (Google 공식 형식)
 *    · AdReward data class 의 동등성 / 필드
 *    · configure 전 / 후 isConfigured 표식
 *  - 광고 표시 흐름 / 빌드 환경 분기 / 광고 적재 콜백은 데모 앱 통합 검증으로.
 *
 * iOS 자매 (`IndieKitAdsTests`) 와 같은 시나리오.
 */

package kr.co.junu.indiekit.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndieKitAdsTests {

    /** 2단계 진입 표식 — placeholder 가 실 구현으로 교체되었음. */
    @Test
    fun `is no longer placeholder`() {
        assertFalse(
            "2단계에서는 placeholder 표식이 false 여야 한다",
            IndieKitAds.isPlaceholder
        )
    }

    /** Google 공식 테스트 광고 ID 4종이 모두 채워져 있고 형식이 맞는지. */
    @Test
    fun `test ad unit IDs are filled with Google format`() {
        val ids = listOf(
            "banner" to AdMobTestUnitID.BANNER,
            "interstitial" to AdMobTestUnitID.INTERSTITIAL,
            "rewarded" to AdMobTestUnitID.REWARDED,
            "native" to AdMobTestUnitID.NATIVE
        )
        for ((label, id) in ids) {
            assertNotNull("$label 테스트 ID 가 null", id)
            assertTrue(
                "$label 테스트 ID 가 Google 형식 (ca-app-pub-...) 이 아님: $id",
                id.startsWith("ca-app-pub-")
            )
        }

        // 4개 모두 서로 달라야 함 — 같은 ID 면 카피-페이스트 실수.
        val unique = ids.map { it.second }.toSet()
        assertEquals("테스트 ID 4종이 모두 달라야 함", 4, unique.size)
    }

    /** AdUnitID 의 빈 인스턴스 — 두 자리 모두 null. */
    @Test
    fun `default AdUnitID has null debug and release`() {
        val empty = AdUnitID()
        assertEquals(null, empty.debug)
        assertEquals(null, empty.release)
    }

    /** AdUnitID data class 동등성. */
    @Test
    fun `AdUnitID equality holds for same values`() {
        val a = AdUnitID(debug = "ca-app-pub-test", release = "ca-app-pub-real")
        val b = AdUnitID(debug = "ca-app-pub-test", release = "ca-app-pub-real")
        val c = AdUnitID(debug = "ca-app-pub-test", release = null)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    /** AdReward data class 동등성 / 필드. */
    @Test
    fun `AdReward holds amount and type`() {
        val reward = AdReward(amount = 5, type = "coins")
        assertEquals(5, reward.amount)
        assertEquals("coins", reward.type)

        val same = AdReward(amount = 5, type = "coins")
        assertEquals(reward, same)

        val different = AdReward(amount = 10, type = "coins")
        assertNotEquals(reward, different)
    }

    /**
     * configure 전 단계에선 isInterstitialReady / isRewardedReady 모두 false.
     * configure 후의 검증은 통합 검증 (데모 앱) 에서.
     */
    @Test
    fun `before configure ready flags are false`() {
        // 이 테스트는 다른 테스트가 먼저 configure 를 부르지 않는 한 의미가 있다.
        // 단위 테스트엔 Application context 가 없어 configure 자체를 못 부른다 — 자연스럽게 빈 상태 검증.
        assertFalse(IndieKitAds.isInterstitialReady)
        assertFalse(IndieKitAds.isRewardedReady)
    }
}
