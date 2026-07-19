/*
 * PushDeviceStore.kt — IndieKitPush
 *
 * 역할
 *  - 푸시 모듈이 기기에 남겨 두는 작은 기억들 — 기기 이름 (device_id), 알림 주소,
 *    연결된 사용자, 마지막으로 서버에 올린 내용의 지문, 그리고 예약 알림 목록.
 *  - SharedPreferences 에 보관. 앱을 지우면 같이 사라진다 (새 설치 = 새 기기 이름 — 의도된 동작).
 *
 * 주요 개념
 *  - 예약 알림 목록을 저장하는 이유: AlarmManager 예약은 기기 재부팅 / 앱 강제 종료에 사라진다.
 *    여기 저장된 목록이 기준이 되는 원본이고, BootReceiver / configure 가 이걸 보고 알람을 다시 건다.
 *  - 예약 알림 한 줄의 저장 형식은 필드를 구분자 (US, unit separator, ) 로 이은 문자열.
 *    JSON 라이브러리를 안 쓰는 이유는 PushRegistration.kt 상단 주석과 같다.
 *
 * 주의사항
 *  - 알림 주소 (토큰) 는 비밀값이 아니다 (그 주소로 보내려면 서버 인증 키가 따로 필요).
 *    그래서 암호화 저장까지는 하지 않는다.
 */

package kr.co.junu.indiekit.push

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** 저장되는 예약 알림 한 건 — 알람을 다시 걸 때 필요한 전부. */
internal data class StoredLocalNotification(
    val id: String,
    val title: String,
    val body: String,
    val route: String?,
    val trigger: PushTrigger,
    /** 분 단위 정확도가 필요한 앱만 true (PUSH_PLAN.md 10번 항목 2번). */
    val exact: Boolean
) {

    /** 한 줄 저장 형식으로. 필드 사이는 US (unit separator) — 사용자 문구에 나올 일 없는 제어 문자. */
    fun serialize(): String {
        val triggerPart = when (trigger) {
            is PushTrigger.Daily -> "daily:${trigger.hour}:${trigger.minute}"
            is PushTrigger.Once -> "once:${trigger.epochMillis}"
            is PushTrigger.Weekly -> "weekly:${trigger.dayOfWeek}:${trigger.hour}:${trigger.minute}"
        }
        return listOf(id, title, body, route ?: "", triggerPart, exact.toString()).joinToString(SEP)
    }

    companion object {
        private const val SEP = "\u001F"

        /** 저장된 한 줄을 되읽는다. 형식이 깨져 있으면 null (그 알림은 버림). */
        fun deserialize(line: String): StoredLocalNotification? {
            val parts = line.split(SEP)
            if (parts.size != 6) return null
            val triggerParts = parts[4].split(":")
            val trigger = when (triggerParts.getOrNull(0)) {
                "daily" -> {
                    val h = triggerParts.getOrNull(1)?.toIntOrNull() ?: return null
                    val m = triggerParts.getOrNull(2)?.toIntOrNull() ?: return null
                    PushTrigger.Daily(h, m)
                }
                "once" -> {
                    val at = triggerParts.getOrNull(1)?.toLongOrNull() ?: return null
                    PushTrigger.Once(at)
                }
                "weekly" -> {
                    val d = triggerParts.getOrNull(1)?.toIntOrNull() ?: return null
                    val h = triggerParts.getOrNull(2)?.toIntOrNull() ?: return null
                    val m = triggerParts.getOrNull(3)?.toIntOrNull() ?: return null
                    PushTrigger.Weekly(d, h, m)
                }
                else -> return null
            }
            return StoredLocalNotification(
                id = parts[0],
                title = parts[1],
                body = parts[2],
                route = parts[3].ifEmpty { null },
                trigger = trigger,
                exact = parts[5].toBoolean()
            )
        }
    }
}

/** 기기에 남기는 푸시 관련 기억. SharedPreferences 위 얇은 덮개. */
internal class PushDeviceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kr.co.junu.indiekit.push", Context.MODE_PRIVATE)

    /** 설치마다 하나 만드는 고정 기기 이름. 없으면 새로 만들어 저장 후 돌려준다. */
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val fresh = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
            return fresh
        }

    /** FCM 이 준 알림 주소. 아직 못 받았으면 null. */
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** linkUser 로 연결된 사용자 이름. 익명이면 null. */
    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    /** 마지막으로 서버에 올리기 성공한 본문의 지문 (JSON 문자열). */
    var lastUploadFingerprint: String?
        get() = prefs.getString(KEY_FINGERPRINT, null)
        set(value) = prefs.edit().putString(KEY_FINGERPRINT, value).apply()

    /** 권한 팝업을 한 번이라도 띄웠는지 — permissionStatus 의 "아직 안 물음 / 거부" 구분용. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    /** 알림 채널 이름 — 앱 프로세스 없이 리시버만 깨어났을 때도 채널을 만들 수 있게 보관. */
    var channelName: String?
        get() = prefs.getString(KEY_CHANNEL_NAME, null)
        set(value) = prefs.edit().putString(KEY_CHANNEL_NAME, value).apply()

    /** 알림 작은 아이콘 리소스 번호 — 위와 같은 이유로 보관. 0 이면 미지정. */
    var smallIconRes: Int
        get() = prefs.getInt(KEY_SMALL_ICON, 0)
        set(value) = prefs.edit().putInt(KEY_SMALL_ICON, value).apply()

    // MARK: 예약 알림 목록

    /** 저장된 예약 알림 전부. */
    fun scheduledNotifications(): List<StoredLocalNotification> {
        val raw = prefs.getStringSet(KEY_SCHEDULED, emptySet()) ?: emptySet()
        return raw.mapNotNull { StoredLocalNotification.deserialize(it) }
    }

    /** 예약 알림 한 건 저장 (같은 id 가 있으면 갈아끼움). */
    fun saveScheduled(notification: StoredLocalNotification) {
        val remaining = scheduledNotifications().filter { it.id != notification.id }
        persistScheduled(remaining + notification)
    }

    /** 예약 알림 한 건 제거. */
    fun removeScheduled(id: String) {
        persistScheduled(scheduledNotifications().filter { it.id != id })
    }

    /** 예약 알림 전부 제거. */
    fun clearScheduled() {
        prefs.edit().remove(KEY_SCHEDULED).apply()
    }

    private fun persistScheduled(list: List<StoredLocalNotification>) {
        prefs.edit().putStringSet(KEY_SCHEDULED, list.map { it.serialize() }.toSet()).apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "deviceId"
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "userId"
        const val KEY_FINGERPRINT = "lastUploadFingerprint"
        const val KEY_PERMISSION_ASKED = "permissionAsked"
        const val KEY_CHANNEL_NAME = "channelName"
        const val KEY_SMALL_ICON = "smallIconRes"
        const val KEY_SCHEDULED = "scheduledNotifications"
    }
}
