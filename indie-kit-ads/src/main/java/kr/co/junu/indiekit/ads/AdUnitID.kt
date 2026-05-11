/*
 * AdUnitID.kt — IndieKitAds
 *
 * 역할
 *  - 광고 ID 한 쌍 (debug / release) 을 받아 빌드 환경에 맞는 값을 골라 주는 작은 그릇.
 *  - 미주입 시 Google 공식 테스트 ID 자동 적용 — 개발 초기 / SDK 동작 확인용.
 *
 * 주요 개념
 *  - 출시 직전 앱이 발급받은 실제 ID 를 `release` 자리에 한 번만 채워 주면 끝.
 *  - 빌드 분기는 라이브러리가 대신 한다 — SolTi 의 옛 패턴 (앱 안에서 상수 분기) 을 라이브러리로 흡수.
 *  - 빌드 환경 판별: ApplicationInfo.FLAG_DEBUGGABLE (사용처의 BuildConfig.DEBUG 직접 참조하면 모듈 의존성 충돌이라 회피).
 *
 * 사용 방법
 *  ```kotlin
 *  IndieKitAds.configure(
 *      context = this,
 *      bannerAdUnitID       = AdUnitID(release = "ca-app-pub-..."),
 *      interstitialAdUnitID = AdUnitID(),                          // 둘 다 비우면 테스트 ID 사용
 *      rewardedAdUnitID     = AdUnitID(debug = "ca-app-pub-개별테스트", release = "ca-app-pub-출시")
 *  )
 *  ```
 *
 * 주의사항
 *  - 구글 정책: 출시 앱에서 테스트 ID 사용 금지. 정책 위반이지만 자동 차단은 하지 않음 (인디 앱 특성상 너무 가혹).
 *  - iOS 자매 (`AdUnitID` struct) 와 같은 매개변수 이름 / 결과 동작.
 */

package kr.co.junu.indiekit.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import kr.co.junu.indiekit.core.IKLogger

/**
 * 광고 ID 한 쌍.
 *
 * 두 자리 모두 nullable — 미주입 시 Google 공식 테스트 ID 자동 적용.
 *
 * @property debug 디버그 빌드에서 쓸 광고 ID. null 이면 Google 공식 테스트 ID 사용.
 * @property release 출시 빌드에서 쓸 광고 ID. null 이면 Google 공식 테스트 ID 사용 (정책 위반 — 출시 전 채워야 함).
 */
public data class AdUnitID(
    public val debug: String? = null,
    public val release: String? = null
) {
    /**
     * 현재 빌드 환경에 맞는 광고 ID 를 돌려준다.
     *
     * @param context 빌드 환경 (debuggable) 판별용. configure 에서 받은 application context.
     * @param testFallback 디버그 / 출시 자리가 모두 비었을 때 쓸 Google 테스트 ID.
     * @return 사용할 광고 ID (절대 null 아님).
     */
    public fun resolve(context: Context, testFallback: String): String {
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebug) {
            debug ?: testFallback
        } else {
            release ?: run {
                // 출시 빌드인데 release 가 비어 있음 — 정책 위반 가능. Logcat 으로 강한 경고만 남기고 테스트 ID 사용.
                IKLogger.ads.error(
                    "출시 빌드인데 release 광고 ID 가 비어 있습니다. 구글 정책 위반입니다 — 출시 전 채워 주세요."
                )
                testFallback
            }
        }
    }
}

/**
 * Google 이 공식 제공하는 테스트 광고 ID 모음.
 *
 * 출처: https://developers.google.com/admob/android/test-ads
 * 이 ID 들은 어떤 환경에서도 광고를 정상 로드하는 영구적인 테스트 자리.
 *
 * iOS 자매 (`AdMobTestUnitID`) 와 같은 4개 — 단 안드로이드 / iOS 용 ID 가 서로 다름.
 */
public object AdMobTestUnitID {
    /** 배너 광고 테스트 ID (Android). */
    public const val BANNER: String = "ca-app-pub-3940256099942544/6300978111"

    /** 전면 광고 테스트 ID (Android). */
    public const val INTERSTITIAL: String = "ca-app-pub-3940256099942544/1033173712"

    /** 리워드 광고 테스트 ID (Android). */
    public const val REWARDED: String = "ca-app-pub-3940256099942544/5224354917"

    /** Native 광고 테스트 ID (Android). */
    public const val NATIVE: String = "ca-app-pub-3940256099942544/2247696110"
}

/**
 * 리워드 광고로 받은 보상 정보.
 *
 * iOS 자매 (`AdReward` struct) 와 같은 두 필드.
 *
 * @property amount 보상 양 (AdMob 콘솔에서 설정).
 * @property type 보상 종류 이름 (예: "coins", "hints").
 */
public data class AdReward(
    public val amount: Int,
    public val type: String
)
