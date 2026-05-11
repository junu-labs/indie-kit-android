/*
 * IndieKitAnalytics.kt — IndieKitAnalytics
 *
 * 역할
 *  - Firebase Analytics 의 시작 호출, 이벤트 / 화면 / 사용자 속성 기록을 한 줄로.
 *  - 다른 IndieKit 모듈 (광고 / 결제 / 로그인) 이 흘려보낸 이벤트도 자동으로 통계로 흘러가게.
 *
 * 주요 개념
 *  - 단일 진입점: object 의 정적 메서드. 인스턴스 만들 일 없음 (Firebase Analytics 자체가 싱글턴).
 *  - 처음 설정: 앱 시작 시 한 번 `IndieKitAnalytics.configure(application)` 호출.
 *    기본값 `autoInitFirebase = true` 이면 `FirebaseApp.initializeApp(application)` 까지 자동.
 *  - 약한 연결 통로 자동 등록: `configure` 가 끝나면 자기 자신을 `AnalyticsBus` 에 등록.
 *    이후 광고 / 결제 / 로그인 모듈이 `AnalyticsBus.record(...)` 호출하면 자동으로 Firebase 로 흘러감.
 *
 * 사용 방법
 *  ```kotlin
 *  // Application.onCreate 에서:
 *  import kr.co.junu.indiekit.analytics.IndieKitAnalytics
 *  IndieKitAnalytics.configure(this)
 *
 *  // 이후 어디서든:
 *  IndieKitAnalytics.logScreen("HomeScreen")
 *  IndieKitAnalytics.log("timer_start", analyticsParams("mode" to "hiit", "duration" to 60))
 *  IndieKitAnalytics.setUserId("u_123")
 *  IndieKitAnalytics.setUserProperty("plan_tier", "pro_yearly")
 *  ```
 *
 * 주의사항
 *  - `google-services.json` 이 사용처 앱 모듈에 있어야 한다 (Firebase 콘솔에서 받음).
 *  - 사용처 앱의 build.gradle.kts 에 `id("com.google.gms.google-services")` 플러그인 적용 필요.
 *    (라이브러리는 google-services 플러그인을 적용하지 않음 — 사용처 책임.)
 *  - 이미 다른 곳에서 Firebase 초기화를 끝낸 앱이라면 `autoInitFirebase = false` 로 끈다.
 *
 * iOS 자매 (`IndieKitAnalytics.swift`) 와 진입점 이름 1:1 대칭.
 */

package kr.co.junu.indiekit.analytics

import android.content.Context
import kr.co.junu.indiekit.core.AnalyticsBus
import kr.co.junu.indiekit.core.AnalyticsValue
import kr.co.junu.indiekit.core.IKLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.concurrent.locks.ReentrantLock

/**
 * 통계 모듈의 단일 진입점.
 */
public object IndieKitAnalytics {

    // ────────────────────────────────────────────────────────────────────────
    // 내부 상태
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 모듈이 살아 있는지 확인하는 표식.
     * 0단계 placeholder 의 `isPlaceholder` 자리를 그대로 유지 (부트스트랩 테스트가 의지하는 약속).
     */
    public const val isPlaceholder: Boolean = false

    @Volatile
    private var configured: Boolean = false
    private val configureLock = ReentrantLock()

    /**
     * Firebase Analytics 인스턴스 캐시. configure 후에만 채워짐.
     * configure 가 안 된 상태에선 null — log* 호출이 무시된다.
     */
    @Volatile
    private var analytics: FirebaseAnalytics? = null

    // ────────────────────────────────────────────────────────────────────────
    // 처음 설정
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 통계 모듈을 한 번 처음 설정한다.
     *
     * 동작
     *  1. `autoInitFirebase` 가 true 이면 `FirebaseApp.initializeApp(context)` 호출 (이미 되어 있으면 건너뜀).
     *     Firebase 초기화는 앱 생애 동안 **한 번만** 일어난다 (FirebaseInitProvider 가 자동 처리하기도 함).
     *  2. 자기 자신 (FirebaseAnalyticsRecorder) 을 `AnalyticsBus` 에 등록 → 광고 / 결제 / 로그인 모듈 이벤트가 자동 통계 진입.
     *     이 등록은 **매 호출마다** 갱신된다. (누군가 `AnalyticsBus.unregister()` 후 재호출해도 안전)
     *
     * 멱등성
     *  - 두 번째 호출은 사용자에게 보이는 효과가 동일 (register 가 같은 어댑터로 슬롯을 다시 채움).
     *  - 차이는 Firebase 초기화 호출이 다시 안 됨 (한 번만).
     *
     * @param context 보통 Application context. applicationContext 로 정규화해서 보관 (Activity 누수 방지).
     * @param autoInitFirebase
     *   true (기본): 라이브러리가 `FirebaseApp.initializeApp(context)` 까지 책임.
     *   false: 앱이 다른 곳에서 이미 Firebase 초기화를 끝냈을 때 (단위 테스트 / 커스텀 옵션).
     */
    public fun configure(context: Context, autoInitFirebase: Boolean = true) {
        configureLock.lock()
        try {
            val alreadyConfigured = configured

            if (!alreadyConfigured) {
                if (autoInitFirebase) {
                    startFirebaseIfNeeded(context.applicationContext)
                }
                analytics = try {
                    FirebaseAnalytics.getInstance(context.applicationContext)
                } catch (t: Throwable) {
                    // Firebase 초기화 실패 (예: google-services.json 누락) 시에도 라이브러리 자체는 살아 있게.
                    // log* 호출은 모두 no-op 으로 흘러간다.
                    IKLogger.analytics.error("FirebaseAnalytics.getInstance 실패 — log 호출은 무시됩니다.", t)
                    null
                }
                configured = true
                IKLogger.analytics.info("IndieKitAnalytics 처음 설정 완료 (autoInitFirebase=$autoInitFirebase)")
            } else {
                IKLogger.analytics.debug("IndieKitAnalytics.configure 재호출 — Firebase 초기화는 건너뜀, AnalyticsBus 등록만 갱신")
            }
        } finally {
            configureLock.unlock()
        }

        // 약한 연결 통로 등록은 매 호출마다 보장.
        // 누군가 unregister 후 재구성하려는 흐름도 자연스럽게 지원.
        AnalyticsBus.register(FirebaseAnalyticsRecorder)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 화면 이동
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 화면 이동 이벤트 한 건 기록.
     *
     * Firebase 의 표준 화면 이벤트 (`screen_view`) 로 기록. Firebase Console 의
     * 화면 보고서에 자동 집계됨.
     *
     * @param name 화면 이름 (예: "HomeScreen", "SettingsScreen"). 사람이 읽을 수 있게.
     * @param klass 화면 class 이름. Compose 면 보통 null 또는 "Compose". View 시스템이면 Activity / Fragment class 이름.
     */
    public fun logScreen(name: String, klass: String? = null) {
        val analytics = ensureConfigured() ?: return

        val params = android.os.Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
            if (klass != null) {
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, klass)
            }
        }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 일반 이벤트
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 임의 이벤트 한 건 기록.
     *
     * 도메인 이벤트 (예: timer_start, preset_create) 가 모두 이 길로.
     * 매개값 (`parameters`) 은 `AnalyticsValue` 로 묶음 — 호출 측에서 [analyticsParams] 헬퍼로 자연스럽게 빌드.
     */
    public fun log(event: String, parameters: Map<String, AnalyticsValue>? = null) {
        val analytics = ensureConfigured() ?: return
        analytics.logEvent(event, parameters?.toFirebaseBundle())
    }

    // ────────────────────────────────────────────────────────────────────────
    // 표준 이벤트 (자주 쓰는 헬퍼)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 회원가입 완료 이벤트. Firebase 의 표준 이벤트 (`sign_up`) 와 매개값 (`method`) 사용.
     * @param method 회원가입 경로 (예: "apple", "google", "kakao", "email").
     */
    public fun logSignUp(method: String) {
        val analytics = ensureConfigured() ?: return
        val params = android.os.Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, params)
    }

    /** 로그인 이벤트. Firebase 의 표준 이벤트 (`login`) 와 매개값 (`method`) 사용. */
    public fun logLogin(method: String) {
        val analytics = ensureConfigured() ?: return
        val params = android.os.Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        }
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, params)
    }

    /**
     * 결제 완료 이벤트. Firebase 의 표준 이벤트 (`purchase`) 사용.
     * @param productId 상품 식별자 (Play Console 의 Product ID).
     * @param currency 통화 코드 (ISO 4217, 예: "KRW", "USD").
     * @param value 결제 금액. Double 로 받음 — Firebase 가 동일 형식으로 받음.
     */
    public fun logPurchase(productId: String, currency: String, value: Double) {
        val analytics = ensureConfigured() ?: return
        val params = android.os.Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putDouble(FirebaseAnalytics.Param.VALUE, value)
        }
        analytics.logEvent(FirebaseAnalytics.Event.PURCHASE, params)
    }

    // ────────────────────────────────────────────────────────────────────────
    // 사용자 속성
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 사용자 ID 설정. 로그인하면 우리 시스템의 사용자 식별자를, 로그아웃하면 null.
     * @param id 우리 시스템의 사용자 ID. null 이면 제거.
     */
    public fun setUserId(id: String?) {
        val analytics = ensureConfigured() ?: return
        analytics.setUserId(id)
    }

    /**
     * 사용자 속성 한 건 설정 (예: plan_tier=pro_yearly, language=ko).
     * @param name 속성 이름. Firebase Console 에서 미리 등록되어 있어야 의미 있는 분석 가능.
     * @param value 속성 값. null 이면 제거.
     */
    public fun setUserProperty(name: String, value: String?) {
        val analytics = ensureConfigured() ?: return
        analytics.setUserProperty(name, value)
    }

    /**
     * 디버그 태그 켬 / 끔.
     *
     * 켜면 모든 이벤트에 `is_debug=true` 매개값이 자동 첨부.
     * Firebase Console 에서 디버그 빌드 이벤트를 분리해 볼 때 유용.
     */
    public fun setDebugTag(enabled: Boolean) {
        val analytics = ensureConfigured() ?: return
        if (enabled) {
            val params = android.os.Bundle().apply {
                putBoolean("is_debug", true)
            }
            analytics.setDefaultEventParameters(params)
        } else {
            analytics.setDefaultEventParameters(null)
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Firebase 의 시작 호출이 필요하면 한다.
     * 이미 다른 곳에서 한 적 있으면 (`FirebaseApp.getApps(context).isNotEmpty()`) 건너뜀.
     */
    private fun startFirebaseIfNeeded(applicationContext: Context) {
        try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
                IKLogger.analytics.info("FirebaseApp.initializeApp 자동 호출")
            } else {
                IKLogger.analytics.debug("FirebaseApp 이 이미 시작되어 있음 — 자동 호출 건너뜀")
            }
        } catch (t: Throwable) {
            // google-services.json 누락 등 — 라이브러리는 살아 있게, log* 만 no-op.
            IKLogger.analytics.error("FirebaseApp.initializeApp 실패 — google-services.json 이 사용처에 있는지 확인", t)
        }
    }

    /**
     * configure 가 끝났는지 + Firebase Analytics 인스턴스가 살아 있는지 검사.
     * 안 됐으면 Logcat 경고만 남기고 null 을 돌려준다 — 호출 측은 그대로 끝.
     */
    private fun ensureConfigured(): FirebaseAnalytics? {
        configureLock.lock()
        val current = try {
            if (!configured) null else analytics
        } finally {
            configureLock.unlock()
        }

        if (current == null) {
            IKLogger.analytics.warning("IndieKitAnalytics.configure 가 호출되지 않았거나 Firebase 가 시작되지 않은 상태에서 이벤트 기록 시도 — 무시합니다.")
        }
        return current
    }
}

// ────────────────────────────────────────────────────────────────────────────
// AnalyticsValue → Firebase Bundle 변환
// ────────────────────────────────────────────────────────────────────────────

/**
 * `Map<String, AnalyticsValue>` 를 Firebase Analytics 가 받는 Bundle 로 변환.
 *
 * `internal` 노출 — 같은 모듈의 [FirebaseAnalyticsRecorder] 도 같이 쓴다.
 */
internal fun Map<String, AnalyticsValue>.toFirebaseBundle(): android.os.Bundle {
    val bundle = android.os.Bundle()
    for ((key, value) in this) {
        when (value) {
            is AnalyticsValue.StringValue -> bundle.putString(key, value.value)
            is AnalyticsValue.IntValue -> bundle.putInt(key, value.value)
            is AnalyticsValue.DoubleValue -> bundle.putDouble(key, value.value)
            is AnalyticsValue.BoolValue -> bundle.putBoolean(key, value.value)
        }
    }
    return bundle
}
