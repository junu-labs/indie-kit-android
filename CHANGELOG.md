# 바뀐 내용

[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식. SemVer 를 따른다.

자매 저장소: [`indie-kit-ios`](https://github.com/junu-labs/indie-kit-ios). iOS 와 단계 번호가 1:1 대칭이며, 같은 단계라도 데모 검증 시점이 다르면 태그 번호가 어긋날 수 있다.

## [Unreleased]

## [0.2.5] - 2026-05-12

### 고침

- `NativeAdView` 의 `Advertiser assets outside native ad view` validator 경고를 **Google 공식 Jetpack Compose 데모 (`DisplayNativeAdView`) 와 1:1 동일 구조** 로 정렬해 해결.
  - 출처: [googleads-mobile-android-examples / JetpackComposeDemo / NativeScreen.kt](https://github.com/googleads/googleads-mobile-android-examples/blob/698554091d61442dd157c5508c848974ee420d39/kotlin/advanced/JetpackComposeDemo/app/src/main/java/com/google/android/gms/example/jetpackcomposedemo/formats/NativeScreen.kt) — iOS 자매가 같은 출처 샘플로 검증 통과.
  - **`DefaultNativeAdContent` 에서 advertiser 자산을 표시하지 않음** — Google 공식 데모도 advertiser 를 등록 / 표시하지 않음. v0.2.1 ~ v0.2.4 에서 조건부 / 항상 / 빈 콘텐츠 어떤 변형으로 시도해도 validator boundary 검사가 가끔 실패해 경고가 떴음. 기본 UI 에서는 빼고, 직접 커스텀이 필요한 사용자는 `NativeAdView(nativeAd) { ... }` 안에서 `NativeAdAdvertiserView { Text(...) }` 를 자기 책임으로 사용.
  - **외부 `Box(modifier.padding(8.dp))` 복원** — v0.2.4 에서 root 가설 따라 제거했었지만 Google 공식 데모는 외부 Box 가 있음. Box → NativeAdView 의 AndroidView 구조 자체가 root 인지에는 영향 없음.
  - **`NativeAdMediaView` 의 `aspectRatio` 제거** — `Modifier.fillMaxWidth()` 만. Google 공식 데모와 동일. SDK 의 MediaView 가 자체적으로 aspectRatio 를 측정.
  - **`NativeAdMediaView` (primitive) 의 MediaView 에서 명시적 `layoutParams` 제거** — `MediaView(context)` 만. Google 공식 데모와 동일.

## [0.2.4] - 2026-05-12

### 고침

- `NativeAdView` validator 의 `Advertiser assets outside native ad view` 경고를 일으키는 **진짜 원인 두 가지** 를 잡음.
  진단 출처: [Google AdMob 공식 포럼 글타래](https://groups.google.com/g/google-admob-ads-sdk/c/6XzeFnEcCj0) — SDK 가 일반적 boundary 검사 실패를 "advertiser" 한 자산 메시지로 표시하는 케이스.
  - **`DefaultNativeAdContent` 의 외부 Box wrapping 제거** — NativeAdView 가 layout 의 root 가 되도록. 이전: `Box(modifier.padding(8.dp)) { NativeAdView(nativeAd) { ... } }`. 이후: `NativeAdView(nativeAd, modifier = modifier.padding(8.dp)) { ... }`.
  - **`NativeAdMediaView` 에 aspectRatio 명시** — `Modifier.fillMaxWidth()` 만 있으면 첫 layout pass 에 height 가 0 으로 측정되어 validator 가 "asset outside native ad view" 경고를 띄움. 응답의 `mediaContent.aspectRatio` 를 우선 사용, null 이면 16:9 fallback.
  - **`NativeAdMediaView` (primitive) 의 MediaView 에 layoutParams 명시** — `ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)`. View 시스템 기본 layoutParams 가 환경에 따라 0×0 으로 잡힐 수 있어 안전장치.

## [0.2.3] - 2026-05-11

### 고침

- `DefaultNativeAdContent` 에 `NativeAdAdvertiserView` 항상 컴포즈 (자산 null 일 땐 빈 콘텐츠).
  - v0.2.2 까지 Google 공식 sample 패턴 (`advertiser` 자산 미등록) 을 따랐지만, 안드로이드 AdMob SDK 25.1.0 native ad validator 가 응답에 advertiser 자산이 있을 때 "Advertiser assets outside native ad view" 경고를 띄움.
  - v0.2.1 의 `nativeAd.advertiser?.let { NativeAdAdvertiserView { ... } }` 조건부 컴포즈도 같은 경고를 띄움 — Compose 의 ?.let 분기로 view 가 가끔 미컴포즈 → 측정 실패로 추정.
  - 신규: `NativeAdAdvertiserView { nativeAd.advertiser?.let { Text(...) } }` — view 자체는 항상 등록, 자산만 조건부 표시. `NativeAdMediaView` 가 이미 쓰는 같은 패턴.

## [0.2.2] - 2026-05-11

### 고침

- `NativeAdView` 기본 UI (`DefaultNativeAdContent`) 를 Google 공식 Compose 데모 (`DisplayNativeAdView`) 와 100% 일치하게 다시 작성.
  - **icon 자산 등록 시점 변경** — 기존: `nativeAd.icon?.drawable?.toBitmap()?.let { bitmap -> NativeAdIconView { Image(bitmap) } }` (bitmap 까지 not null 이어야 NativeAdIconView 컴포즈). 신규: `nativeAd.icon?.let { icon -> NativeAdIconView { icon.drawable?.toBitmap()?.let { Image(bitmap) } } }` (icon 자산이 있으면 NativeAdIconView 는 항상 등록, bitmap 만 별개로 그림). icon 자산이 있는데 drawable 변환이 실패해도 자산 setter 는 등록된 상태가 boundary 검사 통과의 약속.
  - v0.2.1 에서 추가했던 `NativeAdAdvertiserView` 분기 제거 — Google 데모도 advertiser 자체를 표시 안 하지만 boundary 에러 안 띄움. 표시 / 미표시 결정은 사용자 커스텀 진입점에서.
  - `NativeAdAttribution` 의 text 를 명시 ("광고") — 기존 기본값 "Ad" 그대로 둘 수도 있었지만 한국어 라이브러리 정체성 살림.

## [0.2.1] - 2026-05-11

### 고침

- `NativeAdView` 기본 UI (`DefaultNativeAdContent`) 에 `NativeAdAdvertiserView` 추가.
  - AdMob 정책 검증이 native ad 응답의 `advertiser` 자산을 NativeAdView 안에 trackable 한 view 로 등록 안 됐다고 보고 "Advertiser assets outside native ad view, all asset boundaries must be inside the native ad view" 경고를 띄움. advertiser 자산을 표시 (또는 null 일 땐 미표시) 하는 줄을 추가해 boundary 검사 통과.

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
