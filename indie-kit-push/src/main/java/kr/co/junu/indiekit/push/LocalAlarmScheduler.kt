/*
 * LocalAlarmScheduler.kt — IndieKitPush
 *
 * 역할
 *  - 예약 알림 한 건을 AlarmManager 에 실제로 거는 / 빼는 도우미.
 *  - IndieKitPush (등록 / 취소), LocalAlarmReceiver (반복 다음 회차), BootReceiver (재부팅 복원)
 *    세 곳이 같은 방식으로 알람을 걸도록 한 자리에 모았다.
 *
 * 주요 개념
 *  - 시각 정확도 (PUSH_PLAN.md 10번 항목 2번):
 *    기본은 setAndAllowWhileIdle — 절전 (Doze) 중에도 울리지만 몇 분 오차 허용. 리마인드 용도에 충분.
 *    exact=true 인 알림만 setExactAndAllowWhileIdle — 단, 앱이 자기 매니페스트에
 *    SCHEDULE_EXACT_ALARM 권한을 선언하고 사용자가 허용해 둔 경우에만. 아니면 기본 방식으로 내려간다.
 *  - PendingIntent 의 구분 번호는 알림 id 의 hashCode — 같은 id 로 다시 걸면 갈아끼워진다.
 */

package kr.co.junu.indiekit.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kr.co.junu.indiekit.core.IKLogger

/** AlarmManager 에 알람을 거는 / 빼는 도우미. */
internal object LocalAlarmScheduler {

    /** 알림 한 건의 다음 회차 알람을 건다. 다음 회차가 없으면 (지난 한 번짜리) 저장소에서 지운다. */
    fun schedule(context: Context, notification: StoredLocalNotification) {
        val store = PushDeviceStore(context)
        val fireAt = notification.trigger.nextFireTime()
        if (fireAt == null) {
            IKLogger.push.debug("예약 알림의 다음 울릴 시각 없음 (id=${notification.id}) — 저장소에서 제거")
            store.removeScheduled(notification.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, notification.id)

        // 분 단위 정확도가 필요한 앱만 정확 알람 — 권한이 있을 때만 (없으면 기본 방식으로 내려감).
        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (notification.exact && canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        } else {
            if (notification.exact) {
                IKLogger.push.warning("정확 알람 권한 없음 (id=${notification.id}) — 몇 분 오차 방식으로 대신 겁니다")
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
        }
        IKLogger.push.debug("예약 알림 알람 걸음 (id=${notification.id}, at=$fireAt)")
    }

    /** 알림 한 건의 알람을 뺀다. */
    fun cancel(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, id))
    }

    /** 알람용 PendingIntent — id 가 같으면 언제나 같은 것으로 취급된다 (갈아끼우기 / 취소의 열쇠). */
    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, LocalAlarmReceiver::class.java)
            .putExtra(LocalAlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
