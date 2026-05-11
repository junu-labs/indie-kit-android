/*
 * AnalyticsBus.kt — IndieKitCore
 *
 * 역할
 *  - 광고 / 결제 / 로그인 모듈이 자기 이벤트를 통계 모듈로 흘려보낼 때 쓰는 약한 연결 통로.
 *  - 통계 모듈을 깐 앱이면 자동 연결, 안 깐 앱이면 Logcat 로만 남기고 끝.
 *
 * 주요 개념 — "약한 연결" 이 무슨 뜻인가
 *  - 광고 모듈이 통계 모듈을 직접 import 하면, 통계 안 쓰는 앱도 Firebase 가 빌드에 끌려 들어옴.
 *  - 그래서 광고 모듈은 통계 모듈을 모르고, 그 자리에 인터페이스 (AnalyticsRecorder) 만 둔다.
 *  - 통계 모듈이 깔리면 자기 자신을 AnalyticsBus 에 등록 → 광고 모듈 이벤트가 흘러 들어감.
 *  - 통계 모듈이 안 깔리면 등록 안 됨 → 광고 모듈은 null 을 만나 그냥 Logcat 으로만 찍음.
 *
 * 사용 방법
 *  ```kotlin
 *  // 통계 모듈 (IndieKitAnalytics) 안에서 — 자기 자신을 등록:
 *  AnalyticsBus.register(FirebaseAnalyticsRecorder())
 *
 *  // 광고 모듈 (IndieKitAds) 안에서 — 이벤트 기록:
 *  AnalyticsBus.record("ad_impression", mapOf("format" to AnalyticsValue.StringValue("banner")))
 *  // 또는 헬퍼로:
 *  AnalyticsBus.record("ad_impression", analyticsParams("format" to "banner"))
 *  ```
 *
 * 주의사항
 *  - register 는 앱 생애 동안 한 번만 호출하는 것이 자연스럽다 (통계 모듈 configure 안에서 자동).
 *  - record 는 매우 자주 호출될 수 있으므로 thread-safe — ReentrantLock 으로 단일 슬롯 보호.
 */

package kr.co.junu.indiekit.core

import java.util.concurrent.locks.ReentrantLock

// ────────────────────────────────────────────────────────────────────────────
// 이벤트 값 타입
// ────────────────────────────────────────────────────────────────────────────

/**
 * 통계 이벤트에 함께 보낼 값의 타입.
 *
 * Firebase Analytics 가 받는 매개값 타입은 String / 숫자 / Bool 정도라, 그 범위로 닫힌 sealed class 로 표현.
 *
 * iOS 자매 (`AnalyticsValue` enum) 와 같은 4종 (string / int / double / bool).
 *
 * 호출 측에서 `mapOf("mode" to AnalyticsValue.StringValue("hiit"))` 처럼 길게 쓰기 싫으면
 * 같은 파일의 [analyticsParams] 헬퍼로 `analyticsParams("mode" to "hiit", "duration" to 60)` 형태로 줄여 쓸 수 있다.
 */
public sealed class AnalyticsValue {
    public data class StringValue(val value: String) : AnalyticsValue()
    public data class IntValue(val value: Int) : AnalyticsValue()
    public data class DoubleValue(val value: Double) : AnalyticsValue()
    public data class BoolValue(val value: Boolean) : AnalyticsValue()
}

/**
 * 호출 측 편의 헬퍼.
 *
 * `analyticsParams("mode" to "hiit", "duration" to 60, "ratio" to 0.85, "is_pro" to false)`
 * 처럼 자연스럽게 쓰기 위해 String / Int / Double / Boolean 만 받아 자동 wrap.
 *
 * 그 외 타입은 IllegalArgumentException — 호출자 책임으로 미리 변환.
 */
public fun analyticsParams(vararg pairs: Pair<String, Any>): Map<String, AnalyticsValue> {
    return pairs.associate { (key, value) ->
        key to when (value) {
            is String -> AnalyticsValue.StringValue(value)
            is Int -> AnalyticsValue.IntValue(value)
            is Long -> AnalyticsValue.IntValue(value.toInt())
            is Double -> AnalyticsValue.DoubleValue(value)
            is Float -> AnalyticsValue.DoubleValue(value.toDouble())
            is Boolean -> AnalyticsValue.BoolValue(value)
            else -> throw IllegalArgumentException(
                "analyticsParams 가 받지 못하는 타입: ${value::class.simpleName} ($key=$value). " +
                "String / Int / Long / Double / Float / Boolean 중 하나로 변환해 주세요."
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 약한 연결 통로
// ────────────────────────────────────────────────────────────────────────────

/**
 * 통계 모듈이 채택할 인터페이스.
 *
 * 통계 모듈 (IndieKitAnalytics) 의 내부 어댑터가 이 인터페이스를 채택하고
 * AnalyticsBus 에 등록한다. 다른 모듈은 AnalyticsBus 만 알면 된다.
 */
public interface AnalyticsRecorder {
    /**
     * 이벤트 한 건을 통계로 기록한다.
     * @param event 이벤트 이름 (예: "ad_impression", "purchase_completed").
     * @param parameters 이벤트와 함께 보낼 값들. 없으면 null.
     */
    public fun record(event: String, parameters: Map<String, AnalyticsValue>?)
}

/**
 * 약한 연결 통로의 단일 입구.
 *
 * 동작
 *  - `register` 로 통계 모듈이 자기 자신을 꽂는다.
 *  - `record` 는 등록된 recorder 가 있으면 그쪽으로 이벤트를 흘려보내고, 없으면 Logcat 으로만 남긴다.
 *
 * 동시성
 *  - ReentrantLock 으로 보호된 단일 슬롯. 매우 자주 호출돼도 안전.
 */
public object AnalyticsBus {

    /** 등록된 통계 기록자. 없으면 null. */
    @Volatile
    private var recorder: AnalyticsRecorder? = null

    /** 단일 슬롯 보호용 lock. */
    private val lock = ReentrantLock()

    /**
     * 통계 모듈이 자기 자신을 등록할 때 호출.
     * 이미 등록된 게 있으면 덮어쓴다 (대부분의 앱은 1회 호출).
     */
    public fun register(recorder: AnalyticsRecorder) {
        lock.lock()
        try {
            this.recorder = recorder
            IKLogger.core.info("통계 기록자 등록 완료")
        } finally {
            lock.unlock()
        }
    }

    /** 등록된 통계 기록자를 제거. 테스트에서 주로 사용. */
    public fun unregister() {
        lock.lock()
        try {
            recorder = null
        } finally {
            lock.unlock()
        }
    }

    /**
     * 이벤트 한 건 흘려보내기.
     *
     * - 등록된 recorder 가 있으면 그쪽으로 전달.
     * - 없으면 Logcat 에 debug 레벨로만 남김 (앱이 통계 모듈을 안 쓰는 경우).
     */
    public fun record(event: String, parameters: Map<String, AnalyticsValue>? = null) {
        // 짧은 임계 영역만 lock. recorder 자체 호출은 lock 밖에서 — 재진입 / 콜백 안전.
        lock.lock()
        val current: AnalyticsRecorder? = try {
            recorder
        } finally {
            lock.unlock()
        }

        if (current != null) {
            current.record(event, parameters)
        } else {
            // 통계 모듈이 등록 안 됐을 때의 대비책 — Logcat 로그만 남김.
            IKLogger.core.debug("이벤트 기록 (무등록): $event")
        }
    }

    /** 등록된 기록자가 있는지. 테스트 / 디버깅용. */
    public val isRegistered: Boolean
        get() {
            lock.lock()
            try {
                return recorder != null
            } finally {
                lock.unlock()
            }
        }
}
