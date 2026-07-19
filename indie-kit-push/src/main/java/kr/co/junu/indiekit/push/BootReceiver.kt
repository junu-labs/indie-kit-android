/*
 * BootReceiver.kt — IndieKitPush
 *
 * 역할
 *  - 기기 재부팅 때 예약 알림을 다시 걸어 주는 리시버.
 *  - AlarmManager 예약은 재부팅에 전부 사라진다 — 저장소 (PushDeviceStore) 의 목록이
 *    기준이 되는 원본이므로, 그걸 보고 하나씩 다시 건다.
 *
 * 주의사항
 *  - 재부팅 직후 앱 프로세스 / configure 없이 불린다. 저장소만으로 동작해야 한다.
 */

package kr.co.junu.indiekit.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kr.co.junu.indiekit.core.IKLogger

/** 재부팅 때 예약 알림을 복원하는 리시버. */
internal class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val stored = PushDeviceStore(context).scheduledNotifications()
        if (stored.isEmpty()) return

        IKLogger.push.info("재부팅 감지 — 예약 알림 ${stored.size}건 다시 걸기")
        for (notification in stored) {
            LocalAlarmScheduler.schedule(context, notification)
        }
    }
}
