/*
 * PushPayload.kt — IndieKitPush
 *
 * 역할
 *  - 알림을 눌렀을 때 앱에 넘겨주는 내용물 한 덩어리.
 *  - 서버 푸시든 기기 안 예약 알림이든 같은 모양으로 만들어져 onTap 으로 들어간다
 *    → 앱 입장에선 어느 쪽에서 온 알림인지 구분할 필요가 없다.
 *  - iOS 자매 (PushPayload) 와 필드 1:1 대칭.
 *
 * 주요 개념
 *  - route: 데이터 안에 예약된 열쇠 하나. 앱이 화면 이동을 정하는 데 쓴다 (예: "settings/whatsnew").
 *  - extras: route 를 뺀 나머지 문자열 데이터 (예: campaign 이름).
 *  - isLocal: 기기 안 예약 알림이면 true, 서버 푸시면 false.
 *
 * 주의사항
 *  - 시스템 알림판이 대신 띄운 알림 (앱이 뒤에 있을 때의 서버 푸시) 을 누른 경우,
 *    Android 는 제목 / 본문을 intent 로 돌려주지 않는다 → title / body 가 빈 문자열일 수 있다.
 *    화면 이동은 route 로 하므로 실사용엔 지장 없다.
 */

package kr.co.junu.indiekit.push

/** 알림 한 건의 내용물. 서버 푸시 / 예약 알림 공통 모양. */
public data class PushPayload(
    /** 알림 제목. 시스템 알림판 경유 누름이면 빈 문자열일 수 있다 (위 주의사항). */
    val title: String,
    /** 알림 본문. 시스템 알림판 경유 누름이면 빈 문자열일 수 있다. */
    val body: String,
    /** 화면 이동용 예약 열쇠. 없으면 null. */
    val route: String?,
    /** route 를 뺀 나머지 문자열 데이터. */
    val extras: Map<String, String>,
    /** 기기 안 예약 알림이면 true, 서버 푸시면 false. */
    val isLocal: Boolean
)
