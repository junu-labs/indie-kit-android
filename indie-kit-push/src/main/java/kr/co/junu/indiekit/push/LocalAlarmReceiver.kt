/*
 * LocalAlarmReceiver.kt — IndieKitPush
 *
 * 역할
 *  - 예약 알림이 울릴 시각에 AlarmManager 가 깨우는 리시버.
 *  - 하는 일 세 가지:
 *    1. 저장소에서 그 알림의 내용 (제목 / 본문 / route) 을 꺼내 띄운다.
 *    2. 반복 알림 (매일 / 매주) 이면 다음 회차 알람을 다시 건다.
 *    3. 한 번짜리면 저장소에서 지운다.
 *
 * 주요 개념
 *  - intent 에는 알림 id 만 싣는다. 내용은 항상 저장소가 기준 —
 *    같은 id 로 재등록해 내용이 바뀌어도 울릴 때 최신 내용이 나온다.
 *
 * 주의사항
 *  - 앱 프로세스가 죽어 있어도 불린다. configure 와 무관하게 저장소 + 채널만으로 동작해야 한다.
 */

package kr.co.junu.indiekit.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.co.junu.indiekit.core.IKLogger

/** 예약 알림이 울릴 때 깨어나는 리시버. */
internal class LocalAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val store = PushDeviceStore(context)
        val stored = store.scheduledNotifications().firstOrNull { it.id == id }
        if (stored == null) {
            IKLogger.push.debug("울릴 예약 알림이 저장소에 없음 (id=$id) — 취소된 것으로 보고 무시")
            return
        }

        IKLogger.push.info("예약 알림 울림 (id=$id)")
        NotificationPresenter.show(
            context = context,
            notificationId = id.hashCode(),
            title = stored.title,
            body = stored.body,
            route = stored.route,
            extras = emptyMap(),
            isLocal = true
        )

        when (stored.trigger) {
            is PushTrigger.Once -> {
                // 한 번짜리는 여기서 끝 — 저장소에서 지운다.
                store.removeScheduled(id)
            }
            is PushTrigger.Daily, is PushTrigger.Weekly -> {
                // 반복 알림은 다음 회차를 다시 건다.
                LocalAlarmScheduler.schedule(context, stored)
            }
        }
    }

    companion object {
        /** 알람 intent 에 싣는 알림 id 열쇠. */
        const val EXTRA_ID = "kr.co.junu.indiekit.push.alarmId"
    }
}
