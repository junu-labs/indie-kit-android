# indie-kit-android

안드로이드 인디 앱들이 Gradle 한 줄로 끌어다 쓰는 공용 라이브러리 묶음.

새 앱을 시작할 때마다 광고 / 로그인 / 결제 / 통계 / 네트워크 코드를 따로 짜지 않도록, 한 번 잘 만들어 두고 같이 쓴다.

## 들어 있는 것

| 모듈 | 무엇을 해 주는가 | 단계 | 상태 |
|---|---|---|---|
| `indie-kit-analytics` | Firebase Analytics — 화면 이동 / 이벤트 / 사용자 속성 | 1 | `v0.1.0` (라이브러리 완성, SolTi 검증 예정) |
| `indie-kit-ads` | AdMob 배너 / 전면 / 리워드 / Native + 유럽 광고 동의창 | 2 | **`v0.2.7` (실기기 검증 완료 — AdMob native validator "No implementation issues found" 통과)** |
| `indie-kit-network` | OkHttp 위 얇은 호출 묶음 + 인증값 자동 갱신 | 3 | 라이브러리 미착수 |
| `indie-kit-billing` | Play Billing v8 — 구독 / 1회성 / 평생 결제 | 4 | 라이브러리 미착수 |
| `indie-kit-auth` | 카카오 / 구글 / 애플 로그인 + 우리 서버 세션 발급 | 5 | 라이브러리 미착수 |

자세한 단계는 `PLAN.md`, 모듈별 변경 이력은 `CHANGELOG.md` 참고.

현재는 **2단계 IndieKitAds 출시 + 실기기 검증 완료** (`v0.2.7`). 4종 광고 (배너 / 전면 / 리워드 / Native — Native 는 안드로이드 선행) + 유럽 광고 동의창 (UMP) + AdMob native ad debug validator 단말 통과. 다음은 SolTi 통합 검증 또는 3단계 (Network) 진입.

## 사용 방법 (1단계 이후 적용)

`settings.gradle.kts` 의 `dependencyResolutionManagement.repositories` 에 JitPack 추가:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

앱 모듈 (`app/build.gradle.kts`) 의 dependencies 에 필요한 모듈만 추가:

```kotlin
implementation("com.github.junu-labs.indie-kit-android:indie-kit-analytics:v0.1.0")
implementation("com.github.junu-labs.indie-kit-android:indie-kit-ads:v0.2.7")
```

광고만 필요한 앱은 광고 모듈만 추가 → 나머지 외부 라이브러리 (Firebase, 카카오, 구글, OkHttp, Compose) 가 빌드에 안 끼게 한다.

### 통계 — indie-kit-analytics

```kotlin
import kr.co.junu.indiekit.analytics.IndieKitAnalytics
import kr.co.junu.indiekit.core.analyticsParams

// Application.onCreate 에서 한 번:
IndieKitAnalytics.configure(this)    // FirebaseApp.initializeApp() 까지 자동.

// 어디서든:
IndieKitAnalytics.logScreen("HomeScreen")
IndieKitAnalytics.log("timer_start", analyticsParams("mode" to "hiit", "duration" to 60))
IndieKitAnalytics.logSignUp(method = "google")
IndieKitAnalytics.logLogin(method = "kakao")
IndieKitAnalytics.logPurchase(productId = "pro_yearly", currency = "KRW", value = 12_000.0)
IndieKitAnalytics.setUserId("u_123")
IndieKitAnalytics.setUserProperty("plan_tier", "pro_yearly")
```

사용처 앱에 다음이 필요:
- `google-services.json` (Firebase Console 에서 받음, app 모듈에 둠)
- `app/build.gradle.kts` 의 plugins 에 `id("com.google.gms.google-services")` 한 줄 (라이브러리는 이 플러그인을 적용하지 않음 — 사용처 책임)

### 광고 — indie-kit-ads

```kotlin
import kr.co.junu.indiekit.ads.IndieKitAds
import kr.co.junu.indiekit.ads.AdUnitID
import kr.co.junu.indiekit.ads.BannerAdView
import kr.co.junu.indiekit.ads.NativeAdView

// Application.onCreate 에서 한 번:
IndieKitAds.configure(
    context = this,
    bannerAdUnitID       = AdUnitID(release = "ca-app-pub-..."),
    interstitialAdUnitID = AdUnitID(release = "ca-app-pub-..."),
    rewardedAdUnitID     = AdUnitID(release = "ca-app-pub-..."),
    nativeAdUnitID       = AdUnitID(release = "ca-app-pub-..."),
    requestConsent = true
)

// Compose 화면 안:
BannerAdView(modifier = Modifier.fillMaxWidth())   // 배너 (320×50)
NativeAdView(modifier = Modifier.fillMaxWidth())   // Native (라이브러리 기본 UI)

// 사용자 커스텀 Native 레이아웃이 필요하면:
NativeAdView(
    nativeAd = nativeAd,
    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)   // ① IntrinsicSize.Min 강제
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        NativeAdHeadlineView { Text(nativeAd.headline ?: "") }
        if (nativeAd.mediaContent != null) {                       // ② mediaContent null 체크
            NativeAdMediaView(
                modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 120.dp),  // ③ 높이 제한 (AdMob 정책 ≥120dp)
                scaleType = ImageView.ScaleType.CENTER_CROP
            )
        }
        nativeAd.callToAction?.let { cta ->
            NativeAdCallToActionView { NativeAdButton(text = cta) }
        }
    }
}

// 시점이 되면 (Activity 명시 필수 — iOS 의 자동 rootViewController 탐색은 안드로이드 미지원):
IndieKitAds.showInterstitial(activity) { /* 닫힘 후 */ }
IndieKitAds.showRewarded(activity) { reward ->
    if (reward != null) { /* 보상 지급 */ }
}
IndieKitAds.requestConsentForm(activity) { error -> /* EEA 동의창 다시 띄우기 */ }
```

`AndroidManifest.xml` 의 `com.google.android.gms.ads.APPLICATION_ID` meta-data 는 라이브러리가 Google 공식 테스트 app id 로 자동 채워 둠. 출시 직전 사용처 앱이 자기 매니페스트의 같은 키를 `tools:replace="android:value"` 로 실제 app id 로 덮어쓴다.

광고 이벤트 (적재 / 표시 / 닫힘 / 보상 / 클릭) 는 통계 모듈을 깐 앱이면 자동으로 Firebase Analytics 에도 흘러감 (`AnalyticsBus` 약한 연결).

#### Native 광고 — 사용자 커스텀 레이아웃 시 핵심 3가지

라이브러리가 제공하는 기본 UI (`NativeAdView(modifier)` 한 줄) 는 이미 검증 통과 패턴이 들어 있음. 직접 자기 레이아웃을 짤 땐 다음 3가지를 지켜야 AdMob native ad debug validator (테스트 광고 + debug 빌드 전용 도구) 가 "No implementation issues found" 로 통과:

1. `NativeAdView(nativeAd, modifier = ...)` 의 modifier 에 **`Modifier.fillMaxWidth().height(IntrinsicSize.Min)`** — `verticalScroll Column / Card / LazyColumn item` 등 unbounded height 환경에서도 자식 측정만큼만 차지하게.
2. `NativeAdMediaView` 호출은 **`if (nativeAd.mediaContent != null) { ... }`** 안에서만, **`sizeIn(maxHeight = 120.dp)`** 로 높이 제한 (정책 ≥120dp 만족).
3. `loadNativeAd` 가 라이브러리 안에서 이미 **`NativeAdOptions.ADCHOICES_TOP_RIGHT`** 를 적용해 둠 — 직접 `AdLoader.Builder` 를 짜는 경우엔 같은 옵션 명시.

이 3가지를 빠뜨리면 validator 가 "Advertiser assets outside native ad view ..." 라는 오인 메시지 (SDK 가 자산 측정 실패를 첫 자산 이름으로 일반화) 를 띄움. 단 풍선 자체는 debug 빌드 + 테스트 광고에만 뜨고 실제 출시본 (실 광고) 엔 안 뜨므로 정책 / 사용자 영향은 없음.

## 최소 환경

- Android 8.0 (API 26)
- Kotlin 2.3+
- AGP 9.1+
- JDK 17 (바이트코드 타겟)
- Gradle 9.3+

## 폴더 구조

```
indie-kit-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── README.md
├── CHANGELOG.md
├── PLAN.md
├── .github/workflows/ci.yml
├── indie-kit-core/                 # 외부 비공개 (5개 모듈이 공유하는 도구)
├── indie-kit-analytics/
├── indie-kit-ads/
├── indie-kit-network/
├── indie-kit-billing/
└── indie-kit-auth/
```

검증 앱은 라이브러리 저장소 밖 — `~/Developer/JunuLabs/Apps/SolTi/Android/`, `~/Developer/JunuLabs/Apps/TapCounter/Android/`.

## 손볼 때

`PLAN.md` 의 단계별 안내를 따른다. 각 단계는 산출 → 검증 앱에 붙여 검증 → `git tag` 순.

```bash
# 로컬 검증
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew build              # 6개 모듈 컴파일 + 린트
./gradlew test               # 6개 모듈 단위 테스트
./gradlew :indie-kit-ads:publishToMavenLocal   # 한 모듈을 ~/.m2 에 발행
```

### 검증 데모 (`indieKitDemo_Android`) 와 빨리 손발 맞추기 — composite build

매번 git push / 태그 / JitPack 빌드 대기 없이 라이브러리 코드 → 데모 빌드 즉시 반영. `Apps/IndieKitExample/indieKitDemo_Android/settings.gradle.kts` 의 includeBuild 블록 (주석 처리되어 있음) 을 켜고 `gradle/libs.versions.toml` 의 좌표를 `kr.co.junu` + v 접두사 없는 SemVer 로 바꾸면 됨. 다시 JitPack 출시본으로 검증할 땐 그 반대로.

## 버전 히스토리

마일스톤만. 그 사이 hotfix / 시행착오는 `CHANGELOG.md` 참고.

| 버전 | 날짜 | 무엇을 했나 |
|---|---|---|
| **`v0.2.7`** | 2026-05-12 | **2단계 IndieKitAds 실기기 검증 완료** — AdMob native ad validator "No implementation issues found" 통과. 라이브러리 기본 UI 에 TouchCart 출처 측정 패턴 3가지 (`IntrinsicSize.Min` / `mediaContent` null 체크 + `sizeIn(maxHeight = 120.dp)` / `NativeAdOptions.ADCHOICES_TOP_RIGHT`) 흡수. `NativeAdView.kt` 는 [Google 공식 compose_utils/NativeAdView.kt](https://github.com/googleads/googleads-mobile-android-examples/blob/main/kotlin/advanced/JetpackComposeDemo/app/src/main/java/com/google/android/gms/example/jetpackcomposedemo/formats/compose_utils/NativeAdView.kt) 와 한 글자 안 다른 상태로 유지, 라이브러리 추가물은 같은 패키지의 `IndieKitNativeAd.kt` 로 분리. 빌드 도구 AGP 9.1.1 / Gradle 9.3.1 통일. (v0.2.1 ~ v0.2.6 은 같은 풍선 풀려고 시도한 hotfix 6번, 모두 빗나감 — 검증 통과 패턴이 측정 쪽에 있다는 단서를 한 번에 잡지 못한 결과.) |
| `v0.2.0` | 2026-05-11 | 2단계 IndieKitAds 라이브러리 완성. AdMob 4종 광고 (배너 / 전면 / 리워드 / Native — Native 는 안드로이드 선행) + 유럽 광고 동의창 (UMP) + Compose 진입점 (`BannerAdView`, `NativeAdView`) + AnalyticsBus 자동 연결. |
| `v0.1.0` | 2026-05-11 | 1단계 IndieKitAnalytics — Firebase Analytics 한 줄 추상화 (`logScreen` / `log` / `logLogin` / `logSignUp` / `logPurchase` / `setUserId` / `setUserProperty`). AnalyticsBus 등록 통로 마련. |
| `v0.0.1` | 2026-05-11 | 0단계 부트스트랩 — Gradle 멀티 모듈 골격 6개 (Core + 외부 5개), JUnit 4 자리, JitPack / Maven Local 경로, CI (Ubuntu + JDK 17). |

## 함께 쓰는 자매 저장소

- `indie-kit-ios` — iOS 쪽 같은 역할 (Swift Package Manager). 단계 번호와 진입점 이름이 1:1 대칭.
- `indie-kit` — 13명 담당 시스템 (앱 만드는 흐름 자체).
