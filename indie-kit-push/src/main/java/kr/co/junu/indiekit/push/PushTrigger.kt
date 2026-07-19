/*
 * PushTrigger.kt — IndieKitPush
 *
 * 역할
 *  - 기기 안 예약 알림의 "언제 울릴까" 를 표현하는 값.
 *  - iOS 자매 (PushTrigger) 와 같은 세 종류 — 매일 / 한 번 / 매주.
 *
 * 주요 개념
 *  - Daily: 매일 같은 시각 (예: 저녁 8시 운동 리마인드). 반복.
 *  - Once: 정해진 한 시각 (epoch 밀리초) 에 한 번.
 *  - Weekly: 매주 같은 요일 / 시각. 반복.
 *  - dayOfWeek 번호는 iOS 자매 (Calendar 표준) 와 같게 맞춘다 — 1 = 일요일, 2 = 월요일, ... 7 = 토요일.
 *    (java.util.Calendar 의 SUNDAY=1 ... SATURDAY=7 과 같은 번호라 변환 없이 쓴다.)
 *
 * 주의사항
 *  - 시각은 기기의 현재 시간대 기준. 사용자가 시간대를 바꾸면 다음 예약부터 새 시간대의 그 시각.
 */

package kr.co.junu.indiekit.push

import java.util.Calendar

/** 예약 알림이 울릴 시점. 세 종류. */
public sealed class PushTrigger {

    /** 매일 같은 시각 (24시간제). 반복. */
    public data class Daily(val hour: Int, val minute: Int) : PushTrigger()

    /** 정해진 한 시각 (epoch 밀리초) 에 한 번. */
    public data class Once(val epochMillis: Long) : PushTrigger()

    /** 매주 같은 요일 / 시각. dayOfWeek: 1 = 일요일 ... 7 = 토요일. 반복. */
    public data class Weekly(val dayOfWeek: Int, val hour: Int, val minute: Int) : PushTrigger()

    /**
     * 다음으로 울릴 시각 (epoch 밀리초) 을 계산한다.
     *
     * 동작
     *  - Daily: 오늘의 그 시각이 아직 안 지났으면 오늘, 지났으면 내일.
     *  - Weekly: 이번 주의 그 요일 / 시각이 아직 안 지났으면 이번 주, 지났으면 다음 주.
     *  - Once: 등록된 시각 그대로. 이미 지났으면 null (울릴 일 없음).
     *
     * @param now 기준 시각 (epoch 밀리초). 테스트에서 고정값을 넣을 수 있게 매개변수로 받는다.
     * @return 다음 울릴 시각. 없으면 null.
     */
    public fun nextFireTime(now: Long = System.currentTimeMillis()): Long? {
        return when (this) {
            is Once -> if (epochMillis > now) epochMillis else null

            is Daily -> {
                val cal = calendarAt(now, hour, minute)
                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }

            is Weekly -> {
                val cal = calendarAt(now, hour, minute)
                cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                // set(DAY_OF_WEEK) 은 "이번 주" 안에서 움직인다 — 이미 지난 시각이면 한 주 뒤로.
                if (cal.timeInMillis <= now) {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
        }
    }

    /** 기준 시각의 날짜에 시 / 분만 바꾼 Calendar 를 만든다 (초 이하 0). */
    private fun calendarAt(now: Long, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
