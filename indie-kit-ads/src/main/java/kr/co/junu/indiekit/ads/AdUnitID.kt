/*
 * AdUnitID.kt — IndieKitAds
 *
 * 역할
 *  - 출시용 광고 ID (release) 한 개를 받아 빌드 환경에 맞는 값을 골라 주는 작은 그릇.
 *  - 디버그 빌드에선 release 와 무관하게 Google 공식 테스트 ID 자동 적용 — 개발 초기 / SDK 동작 확인용.
 *
 * 주요 개념
 *  - 출시 직전 앱이 발급받은 실제 ID 를 `release` 자리에 한 번만 채워 주면 끝.
 *  - 빌드 분기는 라이브러리가 대신 한다 — SolTi 의 옛 패턴 (앱 안에서 상수 분기) 을 라이브러리로 흡수.
 *  - 빌드 환경 판별: ApplicationInfo.FLAG_DEBUGGABLE (사용처의 BuildConfig.DEBUG 직접 참조하면 모듈 의존성 충돌이라 회피).
 *  - 디버그 빌드: release 값과 무관하게 항상 Google 테스트 ID (개발 중 광고 확인용).
 *  - 출시 빌드: release 가 비면 광고를 아예 안 띄운다 (null 반환). 테스트 광고를 출시 앱에 노출하면
 *    구글 정책 위반 / AdMob 계정 정지 위험이라, 보이게 두지 않고 막는다.
 *
 * 사용 방법
 *  ```kotlin
 *  IndieKitAds.configure(
 *      context = this,
 *      bannerAdUnitIDs = mapOf(
 *          IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-..."),
 *          "settings"                    to AdUnitID(release = "ca-app-pub-...-settings")
 *      )
 *  )
 *  ```
 *
 * 주의사항
 *  - 구글 정책: 출시 앱에서 테스트 ID 사용 금지. 출시 빌드에서 release 가 비면 광고를 표시하지 않아 정책 위반을 막는다.
 *  - iOS 자매 (`AdUnitID` struct) 와 같은 매개변수 이름 / 결과 동작.
 */

package kr.co.junu.indiekit.ads

import android.content.Context
import android.content.pm.ApplicationInfo
import kr.co.junu.indiekit.core.IKLogger

/**
 * 출시용 광고 ID 한 개를 담는 작은 그릇.
 *
 * 디버그 빌드에선 이 값과 무관하게 Google 공식 테스트 ID 가 쓰인다 (라이브러리가 알아서 채움).
 * 그래서 앱은 출시 ID 하나만 신경 쓰면 된다.
 *
 * @property release 출시 빌드에서 쓸 광고 ID. null 이면 출시 빌드에서 광고를 띄우지 않는다 (출시 전 채워야 함).
 */
public data class AdUnitID(
    public val release: String? = null
) {
    /**
     * 현재 빌드 환경에 맞는 광고 ID 를 돌려준다.
     *
     * 동작
     *  - 디버그 빌드: release 값과 무관하게 항상 Google 테스트 ID. 개발 중 실광고 노출은 구글 정책상 무효 트래픽이라 막는다.
     *  - 출시 빌드: release 값. 비어 있으면 null — 테스트 광고를 출시 앱에 노출하지 않기 위함 (구글 정책 위반 / 계정 정지 방지).
     *
     * @param context 빌드 환경 (debuggable) 판별용. configure 에서 받은 application context.
     * @param testFallback 디버그 빌드에서 쓸 Google 테스트 ID.
     * @return 사용할 광고 ID. 출시 빌드인데 release 가 비어 있으면 null — 이 경우 광고를 띄우지 않는다.
     */
    public fun resolve(context: Context, testFallback: String): String? {
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebug) {
            testFallback
        } else {
            release ?: run {
                // 출시 빌드인데 release 가 비어 있음 — 테스트 광고를 띄우면 구글 정책 위반이라 광고를 아예 표시하지 않는다.
                IKLogger.ads.error(
                    "출시 빌드인데 release 광고 ID 가 비어 있습니다 — 광고를 표시하지 않습니다. 출시 전 채워 주세요."
                )
                null
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
