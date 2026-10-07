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

    /** AdUnitID 의 빈 인스턴스 — release 가 null. */
    @Test
    fun `default AdUnitID has null release`() {
        val empty = AdUnitID()
        assertEquals(null, empty.release)
    }

    /** AdUnitID data class 동등성. */
    @Test
    fun `AdUnitID equality holds for same values`() {
        val a = AdUnitID(release = "ca-app-pub-real")
        val b = AdUnitID(release = "ca-app-pub-real")
        val c = AdUnitID(release = null)

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

    /**
     * 자리 (placement) 선택 규칙 — null 은 기본 자리(DEFAULT_PLACEMENT) ID, 등록된 자리는 그 자리 ID,
     * 모르는 자리는 기본 자리 ID 로 대체. 기본 자리도 없으면 빈 AdUnitID.
     */
    @Test
    fun `selectAdUnitID picks by placement`() {
        val defaultID = AdUnitID(release = "ca-app-pub-default")
        val homeID = AdUnitID(release = "ca-app-pub-home")
        val placements = mapOf(IndieKitAds.DEFAULT_PLACEMENT to defaultID, "home" to homeID)

        assertEquals(
            defaultID,
            IndieKitAds.selectAdUnitID(placements, placement = null, format = "배너")
        )
        assertEquals(
            homeID,
            IndieKitAds.selectAdUnitID(placements, placement = "home", format = "배너")
        )
        assertEquals(
            defaultID,
            IndieKitAds.selectAdUnitID(placements, placement = "unknown", format = "배너")
        )
        // 기본 자리조차 등록 안 됨 → 빈 AdUnitID.
        assertEquals(
            AdUnitID(),
            IndieKitAds.selectAdUnitID(mapOf("home" to homeID), placement = null, format = "배너")
        )
    }

    // ── 전면 / 리워드 요청 규칙 (2026-10 광고 일치율 이슈) ─────────────────────

    /** 앱 시작 때 전면 / 리워드를 미리 받지 않는다 — 안 보일 광고 요청이 일치율 · 노출률을 깎았다. */
    @Test
    fun `full screen ads are not preloaded on configure`() {
        assertFalse(FullScreenAdPolicy.PRELOADS_ON_CONFIGURE)
        assertFalse(FullScreenAdPolicy.RELOADS_AFTER_DISMISS)
    }

    /** 처음에는 쓸 광고가 없어 요청해야 하고, 요청 중에는 겹쳐 보내지 않는다. */
    @Test
    fun `full screen ad state does not duplicate requests`() {
        val now = 1_000_000L
        var state = FullScreenAdState()
        assertFalse(state.isReady(now))
        assertTrue(state.shouldRequest(now))

        state = state.requested()
        assertFalse("요청 중에 또 요청하면 안 보일 요청이 하나 더 생긴다", state.shouldRequest(now))

        state = state.loaded(now)
        assertTrue(state.isReady(now))
        assertFalse("받아 둔 광고가 있으면 요청하지 않는다", state.shouldRequest(now))
    }

    /** 받은 지 55분이 넘은 광고는 띄우지 않고 (노출이 안 잡힘) 새로 요청한다. */
    @Test
    fun `full screen ad state expires old ads`() {
        val loadedAt = 1_000_000L
        val state = FullScreenAdState().requested().loaded(loadedAt)

        val fresh = loadedAt + FullScreenAdState.MAX_AGE_MILLIS - 1
        assertTrue(state.isReady(fresh))
        assertFalse(state.isExpired(fresh))

        val old = loadedAt + FullScreenAdState.MAX_AGE_MILLIS + 1
        assertFalse(state.isReady(old))
        assertTrue(state.isExpired(old))
        assertTrue(state.shouldRequest(old))
        assertTrue("구글의 1시간 만료보다 짧아야 한다", FullScreenAdState.MAX_AGE_MILLIS < 60L * 60L * 1000L)
    }

    /** 띄우는 중에는 같은 광고를 다시 띄우거나 새로 요청하지 않고, 닫힌 뒤엔 다시 요청할 수 있다. */
    @Test
    fun `full screen ad state presenting and dismiss`() {
        val now = 1_000_000L
        var state = FullScreenAdState().requested().loaded(now)

        state = state.presenting()
        assertFalse(state.isReady(now))
        assertFalse(state.shouldRequest(now))

        state = state.dismissed()
        assertTrue(state.shouldRequest(now))
    }

    /** 요청이 실패하면 다음 차례에 다시 요청할 수 있다. */
    @Test
    fun `full screen ad state failure allows retry`() {
        val now = 1_000_000L
        val state = FullScreenAdState().requested().failed()
        assertFalse(state.isReady(now))
        assertTrue(state.shouldRequest(now))
    }

    /** configure 전에 미리 받기를 불러도 터지지 않고 아무 요청도 하지 않는다. */
    @Test
    fun `prepare before configure is ignored`() {
        IndieKitAds.prepareInterstitial()
        IndieKitAds.prepareRewarded(placement = "home")
        assertFalse(IndieKitAds.isInterstitialReady)
        assertFalse(IndieKitAds.isRewardedReady(placement = "home"))
    }

    /** 자리 이름으로 묻는 적재 상태도 configure 전엔 false. */
    @Test
    fun `before configure ready flags by placement are false`() {
        assertFalse(IndieKitAds.isInterstitialReady(placement = "home"))
        assertFalse(IndieKitAds.isRewardedReady(placement = "home"))
    }

    // ── 유럽 동의를 받은 뒤에 광고 SDK 시작 (2026-10) ─────────────────────────

    /** 동의 절차를 켠 앱은 UMP 가 허락하기 전에는 SDK 를 시작하지 않는다 (예전엔 나란히 시작했다). */
    @Test
    fun `ads start gate waits for consent`() {
        val gate = AdsStartGate()
        assertFalse(
            "동의 전에 시작하면 동의 전 광고 요청이 나간다",
            gate.tryStart(requestConsent = true, canRequestAds = false)
        )
        assertFalse(gate.isStarted)

        assertTrue(gate.tryStart(requestConsent = true, canRequestAds = true))
        assertTrue(gate.isStarted)
    }

    /** 시작은 한 번만 — "거는 순간 (지난 동의)" 과 "절차 끝" 두 번 허락이 와도 SDK 시작은 한 번. */
    @Test
    fun `ads start gate starts only once`() {
        val gate = AdsStartGate()
        assertTrue(gate.tryStart(requestConsent = true, canRequestAds = true))
        assertFalse(gate.tryStart(requestConsent = true, canRequestAds = true))
        assertFalse(gate.tryStart(requestConsent = false, canRequestAds = true))
    }

    /** 동의 절차를 끈 앱은 기다리지 않고 곧바로 시작한다 (예전과 같음). */
    @Test
    fun `ads start gate without consent starts immediately`() {
        assertTrue(AdsStartGate().tryStart(requestConsent = false, canRequestAds = false))
    }

    /** configure 전에는 화면이 광고를 요청하지 않는다 (SDK 준비 전 = 빈 자리). */
    @Test
    fun `ads are not ready for compose before configure`() {
        assertFalse(IndieKitAds.isAdsReadyForCompose)
        assertFalse(IndieKitAds.isSdkReady)
    }

    // ── 배너 둘레 검은 띠 (2026-10) ──────────────────────────────────────────

    /** 광고가 자리보다 작으면 광고 크기 그대로 가운데에 — 남는 곳은 그릇 (투명) 이라 앱 바탕색이 보인다. */
    @Test
    fun `banner smaller than slot is centered`() {
        assertEquals(
            "예전엔 AdView 가 자리 전체 (1080) 를 차지해 양옆이 검게 채워졌다",
            BannerFrame(left = 120, top = 0, width = 840, height = 131),
            BannerLayout.adFrame(adW = 840, adH = 131, containerW = 1080, containerH = 131)
        )
        assertEquals(
            "위아래도 가운데",
            BannerFrame(left = 0, top = 23, width = 1080, height = 85),
            BannerLayout.adFrame(adW = 1080, adH = 85, containerW = 1080, containerH = 131)
        )
    }

    /** 광고가 자리보다 크면 자리 안으로 줄여 잡는다 (468×60 시험 광고 등 — 옆 화면을 가리지 않게). */
    @Test
    fun `banner larger than slot is clamped`() {
        assertEquals(
            BannerFrame(0, 0, 1080, 131),
            BannerLayout.adFrame(adW = 1229, adH = 158, containerW = 1080, containerH = 131)
        )
    }

    /** 광고 크기를 아직 모르면 자리 전체. */
    @Test
    fun `banner unknown size fills slot`() {
        assertEquals(BannerFrame(0, 0, 1080, 131), BannerLayout.adFrame(0, 0, 1080, 131))
    }

    /**
     * 시험용 나라 설정 그릇 — 기본값 (기기 번호 목록 비움) / 동등성 / 나라 두 갈래.
     * 실제 동의창이 뜨는지는 단위 테스트로 못 본다 (UMP 는 네트워크 + Activity 의존)
     * — 데모 앱에서 consentTestSettings = EEA 로 통합 검증.
     */
    @Test
    fun `ConsentTestSettings holds geography and device ids`() {
        val eea = ConsentTestSettings(geography = ConsentTestGeography.EEA)
        assertEquals(ConsentTestGeography.EEA, eea.geography)
        assertTrue("기기 번호 목록 기본값은 비어 있어야 함", eea.testDeviceHashedIds.isEmpty())

        val same = ConsentTestSettings(geography = ConsentTestGeography.EEA)
        assertEquals(eea, same)

        val notEea = ConsentTestSettings(
            geography = ConsentTestGeography.NOT_EEA,
            testDeviceHashedIds = listOf("ABCD1234")
        )
        assertNotEquals(eea, notEea)
        assertEquals(listOf("ABCD1234"), notEea.testDeviceHashedIds)
    }
}
