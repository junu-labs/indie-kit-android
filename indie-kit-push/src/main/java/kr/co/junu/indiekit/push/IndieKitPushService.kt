/*
 * IndieKitPushService.kt — IndieKitPush
 *
 * 역할
 *  - FCM 이 이 앱으로 보낸 것들을 받는 서비스. 모듈 매니페스트에 이미 선언되어 있어
 *    사용처 앱은 아무것도 안 해도 된다.
 *  - 받는 것 두 가지:
 *    1. onNewToken — 알림 주소가 새로 발급 / 갱신됐을 때 → 서버 등록으로 넘김.
 *    2. onMessageReceived — 알림이 도착했을 때 → 직접 띄운다.
 *
 * 주요 개념
 *  - onMessageReceived 가 불리는 경우: 앱이 앞에 있을 때의 모든 알림 + 데이터만 실린 알림.
 *    앱이 뒤에 있고 알림 (notification) 부분이 실려 있으면 시스템 알림판이 대신 띄우고
 *    여긴 안 불린다 — 그 경우의 누름은 IndieKitPush.handleIntent 가 받는다.
 *
 * 주의사항
 *  - 이 서비스는 앱 프로세스가 죽어 있어도 FCM 이 깨울 수 있다. configure 전일 수 있으므로
 *    onNewToken 은 저장만 해 두는 안전한 경로 (IndieKitPush.internalOnNewToken) 로 넘긴다.
 */

package kr.co.junu.indiekit.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kr.co.junu.indiekit.core.IKLogger

/** FCM 수신 서비스 — 알림 주소 갱신 / 도착 알림이 여기로 들어온다. */
internal class IndieKitPushService : FirebaseMessagingService() {

    /** FCM 이 알림 주소를 새로 발급 / 갱신했을 때. */
    override fun onNewToken(token: String) {
        IKLogger.push.info("알림 주소 갱신 수신")
        IndieKitPush.internalOnNewToken(applicationContext, token)
    }

    /** 알림이 도착했을 때 (앱이 앞에 있거나, 데이터만 실린 알림). */
    override fun onMessageReceived(message: RemoteMessage) {
        // 제목 / 본문: 알림 부분이 있으면 그걸, 없으면 (데이터만 실린 알림) 데이터의 title / body 열쇠.
        val title = message.notification?.title ?: message.data["title"] ?: ""
        val body = message.notification?.body ?: message.data["body"] ?: ""

        // 데이터에서 route 를 꺼내고, 나머지 (title / body 중복분 제외) 는 extras 로.
        val route = message.data["route"]
        val extras = message.data.filterKeys { it != "route" && it != "title" && it != "body" }

        IKLogger.push.info("알림 도착 (route=${route ?: "-"}) — 직접 표시")
        NotificationPresenter.show(
            context = applicationContext,
            // 같은 서버 알림이 두 번 오면 갈아끼워지도록 메시지 고유값 기반, 없으면 시각 기반.
            notificationId = (message.messageId ?: System.currentTimeMillis().toString()).hashCode(),
            title = title,
            body = body,
            route = route,
            extras = extras,
            isLocal = false
        )
    }
}
