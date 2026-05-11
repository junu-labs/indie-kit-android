/*
 * FirebaseAnalyticsRecorder.kt — IndieKitAnalytics
 *
 * 역할
 *  - 약한 연결 통로 (`AnalyticsBus`) 에 꽂힐 어댑터. 다른 모듈 (광고 / 결제 / 로그인) 이
 *    `AnalyticsBus.record(...)` 로 흘려보낸 이벤트를 받아 Firebase Analytics 로 전달.
 *
 * 주요 개념
 *  - 광고 모듈은 통계 모듈을 모름. AnalyticsRecorder 인터페이스만 알고 있음.
 *  - 통계 모듈이 깔린 앱이면 `IndieKitAnalytics.configure(application)` 시점에 이 어댑터가 등록 → 자동 연결.
 *  - 통계 모듈이 안 깔린 앱이면 등록 안 됨 → 광고 모듈 이벤트는 Logcat 으로만.
 *
 * 주의사항
 *  - record 는 Firebase Analytics 에 직접 위임. Firebase 가 thread-safe 라 추가 동기화 불필요.
 *  - configure 호출 전이거나 Firebase 시작이 실패한 경우엔 IndieKitAnalytics.log 가 알아서 무시 — 여기선 그냥 위임만.
 *
 * iOS 자매 (`FirebaseAnalyticsRecorder.swift`) 와 같은 역할.
 */

package kr.co.junu.indiekit.analytics

import kr.co.junu.indiekit.core.AnalyticsRecorder
import kr.co.junu.indiekit.core.AnalyticsValue

/**
 * `AnalyticsBus` 에 등록되는 어댑터.
 * 다른 모듈에서 흘려보낸 이벤트를 IndieKitAnalytics 의 일반 이벤트 길로 그대로 전달.
 *
 * `object` 채택 — 인스턴스가 1개로 충분하고, AnalyticsBus.register 가 같은 슬롯을 매번 갱신해도 안전.
 */
internal object FirebaseAnalyticsRecorder : AnalyticsRecorder {
    override fun record(event: String, parameters: Map<String, AnalyticsValue>?) {
        // IndieKitAnalytics.log 자체가 ensureConfigured / null 안전성을 다 처리한다.
        // 여기서 다시 한번 검사할 필요 없음.
        IndieKitAnalytics.log(event, parameters)
    }
}
