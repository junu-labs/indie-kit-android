/*
 * NotificationPresenter.kt — IndieKitPush
 *
 * 역할
 *  - 알림 한 건을 실제로 화면에 띄우는 도우미.
 *  - 세 곳에서 쓰인다:
 *    1. 앱이 앞에 있을 때 도착한 서버 푸시 (IndieKitPushService)
 *    2. 예약 알림이 울릴 때 (LocalAlarmReceiver)
 *  - 누르면 앱의 시작 화면이 열리며 내용물이 intent 에 실려 간다
 *    → 시작 화면의 IndieKitPush.handleIntent 가 onTap 으로 전달.
 *
 * 주요 개념
 *  - 채널: Android 8 부터 알림은 채널 소속이 필수. 모듈은 기본 채널 하나 ("indiekit_push") 를 만든다.
 *    채널 이름은 configure 때 앱이 정한 것 (저장소에 보관 — 앱 프로세스 없이 리시버만 깨어나도 접근).
 *  - 작은 아이콘: configure 때 앱이 지정. 미지정이면 앱 아이콘 → 그래도 없으면 OS 기본 그림.
 *
 * 주의사항
 *  - 알림 권한이 없으면 notify 가 조용히 무시된다 (Android 13+). 그래서 미리 검사해 경고 로그를 남긴다.
 */

package kr.co.junu.indiekit.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kr.co.junu.indiekit.core.IKLogger

/** 알림을 실제로 화면에 띄우는 도우미. 모듈 안에서만 쓴다. */
internal object NotificationPresenter {

    /** 모듈이 만드는 기본 채널의 고정 이름표. 사용자에게 보이는 이름은 store.channelName. */
    const val CHANNEL_ID = "indiekit_push"

    /** intent 에 내용물을 실을 때 쓰는 예약 열쇠들 — 앱의 다른 extra 와 안 겹치게 접두어. */
    const val EXTRA_MARKER = "kr.co.junu.indiekit.push.tap"
    const val EXTRA_TITLE = "kr.co.junu.indiekit.push.title"
    const val EXTRA_BODY = "kr.co.junu.indiekit.push.body"
    const val EXTRA_ROUTE = "kr.co.junu.indiekit.push.route"
    const val EXTRA_IS_LOCAL = "kr.co.junu.indiekit.push.isLocal"
    const val EXTRA_DATA_KEYS = "kr.co.junu.indiekit.push.dataKeys"

    /** 기본 채널을 만든다 (이미 있으면 이름만 갱신). configure 와 리시버 양쪽에서 부른다. */
    fun ensureChannel(context: Context) {
        val store = PushDeviceStore(context)
        val name = store.channelName ?: "알림"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
    }

    /**
     * 알림 한 건을 띄운다.
     *
     * @param notificationId 시스템 알림판에서의 구분 번호. 같은 번호로 다시 띄우면 갈아끼워진다.
     * @param extras route 를 뺀 나머지 문자열 데이터 — 누름 intent 에 같이 실린다.
     */
    fun show(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        route: String?,
        extras: Map<String, String>,
        isLocal: Boolean
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            IKLogger.push.warning("알림 권한이 없어 표시 못함 — requestPermission 호출 여부 확인")
            return
        }

        ensureChannel(context)
        val store = PushDeviceStore(context)

        // 누르면 앱 시작 화면이 열리며 내용물이 실려 간다.
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent == null) {
            IKLogger.push.error("앱 시작 화면 intent 를 찾지 못해 알림 표시 못함")
            return
        }
        launchIntent.putExtra(EXTRA_MARKER, true)
        launchIntent.putExtra(EXTRA_TITLE, title)
        launchIntent.putExtra(EXTRA_BODY, body)
        launchIntent.putExtra(EXTRA_IS_LOCAL, isLocal)
        if (route != null) launchIntent.putExtra(EXTRA_ROUTE, route)
        // 데이터는 각자 자기 열쇠로 싣고, 어떤 열쇠들이 데이터인지 목록을 따로 실어 되읽을 때 구분.
        for ((key, value) in extras) launchIntent.putExtra(key, value)
        launchIntent.putExtra(EXTRA_DATA_KEYS, extras.keys.toTypedArray())

        val tapIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 작은 아이콘: 앱 지정 → 앱 아이콘 → OS 기본 그림 순서로 고른다.
        val smallIcon = store.smallIconRes.takeIf { it != 0 }
            ?: context.applicationInfo.icon.takeIf { it != 0 }
            ?: android.R.drawable.ic_dialog_info

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (error: SecurityException) {
            // 권한 검사와 notify 사이에 권한이 꺼진 극단 경우 — 죽지 않고 로그만.
            IKLogger.push.warning("알림 표시 권한 거부: ${error.message}")
        }
    }
}
