/*
 * IKLogger.kt — IndieKitCore
 *
 * 역할
 *  - println() 대신 5개 모듈이 공유해서 쓰는 통일된 로거.
 *  - android.util.Log 기반이라 Logcat 에 자동 표시.
 *
 * 주요 개념
 *  - 카테고리: analytics / ads / network / billing / auth / core — 어느 모듈이 찍은 로그인지 한눈에 구분.
 *  - tag 형식: "IndieKit.{카테고리}". Logcat 에서 "IndieKit" 한 단어로 필터링하면 라이브러리 전체 로그만 모아 볼 수 있다.
 *  - 레벨: debug / info / warning / error — Android Log 표준 4단계.
 *
 * 사용 방법
 *  ```kotlin
 *  IKLogger.ads.info("배너 광고 로드 성공")
 *  IKLogger.network.warning("401 응답 → 인증값 갱신 시도")
 *  IKLogger.billing.error("결제 검증 실패: ${'$'}error")
 *  ```
 *
 * 주의사항
 *  - 사용자 개인 정보 (이메일, 토큰 등) 는 직접 찍지 말 것. Logcat 은 마스킹 자동이 아니라 호출자 책임.
 *  - iOS 자매 (IKLogger / OSLog) 와 진입점 이름 1:1 대칭.
 */

package kr.co.junu.indiekit.core

import android.util.Log

/**
 * 5개 모듈이 공유하는 로거 묶음.
 *
 * 카테고리별 정적 인스턴스를 제공해 호출처에서 `IKLogger.ads.info(...)` 형태로 쓴다.
 * `subsystem` 은 모든 모듈에서 공통으로 `kr.kr.co.junu.indiekit` 사용 → Logcat 필터링 편의.
 */
public object IKLogger {

    /**
     * 모든 IndieKit 로거가 공유하는 subsystem 이름.
     * Logcat 에서 이 값으로 필터링하면 IndieKit 관련 로그만 모아 볼 수 있다.
     * iOS 자매와 같은 값 (`kr.kr.co.junu.indiekit`) — 양쪽 폰 동일 식별.
     */
    public const val subsystem: String = "kr.kr.co.junu.indiekit"

    /** 통계 모듈용 로거 — 이벤트 기록, 화면 이동 등. */
    public val analytics: Category = Category("analytics")

    /** 광고 모듈용 로거 — 배너 / 전면 / 리워드 광고 적재, 표시, 동의창. */
    public val ads: Category = Category("ads")

    /** 네트워크 모듈용 로거 — 요청 / 응답 / 인증값 자동 갱신. */
    public val network: Category = Category("network")

    /** 결제 모듈용 로거 — 구매 흐름, 영수증 검증, 권한 변화. */
    public val billing: Category = Category("billing")

    /** 로그인 모듈용 로거 — 제공자별 로그인, 우리 서버 세션 발급. */
    public val auth: Category = Category("auth")

    /** 푸시 모듈용 로거 — 권한, 알림 주소 등록, 수신 / 누름, 예약 알림. */
    public val push: Category = Category("push")

    /** IndieKitCore 자체용 로거 — 약한 연결 통로 등록 등 라이브러리 내부 동작. */
    public val core: Category = Category("core")

    /**
     * 한 모듈에 묶인 로거.
     *
     * Logcat tag 는 `"IndieKit.{name}"` 형태. Android tag 길이 제한 (23자) 안에 들어오도록 한다.
     * 가장 긴 카테고리는 "analytics" 라 `"IndieKit.analytics"` = 18자, 안전.
     */
    public class Category internal constructor(name: String) {
        private val tag: String = "IndieKit.$name"

        /** 디버깅용 자세한 흐름. 출시 빌드에서도 Logcat 에 남는다 — 호출자 판단. */
        public fun debug(message: String) {
            Log.d(tag, message)
        }

        /** 일반 정보 — "처음 설정 완료", "배너 적재" 같은 흐름 사건. */
        public fun info(message: String) {
            Log.i(tag, message)
        }

        /** 의도와 다른 일이 일어났지만 흐름은 계속 — "401 → 토큰 갱신" 같은. */
        public fun warning(message: String) {
            Log.w(tag, message)
        }

        /** 흐름이 더 못 가는 경우 — "결제 검증 실패", "Firebase 시작 실패". */
        public fun error(message: String, throwable: Throwable? = null) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
