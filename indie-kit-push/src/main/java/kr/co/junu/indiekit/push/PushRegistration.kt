/*
 * PushRegistration.kt — IndieKitPush
 *
 * 역할
 *  - 알림 주소 (FCM 등록 토큰) 를 우리 서버에 올릴 때 쓰는 설정 + 요청 본문 형식.
 *  - 두 플랫폼이 같은 끝점 / 같은 형식을 쓴다 — PUSH_PLAN.md 6번 항목의 약속이 여기 코드로 있다.
 *  - iOS 자매 (PushRegistration / DeviceRegistrationBody) 와 1:1 대칭.
 *
 * 주요 개념
 *  - PushRegistration: 끝점 URL + 앱 이름 + (필요 시) 추가 헤더. configure 때 한 번 넘긴다.
 *  - DeviceRegistrationBody: 서버로 보내는 JSON 의 정확한 모양. snake_case 열쇠.
 *
 * 주의사항
 *  - JSON 은 라이브러리 없이 직접 만든다 (toJson) — 이유:
 *    1. org.json 은 단위 테스트 (JVM) 에서 안드로이드 스텁이라 못 쓴다.
 *    2. 열쇠 순서를 고정해야 "바뀌었을 때만 올림" 지문 비교가 안정적이다.
 */

package kr.co.junu.indiekit.push

/** 기기 등록 설정 — 우리 서버의 어디로, 어느 앱 이름으로 올릴지. */
public data class PushRegistration(
    /** 우리 서버의 기기 등록 끝점. 예: https://api.junulabs.com/api/push/devices/ */
    val url: String,
    /** 서버가 앱을 구분하는 이름. 예: "solti", "tapcounter". */
    val appId: String,
    /** 추가 헤더 (예: 내부 API 열쇠). 기본 없음. */
    val headers: Map<String, String> = emptyMap()
)

/** 서버로 보내는 기기 등록 요청 본문. 두 플랫폼 공통 형식 (PUSH_PLAN.md 6번 항목). */
internal data class DeviceRegistrationBody(
    /** 어느 앱인가 — PushRegistration.appId. */
    val app: String,
    /** 항상 "android" (iOS 자매는 "ios"). */
    val platform: String,
    /** 알림 주소 — FCM 등록 토큰. */
    val token: String,
    /** 설치마다 하나 만드는 고정 기기 이름 (모듈이 생성 / 보관). */
    val deviceId: String,
    /** 로그인 전엔 null, linkUser 후엔 사용자 이름. */
    val userId: String?,
    /** 기기 언어 / 지역 (예: "ko_KR"). */
    val locale: String,
    /** 앱 버전 (예: "1.2.0"). */
    val appVersion: String,
    /** OS 버전 — Android 는 API 레벨 문자열 (예: "34"). */
    val osVersion: String
) {

    /**
     * 서버 약속 형식의 JSON 문자열.
     * 열쇠는 항상 같은 순서 → 이 문자열이 그대로 "지문" 이 되어 지난번 올린 것과 비교된다.
     * userId 가 null 이면 열쇠 자체를 뺀다 (iOS 자매의 Codable 기본 동작과 같음).
     */
    fun toJson(): String = buildString {
        append('{')
        append("\"app\":").append(quote(app))
        append(",\"platform\":").append(quote(platform))
        append(",\"token\":").append(quote(token))
        append(",\"device_id\":").append(quote(deviceId))
        if (userId != null) {
            append(",\"user_id\":").append(quote(userId))
        }
        append(",\"locale\":").append(quote(locale))
        append(",\"app_version\":").append(quote(appVersion))
        append(",\"os_version\":").append(quote(osVersion))
        append('}')
    }

    /** JSON 문자열 값 이스케이프 — 따옴표 / 역빗금 / 제어 문자만 처리 (담기는 값이 전부 단순 문자열이라 충분). */
    private fun quote(value: String): String = buildString {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
