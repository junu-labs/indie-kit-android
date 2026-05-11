# 바뀐 내용

[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식. SemVer 를 따른다.

자매 저장소: [`indie-kit-ios`](https://github.com/junu-labs/indie-kit-ios). iOS 와 단계 번호가 1:1 대칭이며, 같은 단계라도 데모 검증 시점이 다르면 태그 번호가 어긋날 수 있다.

## [Unreleased]

## [0.2.0] - 2026-05-11

### 추가

- 2단계 IndieKitAds — AdMob 4종 광고 (배너 / 전면 / 리워드 / Native) + 유럽 광고 동의창 (UMP).
  - `IndieKitAds.configure(context, bannerAdUnitID, interstitialAdUnitID, rewardedAdUnitID, nativeAdUnitID, requestConsent)` — iOS 자매와 같은 시그니처. context 만 안드로이드 추가.
  - `BannerAdView(modifier)` — Compose Composable, 표준 320×50 배너. AndroidView 안에 AdMob AdView wrapping. DisposableEffect 로 메모리 누수 방지.
  - `IndieKitAds.showInterstitial(activity, onDismiss)` / `showRewarded(activity, onResult)` — preload + show 패턴, 닫힘 후 자동 다음 광고 적재.
  - `IndieKitAds.requestConsentForm(activity, onComplete)` — UMP 동의창 수동 재호출.
  - **Native 광고 (안드로이드 선행)**:
    - `NativeAdView(modifier)` — 자동 적재 + 라이브러리 기본 UI (한 줄). Google 공식 Compose 데모 패턴 흡수.
    - `NativeAdView(nativeAd, modifier, content)` — 사용자 커스텀 레이아웃.
    - 자식 Composable 11개: `NativeAdHeadlineView`, `NativeAdBodyView`, `NativeAdMediaView`, `NativeAdIconView`, `NativeAdCallToActionView`, `NativeAdPriceView`, `NativeAdStarRatingView`, `NativeAdStoreView`, `NativeAdAdvertiserView`, `NativeAdChoicesView`, `NativeAdAttribution`, `NativeAdButton`.
  - `AdUnitID(debug, release)` data class + `AdMobTestUnitID` (4종 Google 공식 테스트 ID 상수) + `AdReward(amount, type)` data class.
  - 광고 적재 / 노출 / 닫힘 / 보상 / 클릭 이벤트는 `AnalyticsBus` 로 자동 흘림 — 통계 모듈 깐 앱이면 자동으로 Firebase 도착.
- 의존성 추가 (이 모듈에만): `play-services-ads:25.1.0` (api), `user-messaging-platform:3.2.0` (implementation), `compose-bom:2026.03.00` + `foundation` / `material3` / `ui` (api), `androidx.core:core-ktx:1.18.0` (Drawable.toBitmap 용).
- 단위 테스트 6개 추가 — placeholder 표식, 테스트 ID 상수 형식 / 유일성, AdUnitID 등호, AdReward 등호, configure 전 ready flags.
- 검증 데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 정비:
  - `DemoApplication` 에서 `IndieKitAnalytics.configure(this)` + `IndieKitAds.configure(this, requestConsent = true)`.
  - `MainActivity` 한 화면에 통계 4개 / 광고 4종 / Native / 화면 이동 시연 (iOS `IndieKitDemo_iOS/ContentView` 와 같은 섹션 구성).
  - `SecondScreen` LaunchedEffect 에서 `logScreen("SecondScreen")` 호출.
  - 데모 앱 mavenLocal() 로 라이브러리 끌어 씀. 사용자 승인 후 git tag + push → JitPack maven 으로 전환.

### 발견한 결정

- **Compose 의존성 = api** — `BannerAdView` / `NativeAdView` 가 사용처에서 직접 호출되는 Composable 이라, 사용처가 같은 Compose BOM 으로 자동 정렬되도록.
- **AdMob (play-services-ads) = api** — Native 커스텀 모드에서 사용처가 NativeAd 타입을 직접 만진다.
- **UMP = implementation** — UMP 타입은 라이브러리 안에서만 사용. 사용자는 IndieKitAds.requestConsentForm 만 호출.
- **Activity 매개변수 명시** — iOS 의 자동 rootViewController 탐색 (UIApplication.shared.windows) 대신 안드로이드는 명시 필수. `showInterstitial(activity, ...)` 시그니처에 Activity 강제.
- **AdMob app id 매니페스트 자동 merge** — 라이브러리 매니페스트가 Google 공식 테스트 app id 로 채워 둠. 사용처 앱은 출시 직전 `tools:replace="android:value"` 로 자기 실제 app id 로 덮어쓴다.
- **권한 매니페스트 명시** — `INTERNET / ACCESS_NETWORK_STATE / com.google.android.gms.permission.AD_ID` 세 줄을 indie-kit-ads 매니페스트에 직접 적음.

### 검증 (모두 그린)

- `./gradlew build` — 6개 모듈 컴파일 + 린트 통과
- `./gradlew test` — 19개 테스트 통과 (Core 6 + Analytics 4 + Ads 6 + 다른 3개 모듈 PlaceholderTest 1개씩)
- `./gradlew publishToMavenLocal` — 6개 모듈 모두 `~/.m2/repository/co/junu/indie-kit-{name}/0.2.0/` 에 발행
- 데모 앱 (`indieKitDemo_Android`) `./gradlew assembleDebug` → `app-debug.apk` 생성. 5개 모듈 의존성 mavenLocal 에서 정확히 풀림.

## [0.1.0] - 2026-05-11

### 추가

- 1단계 IndieKitAnalytics — Firebase Analytics 한 줄 추상화 + 약한 연결 통로 자동 등록.
  - `IndieKitAnalytics.configure(application, autoInitFirebase = true)` — Firebase 초기화 자동, AnalyticsBus 등록.
  - `logScreen / log / logSignUp / logLogin / logPurchase / setUserId / setUserProperty / setDebugTag` — iOS 자매 진입점과 1:1 대칭.
  - 의존성: `firebase-bom` 34.10.0 + `firebase-analytics`. google-services 플러그인은 사용처 (앱) 가 적용.
- 1단계 IndieKitCore 채움 — 5개 모듈이 같이 쓰는 도구 묶음.
  - `IKLogger` — `kr.co.junu.indiekit` subsystem + 카테고리 6개 (analytics / ads / network / billing / auth / core), `android.util.Log` 기반.
  - `IndieKitConfigurationError` — sealed class (NotConfigured / MissingRequiredValue / AlreadyConfigured), 한국어 메시지.
  - `AnalyticsBus` — 광고 → 통계 약한 연결 통로. ReentrantLock 보호, 등록 안 된 상태에서도 record 호출 안전.
  - `AnalyticsValue` — sealed class (StringValue / IntValue / DoubleValue / BoolValue) + `analyticsParams` 헬퍼 (iOS 의 ExpressibleByLiteral 대응).
  - `ConcurrencyHelpers` — 빈 자리, 단계가 진행되며 추출 후보 발견 시 채움.
- 단위 테스트 10개 추가 — Core 6개 (subsystem 통일, ConfigurationError 메시지, AnalyticsBus 흐름, record 무등록 안전, analyticsParams 변환, 타입 거부) + Analytics 4개 (placeholder 표식, recorder 등록, 이벤트 크래시 없음, 중복 등록 안전).

### 발견한 결정

- **Javadoc jar 끔** — AGP 9.0 의 기본 javaDoc 작업 (Dokka 기반) 이 Kotlin 2.3 metadata 를 못 읽어 Firebase 같은 최신 라이브러리 의존 모듈에서 빌드 실패. 6개 모듈 모두 vanniktech `configure(AndroidSingleVariantLibrary(publishJavadocJar = false))` 로 끔. JitPack 경로엔 javadoc jar 불필요. Maven Central 이전 시 Dokka 직접 설정으로 다시 켠다.
- **`testOptions.unitTests.isReturnDefaultValues = true`** — 6개 모듈 일괄 적용. 단위 테스트가 Robolectric 없이 `android.util.Log` 등 정적 호출을 no-op 으로 통과.
- **Firebase Analytics 권한 매니페스트 명시** — `INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK` 세 줄을 indie-kit-analytics 매니페스트에 직접 적음. 사용처 앱 매니페스트와 자동 merge 되어 중복 영향 0. lint 의 MissingPermission 경고를 라이브러리 단계에서 막기 위함.

### 검증 (모두 그린)

- `./gradlew build` — 6개 모듈 컴파일 + 린트 통과
- `./gradlew test` — 14개 테스트 통과 (Core 6 + Analytics 4 + 4개 모듈의 PlaceholderTest 1개씩)
- `./gradlew publishToMavenLocal` — 6개 모듈 모두 `~/.m2/repository/co/junu/indie-kit-{name}/0.1.0/` 에 `.aar / .pom / -sources.jar / .module` 발행

## [0.0.1] - 2026-05-11

### 추가

- 0단계 부트스트랩: Gradle 멀티 모듈 골격.
  - 모듈 6개 자리 — `indie-kit-core` (내부 비공개) + `indie-kit-analytics` / `-ads` / `-network` / `-billing` / `-auth` (외부 노출).
  - 모듈마다 `Placeholder.kt` (`isPlaceholder = false` 표식) + `PlaceholderTest.kt` 1개씩.
- 도구 버전: AGP 9.0.1 / Kotlin 2.3.20 / Gradle 9.1.0 / JDK 17 / minSdk 26 / compileSdk 36 (SolTi 와 동일).
- vanniktech maven-publish 플러그인 (JitPack / Maven Central / Maven Local 양쪽 호환).
- CI: GitHub Actions, ubuntu-latest + JDK 17 + `./gradlew build` + `./gradlew test`.
- 외부 의존성 0개 (junit 만). 단계마다 그 모듈의 `build.gradle.kts` 에 의존성 추가.

### 다음 단계

- 1단계 IndieKitAnalytics — Firebase Analytics 한 줄 추상화 + 약한 연결 통로 (`AnalyticsBus`).
- 2단계 IndieKitAds — 배너 / 전면 / 리워드 + Compose `BannerAdView` + AdMob.
- 3단계 IndieKitNetwork — OkHttp 위 얇은 래퍼 + 401 자동 토큰 갱신.
- 4단계 IndieKitBilling — Play Billing v8 + 구독 / 비소진형 + 자동 acknowledge.
- 5단계 IndieKitAuth — Google + Kakao + Apple (Custom Tabs OAuth) + 우리 서버 세션 발급.
