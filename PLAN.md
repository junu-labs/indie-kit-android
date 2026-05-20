# indie-kit-android — 공용 라이브러리 5개 만들기

> **상태 (2026-05-20):**
> - 0단계 부트스트랩 — 완료 (`v0.0.1`)
> - 1단계 IndieKitAnalytics — 라이브러리 + 데모 검증 완료 (`v0.1.0`)
> - 2단계 IndieKitAds — 라이브러리 + 데모 검증 완료, Native 패치 반복 (`v0.2.7` 까지)
> - 3단계 IndieKitNetwork — 라이브러리 본체 + 데모 검증 완료 (`v0.3.0`)
> - 4단계 IndieKitBilling — 라이브러리 본체 + 데모 검증 완료 (`v0.4.0`)
> - 5단계 IndieKitAuth — 라이브러리 본체 + 데모 신원값 수신 검증 완료 (`v0.5.0-rc1`) — 서버 세션 발급 검증 대기
>
> **저장소:** `junu-labs/indie-kit-android` (Gradle 멀티 모듈 + JitPack)
> **자매 저장소:** `junu-labs/indie-kit-ios` — 같은 단계 / 같은 진입점 이름.
> **자세한 변경 이력:** `CHANGELOG.md`

## 1. 왜 이걸 하는가

지금 안드로이드 인디 앱마다 광고 / 로그인 / 결제 / 통계 / 네트워크 코드를 따로 짜고 있다. 새 앱을 시작할 때마다 같은 의존성 등록 / 키 주입 / 보일러플레이트를 반복한다. 한 번 잘 만들어 두고 Gradle 한 줄로 끌어다 쓰는 공용 묶음이 필요하다.

iOS 자매 라이브러리 (`indie-kit-ios`) 와 같은 5개 모듈을 같은 진입점 이름으로 제공한다. 두 폰에서 동작이 같아 보이게 하면 유지비가 절반.

## 2. 큰 그림

저장소 1개, Gradle 멀티 모듈로 **모듈 5개를 각각 따로 끌어 쓸 수 있게** 분리한다. 광고만 필요한 앱은 광고 모듈만 끌어 → 나머지 외부 라이브러리 (Firebase, 카카오, 구글, OkHttp 등) 가 빌드에 안 들어오게 한다.

```
implementation("com.github.junu-labs.indie-kit-android:indie-kit-analytics:vX.Y.Z")  → Firebase Analytics
implementation("com.github.junu-labs.indie-kit-android:indie-kit-ads:vX.Y.Z")        → Google Mobile Ads + 광고 동의창
implementation("com.github.junu-labs.indie-kit-android:indie-kit-network:vX.Y.Z")    → OkHttp 위 얇은 호출 묶음 + 인증값 자동 갱신
implementation("com.github.junu-labs.indie-kit-android:indie-kit-billing:vX.Y.Z")    → Play Billing v8 (구독 + 비소진형)
implementation("com.github.junu-labs.indie-kit-android:indie-kit-auth:vX.Y.Z")       → 카카오 / 구글 / 애플 + 우리 서버 세션 발급
```

추가로 외부엔 안 보이는 내부 묶음 `indie-kit-core` 1개. 5개 모듈이 같이 쓰는 로깅 / 에러 / 약한 연결 통로가 들어간다 (`api(project(":indie-kit-core"))` 로 자동 노출).

| 항목 | 결정 |
|---|---|
| 최소 안드로이드 버전 | API 26 (Android 8.0) |
| compileSdk / targetSdk | 36 |
| Kotlin | 2.3.20 |
| AGP | 9.0.1 |
| Gradle | 9.1.0 |
| JDK | 17 (바이트코드 타겟) |
| 모듈 한 묶음에 한 발행 좌표 | 그렇다 (안 쓰는 외부 라이브러리는 빌드에 안 들어옴) |
| 통합 발행 좌표 | 없음 (필요한 모듈만 개별 import) |
| 자매 저장소 | `indie-kit-ios` (Swift Package Manager) — 단계 번호와 진입점 이름 1:1 대칭 |

## 3. 사용자가 정한 갈림길

| 갈림길 | 정해진 답 | 의미 |
|---|---|---|
| 모듈 범위 | iOS 와 동일한 5개 | 통계 + 광고 + 네트워크 + 결제 + 로그인. 0단계 부트스트랩에서 6개 모듈 (Core 포함) 자리를 한꺼번에 잡는다. |
| 첫 시작 지점 | 0단계 부트스트랩부터 | Gradle 멀티 모듈 골격 + Placeholder + CI 먼저. 실제 모듈 코드는 1단계부터 한 모듈씩. |
| 의존성 주입 (DI) | 안 씀 (object 싱글톤) | iOS 의 `IndieKitAds.shared` / `IndieKitAuth.shared` 와 같은 패턴. Hilt 를 강제하지 않아 사용처가 자유. Hilt 쓰는 앱은 `@Provides` 한 줄로 감싼다. |
| 네트워크 범위 | 인증값 자동 갱신까지 | 401 응답이 오면 인증값 자동 재발급 → 같은 요청 한 번 더 시도. 토큰 공급자 + 갱신 람다 두 개를 받는다. (iOS 와 동일) |
| 로그인 책임 | 우리 서버 세션 발급까지 | 카카오 / 구글 / 애플에서 받은 신원 확인값을 우리 서버로 던져 세션값까지 받아온다. 백엔드 끝점 형식이 앱마다 다른 건 어댑터로 풀어 준다. |
| 모듈 간 자동 연결 | 약하게 자동 연결 | 광고 모듈은 통계 모듈을 직접 모른다. `indie-kit-core` 안의 통로 (`AnalyticsBus`) 만 안다. 통계 모듈을 깐 앱이면 광고 이벤트가 자동으로 통계로 흘러간다. |
| 1차 배포 경로 | JitPack | `git tag` 한 번이면 `com.github.junu-labs.indie-kit-android:{모듈}:{버전}` 으로 받아 쓸 수 있다. Maven Central 은 1단계 검증 이후 별도 결정. |

## 4. 모듈 — 무엇을 해 주는가

### IndieKitAnalytics (1단계)

해 주는 것: Firebase Analytics 의 시작 호출, 화면 이동 / 사용자 속성 / 일반 이벤트 기록을 한 줄로.

받는 설정값: 없음. `google-services.json` 은 앱 모듈에 두면 자동.

```kotlin
IndieKitAnalytics.configure(application)
IndieKitAnalytics.logScreen("HomeScreen")
IndieKitAnalytics.log("timer_start", mapOf("mode" to "hiit"))
IndieKitAnalytics.setUserId("u_123")
```

`AnalyticsBus.register(...)` 가 자동으로 호출되어 광고 모듈이 보내는 이벤트도 같은 통로로 흐른다.

### IndieKitAds (2단계)

해 주는 것: 배너, 전면, 리워드 광고 + 유럽 광고 동의창 (UMP).

받는 설정값: 광고 ID 3개. 미주입 시 구글 공식 테스트 ID 자동.

```kotlin
IndieKitAds.configure(
    context = this,
    bannerAdUnitID       = AdUnitID(release = "ca-app-pub-..."),
    interstitialAdUnitID = AdUnitID(release = "ca-app-pub-..."),
    rewardedAdUnitID     = AdUnitID(release = "ca-app-pub-..."),
    requestConsent = true
)

// Compose 안:
BannerAdView(modifier = Modifier.fillMaxWidth())

// 시점이 되면:
IndieKitAds.showInterstitial(activity) { /* 닫힘 후 */ }
IndieKitAds.showRewarded(activity) { reward -> if (reward != null) { /* 보상 지급 */ } }
```

`AdUnitID` 가 `debug` / `release` 를 분리해 받아서, 앱 안의 build type 분기를 라이브러리가 대신 한다. Pro 사용자 광고 숨김은 앱이 분기.

`AndroidManifest.xml` 의 `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" .../>` 는 Google 공식 테스트 app ID (`ca-app-pub-3940256099942544~3347511713`) 를 기본값으로. 사용자가 출시 직전 실제 app ID 로 교체.

iOS 패턴 1:1: `IndieKitAds.BannerView()` (SwiftUI) ↔ `BannerAdView()` (Compose).

### IndieKitNetwork (3단계) — 인증값 자동 갱신 포함

해 주는 것: OkHttp 위 얇은 호출 묶음 + 401 응답 시 인증값 자동 재발급 흐름.

```kotlin
val net = IndieKitNetwork(
    baseURL = "https://api.example.com",
    tokenProvider  = { session.accessToken },
    tokenRefresher = { session.refresh() }
)

val info: AppInfo = net.get("/appInfo")
val user: UserDTO = net.post("/users", body = NewUser(...))
net.delete("/items/42")
```

401 흐름: 응답이 401 이면 `tokenRefresher` 한 번 호출 → 같은 요청 한 번 더 시도 → 또 401 이면 예외 던짐. iOS `IndieKitNetwork` 와 동일 흐름.

### IndieKitBilling (4단계)

해 주는 것: Google Play Billing v8 위에서 자동 갱신 구독, 비소진형 1회성 결제, "평생 사용" 항구 결제를 같은 그릇으로.

지원 결제 종류 (iOS 와 1:1 대칭):
- 자동 갱신 구독 (Play Billing `SUBS`) — 월간 / 연간 Pro
- 1회성 비소진형 (`INAPP` non-consumable, `acknowledgePurchase` 호출 + `consumePurchase` 미호출) — "광고 영구 제거"

소진형 (consumable) 미지원 (iOS 와 같은 사유).

```kotlin
enum class ProductType {
    AUTO_RENEWABLE_SUBSCRIPTION,
    NON_CONSUMABLE
}

data class ProductDescriptor(val id: String, val type: ProductType)

object IndieKitBilling {
    fun configure(context: Context, products: List<ProductDescriptor>, listener: BillingListener?)
    suspend fun owns(productID: String): Boolean
    suspend fun hasActiveSubscription(): Boolean
    suspend fun purchase(activity: Activity, productID: String): PurchaseResult
    suspend fun restore()
    suspend fun products(): List<ProductDetails>
}
```

내부는 Google Play Billing v8 wrapper. SolTi 의 `BillingManager.kt` 추출. Google Play 정책상 구매 후 3일 내 `acknowledgePurchase()` 미호출 시 자동 환불 — 라이브러리가 `purchase()` 성공 직후 자동 호출. 앱은 신경 안 써도 됨.

### IndieKitAuth (5단계) — 우리 서버 세션 발급까지

해 주는 것: 애플 / 구글 / 카카오 로그인 → 우리 서버에 신원 확인값 보내 세션값 받아오기.

지원 로그인: 구글 + 애플 + 카카오.

```kotlin
enum class AuthProvider { GOOGLE, APPLE, KAKAO }

data class AuthUser(
    val id: String,
    val provider: AuthProvider,
    val email: String?,
    val displayName: String?,
    val idToken: String?
)

object IndieKitAuth {
    fun configure(
        context: Context,
        googleServerClientID: String? = null,
        kakaoNativeAppKey: String? = null,
        appleServiceID: String? = null,        // Apple Developer Console 의 Service ID
        appleRedirectURI: String? = null,
        backend: SessionExchangeAdapter? = null
    )
    suspend fun signIn(activity: Activity, provider: AuthProvider): AuthUser
    suspend fun signOut()
    val currentUser: AuthUser?
    val accessToken: String?
}
```

**카카오 셋업 (앱 측):**
- 의존성: `com.kakao.sdk:v2-user:2.+` (라이브러리 build.gradle.kts 가 자동으로 끌어옴)
- `AndroidManifest.xml` 에 `<meta-data android:name="com.kakao.sdk.AppKey" .../>` + Kakao Login Activity intent-filter (`kakao{appkey}://oauth`) 추가
- 카카오 디벨로퍼 콘솔 (https://developers.kakao.com) 에서 앱 등록 + 패키지명 + key hash (디버그 / 릴리즈) 등록 필요

**Apple Sign-In on Android:** 네이티브 SDK 없음 → OAuth 2.0 + Custom Tabs 로 직접 구현. iOS 네이티브 (`ASAuthorizationAppleIDProvider`) 보다 복잡. 5단계 진입 시 라이브러리 선택지 (예: `com.willowtreeapps:signinwithapplebutton-android`) 재조사 필요.

우리 서버 세션을 네트워크 모듈에 자동 연결: `IndieKitNetwork` 의 `tokenProvider` 자리에 `IndieKitAuth.accessToken` 을 넣으면 끝.

## 5. 단계 — 5번 + 부트스트랩 1번

각 단계 끝에 `git tag` 찍고, 한 개 앱에 붙여 동작 확인.

| 단계 | 산출 | 검증 대상 | 태그 |
|---|---|---|---|
| 0 | 부트스트랩 (Gradle 멀티 모듈 골격) | `./gradlew build test` 통과 | v0.0.1 |
| 1 | IndieKitAnalytics | SolTi (Android) | v0.1.0 |
| 2 | IndieKitAds | SolTi (Android) | v0.2.0 |
| 3 | IndieKitNetwork | TapCounter (Android, 신규) | v0.3.0 |
| 4 | IndieKitBilling | SolTi (Android) | v0.4.0 |
| 5 | IndieKitAuth | 신규 검증 앱 | v0.5.0 |
| 후속 | IndieKitDemo (Compose 한 화면 전 모듈 시연) + v1.0.0 | — | v1.0.0 |

## 6. 단계별 결과 / 발견

### 0단계 — 부트스트랩 (완료, `v0.0.1`)

만든 파일:
- `settings.gradle.kts` (6개 모듈 등록 + 외부 저장소 정책)
- `build.gradle.kts` (root, 플러그인 apply false)
- `gradle.properties` (Maven 좌표 공통, AndroidX, AGP 9.0 호환 설정 두 줄)
- `gradle/libs.versions.toml` (버전 카탈로그 — junit + 빌드 도구 3개만)
- `.gitignore`, `.github/workflows/ci.yml`, `CHANGELOG.md`
- 6개 모듈 각자 — `build.gradle.kts`, `AndroidManifest.xml`, `Placeholder.kt`, `PlaceholderTest.kt`

확인 (모두 그린):
- `./gradlew build` → 6개 모두 컴파일 + 린트 통과
- `./gradlew test` → 6개 PlaceholderTest 통과
- `./gradlew :indie-kit-ads:publishToMavenLocal` → 산출물 생성

발견한 결정:
- `android.builtInKotlin=false` + `android.newDsl=false` — AGP 9.0 의 새 기본값 (내장 Kotlin / 새 DSL) 이 아직 maven-publish 등 외부 플러그인과 친하지 않아, SolTi 와 같은 명시 경로를 쓴다. AGP 10 진입 시 일괄 마이그레이션.
- vanniktech maven-publish 가 이미 `singleVariant("release") { withSourcesJar() }` 를 자동 호출 — 모듈 build.gradle.kts 에서 같은 블록을 또 적으면 충돌. 이 블록은 두지 않는다.
- `kotlin { jvmToolchain(17) }` 대신 `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`. Foojay 자동 다운로드가 Gradle 9.1.0 과 호환이 깨져 있어 (`JvmVendorSpec.IBM_SEMERU` 부재) 도구 체인 강제는 0단계엔 쓰지 않는다.

### 2단계 — IndieKitAds (완료, `v0.2.0`)

채운 파일 (`indie-kit-ads/src/main/java/co/junu/indiekit/ads/`):
- `AdUnitID.kt` — `AdUnitID(debug, release)` data class + `AdMobTestUnitID` (Google 공식 테스트 ID 4종) + `AdReward(amount, type)` data class.
- `IndieKitAds.kt` — object 진입점. `configure(context, banner/interstitial/rewarded/nativeAdUnitID, requestConsent)` + `showInterstitial(activity, onDismiss)` + `showRewarded(activity, onResult)` + `requestConsentForm(activity, onComplete)` + `resolved*AdUnitID(context)` + ready flags. iOS 자매와 1:1 (Native 자리 포함).
- `BannerAdView.kt` — Compose Composable. AndroidView 안에 AdMob AdView wrapping + DisposableEffect (메모리 누수 방지).
- `InterstitialAdLoader.kt` / `RewardedAdLoader.kt` — internal object, preload + show + FullScreenContentCallback. 닫힘 후 자동 다음 광고 적재.
- `UMPCoordinator.kt` — internal object, requestConsentInfoUpdate + presentForm. ActivityLifecycleCallbacks 로 첫 Activity 잡히는 시점에 자동 동의창 (Application.onCreate 시점 호출 대응).
- `NativeAdView.kt` — **안드로이드 선행** (iOS 자매도 동시 진행 중). 두 가지 진입점:
  - `NativeAdView(modifier)` — 자동 적재 + 라이브러리 기본 UI.
  - `NativeAdView(nativeAd, modifier, content)` — 사용자 커스텀 레이아웃.
  - 자식 Composable 11개 (`NativeAdHeadlineView` 등). Google 공식 Compose 데모 패턴 (CompositionLocal, AndroidView + ComposeView 중첩) 흡수.

지운 파일:
- `indie-kit-ads/...Placeholder.kt` + `PlaceholderTest.kt`.

검증 데모 앱 정비 (`Apps/IndieKitExample/indieKitDemo_Android/`):
- `DemoApplication.kt` — `IndieKitAnalytics.configure(this)` + `IndieKitAds.configure(this, requestConsent = true)`.
- `MainActivity.kt` — iOS `IndieKitDemo_iOS/ContentView` 와 같은 섹션 (통계 4개 / 광고 4종 / Native / 화면 이동) + `SecondScreen` LaunchedEffect 에서 `logScreen("SecondScreen")`.
- `AndroidManifest.xml` — Application 등록 + AdMob 테스트 app id meta-data (사용자가 출시 직전 `tools:replace` 로 덮어씀).
- `app/build.gradle.kts` — JDK 11 → 17, minSdk 24 → 26, Kotlin 2.2.10 → 2.3.20, Compose BOM 2024.09 → 2026.03 — 라이브러리와 정렬.
- `settings.gradle.kts` — `mavenLocal()` 추가. JitPack maven 은 git tag + push 후 주석 해제.

확인 (모두 그린):
- `./gradlew build` (라이브러리) — 6개 모듈 컴파일 + 린트 통과
- `./gradlew test` (라이브러리) — 19개 테스트 통과 (Core 6 + Analytics 4 + Ads 6 + 다른 3개 모듈 1개씩)
- `./gradlew publishToMavenLocal` (라이브러리) — 6개 모두 `kr.co.junu:indie-kit-*:0.2.0` 발행
- `./gradlew assembleDebug` (데모 앱) — `app-debug.apk` 생성, 5개 모듈 의존성 mavenLocal 에서 정확히 풀림

발견한 결정:
- **Compose 의존성 = api** — `BannerAdView` / `NativeAdView` 가 사용처에서 직접 호출되는 Composable 이라, 사용처가 같은 Compose BOM 으로 자동 정렬되도록.
- **AdMob (play-services-ads) = api** — Native 커스텀 모드에서 사용처가 NativeAd 타입을 직접 만진다.
- **UMP = implementation** — UMP 타입은 라이브러리 안에서만 사용. 사용자는 IndieKitAds.requestConsentForm 만 호출.
- **Activity 매개변수 명시** — iOS 의 자동 rootViewController 탐색 (UIApplication.shared.windows) 대신 안드로이드는 명시 필수. `showInterstitial(activity, ...)` 시그니처에 Activity 강제.
- **AdMob app id 매니페스트 자동 merge** — 라이브러리 매니페스트가 Google 공식 테스트 app id 로 채워 둠. 사용처 앱은 출시 직전 `tools:replace="android:value"` 로 자기 실제 app id 로 덮어쓴다.
- **Native 안드로이드 선행** — iOS 자매 (`indie-kit-ios`) 도 동시 진행 중. 두 폰 모두 Native 까지 끝나면 1:1 대칭 회복.
- **데모 앱 `LocalActivity` 는 `androidx.activity.compose.LocalActivity`** — `androidx.compose.ui.platform.LocalActivity` 가 아님 (1.10+ 부터 activity-compose 안에).

### 1단계 — IndieKitAnalytics (완료, `v0.1.0`)

채운 파일:
- `indie-kit-core/src/main/java/co/junu/indiekit/core/`
  - `IKLogger.kt` — `kr.co.junu.indiekit` subsystem + 카테고리 6개. Logcat tag = `"IndieKit.{name}"` 형태로 라이브러리 전체 로그 한 단어 필터.
  - `Errors.kt` — `IndieKitConfigurationError` sealed class (NotConfigured / MissingRequiredValue / AlreadyConfigured) + 한국어 메시지.
  - `AnalyticsBus.kt` — 약한 연결 통로. `AnalyticsValue` sealed class + `analyticsParams` 헬퍼 + `AnalyticsRecorder` 인터페이스 + `AnalyticsBus` object 가 한 파일에.
  - `ConcurrencyHelpers.kt` — 빈 자리 (iOS 자매와 같이).
- `indie-kit-core/src/test/java/co/junu/indiekit/core/IndieKitCoreTests.kt` — 6개 테스트.
- `indie-kit-analytics/src/main/java/co/junu/indiekit/analytics/`
  - `IndieKitAnalytics.kt` — object 진입점. `configure(application, autoInitFirebase)` / `logScreen` / `log` / `logSignUp` / `logLogin` / `logPurchase` / `setUserId` / `setUserProperty` / `setDebugTag`.
  - `FirebaseAnalyticsRecorder.kt` — `AnalyticsBus` 에 등록되는 어댑터. iOS 자매와 같은 역할.
- `indie-kit-analytics/src/test/java/co/junu/indiekit/analytics/IndieKitAnalyticsTests.kt` — 4개 테스트.
- `indie-kit-analytics/src/main/AndroidManifest.xml` — Firebase 가 요구하는 권한 (`INTERNET / ACCESS_NETWORK_STATE / WAKE_LOCK`) 명시.

지운 파일:
- `indie-kit-core/...Placeholder.kt` + `PlaceholderTest.kt` (실 구현이 들어왔으므로 표식 불필요)
- `indie-kit-analytics/...Placeholder.kt` + `PlaceholderTest.kt`

확인 (모두 그린):
- `./gradlew build` → 6개 컴파일 + 린트 통과
- `./gradlew test` → 14개 테스트 통과 (Core 6 + Analytics 4 + Ads / Network / Billing / Auth Placeholder 1개씩)
- `./gradlew publishToMavenLocal` → 6개 모듈 모두 `~/.m2/repository/co/junu/indie-kit-{name}/0.1.0/` 에 발행. Analytics POM 에 `firebase-bom` (dependencyManagement) + `firebase-analytics` (runtime) + `indie-kit-core` (compile) 정확히 들어감.

발견한 결정:
- **Javadoc jar 끔 (6개 모두)** — AGP 9.0 의 기본 javaDoc 작업 (Dokka 기반) 이 Kotlin 2.3 metadata 를 못 읽어 Firebase / play-services-measurement-api 가 의존되는 모듈에서 빌드 실패. 모든 모듈에 `mavenPublishing.configure(AndroidSingleVariantLibrary(publishJavadocJar = false))`. JitPack 경로엔 javadoc jar 불필요. Maven Central 이전 시 Dokka 직접 설정으로 다시 켠다.
- **`testOptions.unitTests.isReturnDefaultValues = true` (6개 모두)** — `android.util.Log.i / d / w / e` 같은 정적 호출이 단위 테스트에서 throw 하지 않고 no-op 반환. Robolectric 추가 없이 가벼운 JVM 단위 테스트로 끝.
- **Firebase 의존성은 `implementation`** — 사용처 앱이 자기 BOM (보통 더 최신) 을 자유롭게 잡을 수 있도록 강제하지 않음. Firebase Analytics 는 transitive 로 runtime classpath 에 있지만 사용처 코드에 직접 노출되지 않음.
- **권한 매니페스트 명시** — Firebase Analytics SDK 매니페스트가 같은 권한을 선언해도 lint 가 라이브러리 단계에서 미리 잡지 못함. 우리 매니페스트에 직접 적어 두면 매니페스트 merge 후 중복 영향 0.

## 7. 외부 의존성 추가 시점

| 단계 | 추가될 외부 의존성 |
|---|---|
| 1 (Analytics) | `firebase-bom` + `firebase-analytics` |
| 2 (Ads) | `compose-bom` (BannerAdView 가 Compose) + `play-services-ads` + `user-messaging-platform` |
| 3 (Network) | `okhttp` (또는 `ktor-client-android` — 단계 진입 시 결정) + `kotlinx-serialization-json` |
| 4 (Billing) | `billing-ktx` (Play Billing v8) |
| 5 (Auth) | `googleid` (Credential Manager 경로) + `kakao-sdk:v2-user` + Custom Tabs (`androidx.browser:browser`) |

각 단계의 의존성은 **그 모듈의 build.gradle.kts 에만** 추가. 다른 모듈은 끌리지 않는다.

## 8. 저장소 폴더 모양 (5단계 완료 시점)

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

검증 앱은 라이브러리 저장소 밖 — `~/Developer/JunuLabs/Apps/SolTi/Android/`, `~/Developer/JunuLabs/Apps/TapCounter/Android/`, 그리고 5단계 검증용 신규 앱.

## 9. Maven 배포

**1차 (간단): JitPack**
- 사용자: `implementation("com.github.junu-labs.indie-kit-android:indie-kit-ads:v0.2.0")`
- repo 에 `git tag v0.X.Y` push 하면 JitPack 이 자동 빌드 + 호스팅.
- 단점: 첫 다운로드 느림, 빌드 실패 시 사용자에게 노출.

**2차 (안정): Maven Central** (`kr.co.junu:indie-kit-ads:0.2.0`)
- 셋업 복잡 (gpg 서명, sonatype 계정).
- 표준 배포처라 사용자 신뢰도 높음.

**결정: 0~2단계 는 JitPack** — 빠르게 검증. 3단계부터 Maven Central 이전 검토.

## 10. 버저닝

- iOS 와 동일 SemVer (`v0.X.Y`).
- iOS / Android 버전은 **독립적** — 같은 기능 추가했어도 양쪽 따로 태그.
- 0단계 동시 릴리즈 목표: 양쪽 다 `v0.0.1` (안드로이드 — 2026-05-11), 같은 단계 번호로 진행.

## 11. CI

`.github/workflows/ci.yml`:
- ubuntu-latest + JDK 17 (`actions/setup-java@v4`, temurin)
- `actions/checkout@v4` + `gradle/actions/setup-gradle@v4`
- `./gradlew build` → `./gradlew test`
- 1단계 이후 모듈별 instrumented test 는 SKIP (런너에 에뮬레이터 띄우는 비용 큼)

## 12. SolTi / TapCounter 마이그레이션 (각 단계의 검증)

각 단계의 검증 앱이 라이브러리를 끌어다 쓰며 평소처럼 동작하는지 확인하는 절차.

1. `indie-kit-android` repo 에서 그 단계 모듈 완성 + `git tag v0.X.0`
2. 검증 앱의 `build.gradle.kts` 에 `mavenCentral()` 옆에 `maven { url = uri("https://jitpack.io") }` 추가 (앱 settings.gradle.kts dependencyResolutionManagement)
3. `implementation("com.github.junu-labs.indie-kit-android:indie-kit-{name}:v0.X.0")` 추가
4. 검증 앱 안의 같은 기능 코드를 라이브러리 진입점 (`IndieKitAds.*` 등) 으로 교체
5. 에뮬레이터에서 동작 확인 (광고 로딩, 통계 이벤트 도착, 구독 구매 / 복원, 로그인 흐름 등)

JitPack push 전 사용자 승인 필수.

## 13. 잔가지 결정 — 단계마다 시작 직전에

1. **Firebase 시작 호출 자동 / 수동** — 추천: 자동 (`autoConfigureFirebase = false` 로 끌 수 있게)
2. **출시 빌드인데 광고 ID 가 비어 있을 때** — 추천: 컴파일 경고 + 런타임 logcat 경고
3. **결제 영수증 서버 검증** — 추천: 람다 자리만 미리 노출
4. **로그인 후 우리 서버 세션 어댑터 형식** — 5단계 직전에 다시 다듬는다
5. **검증 앱 비밀값 관리 방식** — 5단계 이후에 결정
6. **Network: OkHttp 단독 vs Ktor Client** — 3단계 직전 결정. SolTi / TapCounter 가 이미 OkHttp 면 그대로 OkHttp.

## 14. 미결정 / TODO

- [ ] Hilt 의존성 완전 제거 vs optional Hilt module 추가 여부 — 2단계 (Ads) 검증 후 결정
- [ ] `BuildConfig.DEBUG` 대신 `ApplicationInfo.FLAG_DEBUGGABLE` 유지 — 결정 (호환성 우선)
- [ ] AdMob v23 → 향후 메이저 버전업 대응 정책 (`23.+` caret 유지 vs 고정 버전)
- [ ] AGP 10 / Kotlin 2.4 진입 시점에 `android.builtInKotlin=true` + `android.newDsl=true` 로 일괄 마이그레이션 (gradle.properties 두 줄 제거 + libs.versions.toml 의 kotlin-android 플러그인 제거 + 모든 모듈 build.gradle.kts 의 plugin alias 한 줄 제거)
- [ ] Foojay 가 Gradle 9 와 호환되면 `kotlin { jvmToolchain(17) }` 로 되돌릴지 검토 — CI 와 로컬 PC 의 JDK 차이를 내부에서 해소 가능
