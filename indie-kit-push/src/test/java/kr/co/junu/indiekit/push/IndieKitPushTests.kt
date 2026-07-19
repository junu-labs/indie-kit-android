/*
 * IndieKitPushTests.kt — indie-kit-push
 *
 * 역할
 *  - 푸시 모듈의 순수 로직 검증 — 안드로이드 시스템 (알림 / 알람 / FCM) 을 안 건드리는 부분만.
 *    (권한 팝업 / 실제 울림 / 수신은 데모 앱 검증에서 확인 — PUSH_PLAN.md 9번 항목 2번 순서.)
 *
 * 검증 대상
 *  1. 기기 등록 본문 (DeviceRegistrationBody) 의 JSON 형식 — 서버 약속 (snake_case 열쇠)
 *  2. 예약 조건 (PushTrigger) 의 다음 울릴 시각 계산
 *  3. 예약 알림 저장 형식 (serialize / deserialize) 왕복
 */

package kr.co.junu.indiekit.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IndieKitPushTests {

    // MARK: 기기 등록 본문 (서버 약속)

    /** JSON 열쇠가 서버 약속 (snake_case) 그대로인지 — PUSH_PLAN.md 6번 항목. */
    @Test
    fun registrationBodyEncodesToAgreedJsonKeys() {
        val body = DeviceRegistrationBody(
            app = "solti",
            platform = "android",
            token = "dQw4",
            deviceId = "TEST-DEVICE",
            userId = "u_123",
            locale = "ko_KR",
            appVersion = "1.2.0",
            osVersion = "34"
        )
        val json = body.toJson()

        assertTrue(json.contains("\"device_id\":\"TEST-DEVICE\""))
        assertTrue(json.contains("\"user_id\":\"u_123\""))
        assertTrue(json.contains("\"app_version\":\"1.2.0\""))
        assertTrue(json.contains("\"os_version\":\"34\""))
        assertTrue(json.contains("\"platform\":\"android\""))
        assertTrue(json.contains("\"app\":\"solti\""))
    }

    /** 익명 기기 (user_id 없음) 는 열쇠 자체가 빠져야 한다 — iOS 자매의 Codable 동작과 동일. */
    @Test
    fun registrationBodyWithoutUserOmitsKey() {
        val body = DeviceRegistrationBody(
            app = "tapcounter", platform = "android", token = "ff", deviceId = "D",
            userId = null, locale = "en_US", appVersion = "0", osVersion = "36"
        )
        assertFalse(body.toJson().contains("user_id"))
    }

    /** 같은 내용이면 지문 (JSON 문자열) 도 같아야 한다 — "바뀌었을 때만 올림" 규칙의 바탕. */
    @Test
    fun fingerprintIsStable() {
        val make = {
            DeviceRegistrationBody(
                app = "solti", platform = "android", token = "aa", deviceId = "D",
                userId = null, locale = "ko_KR", appVersion = "1.0", osVersion = "34"
            )
        }
        assertEquals(make().toJson(), make().toJson())
    }

    /** 따옴표 / 역빗금이 든 값도 JSON 이 깨지지 않아야 한다. */
    @Test
    fun jsonEscapesSpecialCharacters() {
        val body = DeviceRegistrationBody(
            app = "a\"b\\c", platform = "android", token = "t", deviceId = "D",
            userId = null, locale = "ko_KR", appVersion = "1", osVersion = "34"
        )
        assertTrue(body.toJson().contains("\"a\\\"b\\\\c\""))
    }

    // MARK: 예약 조건 — 다음 울릴 시각

    /** 기준 시각 도우미 — 2026년 7월 20일 (월요일) 오전 10시. */
    private fun monday10am(): Long {
        return Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 20, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 매일 저녁 8시 — 오전 10시 기준이면 오늘 저녁이어야 한다. */
    @Test
    fun dailyTriggerFiresSameDayIfNotPassed() {
        val now = monday10am()
        val next = PushTrigger.Daily(hour = 20, minute = 0).nextFireTime(now)!!
        assertEquals(10 * 60 * 60 * 1000L, next - now)   // 10시간 뒤
    }

    /** 매일 아침 9시 — 오전 10시 기준이면 이미 지났으니 내일 아침이어야 한다. */
    @Test
    fun dailyTriggerRollsToNextDayIfPassed() {
        val now = monday10am()
        val next = PushTrigger.Daily(hour = 9, minute = 0).nextFireTime(now)!!
        assertEquals(23 * 60 * 60 * 1000L, next - now)   // 23시간 뒤
    }

    /** 매주 월요일 아침 9시 — 월요일 오전 10시 기준이면 다음 주 월요일이어야 한다. */
    @Test
    fun weeklyTriggerRollsToNextWeekIfPassed() {
        val now = monday10am()
        val next = PushTrigger.Weekly(dayOfWeek = Calendar.MONDAY, hour = 9, minute = 0).nextFireTime(now)!!
        assertEquals(7L * 24 * 60 * 60 * 1000 - 60 * 60 * 1000, next - now)   // 6일 23시간 뒤
    }

    /** 한 번짜리 — 미래면 그 시각 그대로, 과거면 null (울릴 일 없음). */
    @Test
    fun onceTriggerReturnsFutureTimeOrNull() {
        val now = monday10am()
        assertEquals(now + 1000, PushTrigger.Once(now + 1000).nextFireTime(now))
        assertNull(PushTrigger.Once(now - 1000).nextFireTime(now))
    }

    // MARK: 예약 알림 저장 형식

    /** 저장 → 되읽기 왕복이 내용을 그대로 보존해야 한다. */
    @Test
    fun storedNotificationSerializationRoundTrip() {
        val original = StoredLocalNotification(
            id = "workout-reminder",
            title = "운동할 시간이에요",
            body = "오늘의 타이머가 기다리고 있어요",
            route = "timer/start",
            trigger = PushTrigger.Daily(hour = 20, minute = 30),
            exact = false
        )
        assertEquals(original, StoredLocalNotification.deserialize(original.serialize()))
    }

    /** route 없는 알림 / 매주 / 한 번짜리도 왕복이 되어야 한다. */
    @Test
    fun storedNotificationRoundTripForAllTriggerKinds() {
        val weekly = StoredLocalNotification(
            id = "w", title = "제목", body = "본문", route = null,
            trigger = PushTrigger.Weekly(dayOfWeek = 2, hour = 9, minute = 0), exact = true
        )
        val once = StoredLocalNotification(
            id = "o", title = "한 번", body = "만", route = null,
            trigger = PushTrigger.Once(epochMillis = 1_800_000_000_000), exact = false
        )
        assertEquals(weekly, StoredLocalNotification.deserialize(weekly.serialize()))
        assertEquals(once, StoredLocalNotification.deserialize(once.serialize()))
    }

    /** 깨진 저장 줄은 null (그 알림만 버림 — 전체가 죽지 않음). */
    @Test
    fun brokenStoredLineReturnsNull() {
        assertNull(StoredLocalNotification.deserialize("깨진 줄"))
        assertNull(StoredLocalNotification.deserialize(""))
    }
}
