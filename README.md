# indie-kit-android

안드로이드 인디 앱들이 Gradle 한 줄로 끌어다 쓰는 공용 라이브러리 묶음.

새 앱을 시작할 때마다 광고 / 로그인 / 결제 / 통계 / 네트워크 코드를 따로 짜지 않도록, 한 번 잘 만들어 두고 같이 쓴다.

## 들어 있는 것

**6개 모듈은 한 묶음이고 버전도 하나다.** JitPack 은 저장소 전체를 git 태그 하나로 빌드하므로, 모듈마다 버전이 따로 있지 않다. 어떤 모듈을 받든 **모두 같은 태그 (지금은 `v0.9.2`)** 로 받는다. 아래 "상태" 칸은 버전이 아니라 *검증이 어디까지 됐는가* 다.

| 모듈 | 무엇을 해 주는가 | 단계 | 상태 |
|---|---|---|---|
| `indie-kit-analytics` | Firebase Analytics — 화면 이동 / 이벤트 / 사용자 속성 | 1 | 라이브러리 완성, SolTi 통합 검증 예정 |
| `indie-kit-ads` | AdMob 배너 / 전면 / 리워드 / Native + 유럽 광고 동의창 | 2 | **실기기 검증 완료** (자리 (placement) 별 광고 ID 는 에뮬레이터 검증) |
| `indie-kit-network` | OkHttp 위 얇은 호출 묶음 + 인증값 자동 갱신 + 사진 / 파일 올리기 (multipart) | 3 | 라이브러리 완성, 데모 검증 |
| `indie-kit-billing` | Play Billing v9 — 구독 / 1회성 / 평생 결제 | 4 | 라이브러리 완성, 데모 검증 |
| `indie-kit-auth` | 카카오 / 구글 로그인 + 우리 서버 세션 발급 | 5 | 라이브러리 완성, 데모 검증 |
| `indie-kit-push` | 알림 권한 / FCM 알림 주소 등록 / 수신·누름 / 기기 안 예약 알림 | 6 | **실기기 검증 완료** (샌드박스 발송 확인) — 설계는 `PUSH_PLAN.md` |

자세한 단계는 `PLAN.md`, 모듈별 변경 이력은 `CHANGELOG.md` 참고.

현재는 **6개 모듈 모두 작성 완료, 최신 태그 `v0.9.2`**. 광고 (`indie-kit-ads`) 와 푸시 (`indie-kit-push`) 는 실기기 검증까지 끝났고, 나머지는 데모 앱 검증 단계다. 다음은 SolTi 통합 검증.

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
implementation("com.github.junu-labs.indie-kit-android:indie-kit-analytics:v0.9.2")
implementation("com.github.junu-labs.indie-kit-android:indie-kit-ads:v0.9.2")
```

**여러 모듈을 쓸 땐 버전을 반드시 똑같이 맞춘다** (위처럼 둘 다 `v0.9.2`). 모듈들은 속에서 `indie-kit-core` 를 함께 쓰는데, 버전을 섞으면 (`analytics:v0.1.0` + `ads:v0.7.1` 식) core 가 두 벌 딸려와 충돌할 수 있다.

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
    bannerAdUnitIDs = mapOf(
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-...")
    ),
    interstitialAdUnitIDs = mapOf(
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-...")
    ),
    rewardedAdUnitIDs = mapOf(
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-...")
    ),
    nativeAdUnitIDs = mapOf(
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-...")
    ),
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

광고 ID 는 자리 이름 → ID 묶음 하나로 등록한다. 자리 이름을 안 넘기고 띄우는 기본 광고는 `IndieKitAds.DEFAULT_PLACEMENT` ("default") 키로 등록한다.

**디버그 / 출시 빌드 동작이 다르다.** 디버그 빌드에서 ID 를 안 넘기면 구글 공식 테스트 ID 가 자동으로 들어간다 (개발 중 광고 확인용). 하지만 **출시 빌드에서 운영(release) ID 가 비어 있으면 광고를 아예 안 띄운다** — 테스트 광고를 출시 앱에 노출하면 구글 정책 위반 / AdMob 계정 정지 위험이라 막아 둔다. 즉 출시 전 `release` 값을 꼭 채워야 광고가 보인다.

#### 자리 (placement) — 화면마다 다른 광고 ID 쓰기

같은 종류의 광고를 여러 화면에서 각각 다른 ID 로 분리하고 싶을 때 (예: 배너를 홈 / 상세 / 설정 화면에 따로) 자리 이름을 등록한다. 네 가지 형식 (배너 / 전면 / 리워드 / Native) 모두 지원.

```kotlin
// Application.onCreate — 기본 자리 + 자리별 ID 를 한 묶음으로 등록:
IndieKitAds.configure(
    context = this,
    bannerAdUnitIDs = mapOf(                                                    // 배너 ID 묶음
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-..."),  // 기본 배너
        "settings"                    to AdUnitID(release = "ca-app-pub-...-settings")
    ),
    interstitialAdUnitIDs = mapOf(
        IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "ca-app-pub-..."),
        "workout_end"                 to AdUnitID(release = "ca-app-pub-...")
    )
)

// Compose 화면 안 — 자리 이름으로 띄움:
BannerAdView(modifier = Modifier.fillMaxWidth(), placement = "settings")
NativeAdView(modifier = Modifier.fillMaxWidth(), placement = "feed")

// 전면 / 리워드도 자리 이름으로:
IndieKitAds.showInterstitial(activity, placement = "workout_end") { /* 닫힘 후 */ }
IndieKitAds.showRewarded(activity, placement = "hint") { reward -> /* ... */ }
```

규칙: 자리 이름을 생략하면 기본 자리 (`DEFAULT_PLACEMENT`) ID. 등록 안 된 자리 이름을 넘기면 경고 로그 후 기본 자리 ID 로 대체 (크래시 없음). 전면 / 리워드는 configure 때 등록한 모든 자리를 각각 미리 적재한다. 통계 이벤트엔 `placement` 칸이 같이 실려 Firebase 보고서에서 자리별로 분리해 볼 수 있다.

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
- Kotlin 2.4+ (이 태그를 받는 앱도 Kotlin 2.4 이상)
- compileSdk 37 (앱도 37 로 컴파일)
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
| `v0.9.2` | 2026-07-24 | 결제 (`indie-kit-billing`) 안정화 — SolTi 통합 검증에서 발견된 결함 수정 (iOS 0.7.7 과 대칭). 실패한 조회가 이미 확보한 권한을 지우던 문제 (실패와 진짜 없음 구분, 두 조회 모두 성공할 때만 교체), 복원이 뮤텍스를 우회하던 경쟁 쓰기, 최근 구매 스티키 (결제 즉시 반영 + 재확인이 못 지움), 연결 끊김 백오프 재연결, acknowledge 유한 재시도. 전체 빌드·테스트 통과. |
| `v0.9.1` | 2026-07-23 | Android Studio 업데이트 후 툴체인 마저 올림. AGP 9.2.1→9.3.0, compileSdk 36→37, coreKtx 1.18.0→1.19.0. **이 태그를 받는 앱은 compileSdk 37 로 컴파일해야 한다** (Kotlin 2.4 이상 요구는 v0.9.0 과 동일). 전체 빌드·테스트 통과. |
| `v0.9.0` | 2026-07-23 | 전체 의존성 정비 (앱 파급 포함). 툴체인: AGP 9.1.1→9.2.1 (Android Studio 지원 최신), Kotlin 2.3.20→2.4.10, Gradle 9.6.1. 라이브러리: okhttp 4.12.0→5.4.0. AGP 9 내장 Kotlin 으로 전환 (kotlin-android 플러그인 제거, 버전은 루트 buildscript 로 고정). **이 태그를 받는 앱은 Kotlin 2.4 이상으로 함께 올려야 한다** (compileSdk 는 36 그대로). 빌드 JDK 21 (jitpack.yml, 산출물 바이트코드는 여전히 Java 17). 전체 빌드·테스트 통과. |
| `v0.8.2` | 2026-07-23 | 의존성 정비 + fragment 경고 해결. `indie-kit-ads` 에 최신 `androidx.fragment:1.8.9` 를 직접 실어, Google Play Services 가 끌어오는 옛 fragment 1.1.0 을 밀어냄 (Play Console "오래된 SDK" 경고 대응 — 단출한 Compose 앱도 자동 해결). firebase/ads/ump/compose/serialization/coroutines/credentials/googleId/activity/kakao/maven-publish 최신 안정판으로 올림. AGP·Kotlin·okhttp·compileSdk 는 앱 파급 때문에 유지. |
| `v0.8.1` | 2026-07-22 | `indie-kit-billing` — Play Billing 결제 라이브러리 7.1.1 → 9.1.0 (구글 정책 2026-08-31 부터 8.0.0 이상 필수 대응, 상품 조회 콜백 한 곳만 수정). `indie-kit-network` — 사진 / 파일 올리기 `postMultipart` 추가 (multipart/form-data). 앱이 손수 짜던 멀티파트 조립을 모듈로 흡수 (CrowdPick 검증분). |
| `v0.8.0` | 2026-07-20 | 단계 6 `indie-kit-push` — 푸시 모듈 (권한 / FCM 알림 주소 등록 / 우리 서버 기기 등록 / 수신·누름 / 기기 안 예약 알림). 샌드박스 발송 실기기 검증 완료. `indie-kit-ads` 배너는 폭 맞춤 (적응형) + 회전 재적재로 전환 (iOS 자매와 짝). |
| `v0.7.1` | 2026-06-25 | 빌드 환경 고침 — `jitpack.yml` 로 빌드 JDK 를 openjdk17 로 고정 (없으면 출시본 빌드가 깨짐). 코드 변경 없음. |
| `v0.7.0` | 2026-06-25 | `indie-kit-ads` — 광고 ID 설정 정리 (자리 이름 → ID 묶음 한 가지로 통일, iOS 자매와 같은 모양). 출시 빌드에서 운영 ID 가 비면 테스트 광고를 띄우지 않고 아예 미표시 (구글 정책 위반 차단). |
| `v0.6.0` | 2026-06-12 | `indie-kit-ads` — 자리 (placement) 별 광고 ID 지원. 같은 종류의 광고를 화면마다 다른 ID 로 분리 (configure 의 `*AdUnitIDs` 묶음 + `placement` 매개변수). 기존 호출 코드는 그대로 동작. iOS 자매와 같은 API 모양. 데모 앱 검증 완료 (에뮬레이터 — 자리 배너 / 미등록 자리 기본 ID 대체 / 자리별 전면 / 리워드 보상까지 동작 확인). |
| `v0.5.1` | 2026-06-05 | `indie-kit-billing` — 구독 만료 정확성. 조용한 재확인 `refresh()` 추가 (포그라운드 복귀만으로 `isPro` 가 정확히 떨어짐). iOS 자매와 1:1. |
| `v0.5.0-rc1` | 2026-05-20 | 5단계 `indie-kit-auth` — 카카오 / 구글 로그인 + 우리 서버 세션 교환. Credential Manager + kakao-sdk 위 얇은 래퍼. iOS 자매와 1:1 API. 데모로 신원값 수신 검증. |
| `v0.4.0` | 2026-05-20 | 4단계 `indie-kit-billing` — Play Billing 구독 / 1회성 / 평생 결제. 자동 acknowledge + 보유 권한 새로 고침 + 구독 만료 근사. iOS 자매와 1:1 API. 데모 검증. |
| `v0.3.0` | 2026-05-19 | 3단계 `indie-kit-network` — OkHttp 위 얇은 호출 묶음 (`get` / `post` / `put` / `delete`) + 인증 헤더 자동 첨부 + 401 자동 갱신. iOS 자매와 1:1 API. 데모 검증. |
| **`v0.2.7`** | 2026-05-12 | **2단계 IndieKitAds 실기기 검증 완료** — AdMob native ad validator "No implementation issues found" 통과. 라이브러리 기본 UI 에 TouchCart 출처 측정 패턴 3가지 (`IntrinsicSize.Min` / `mediaContent` null 체크 + `sizeIn(maxHeight = 120.dp)` / `NativeAdOptions.ADCHOICES_TOP_RIGHT`) 흡수. `NativeAdView.kt` 는 [Google 공식 compose_utils/NativeAdView.kt](https://github.com/googleads/googleads-mobile-android-examples/blob/main/kotlin/advanced/JetpackComposeDemo/app/src/main/java/com/google/android/gms/example/jetpackcomposedemo/formats/compose_utils/NativeAdView.kt) 와 한 글자 안 다른 상태로 유지, 라이브러리 추가물은 같은 패키지의 `IndieKitNativeAd.kt` 로 분리. 빌드 도구 AGP 9.1.1 / Gradle 9.3.1 통일. (v0.2.1 ~ v0.2.6 은 같은 풍선 풀려고 시도한 hotfix 6번, 모두 빗나감 — 검증 통과 패턴이 측정 쪽에 있다는 단서를 한 번에 잡지 못한 결과.) |
| `v0.2.0` | 2026-05-11 | 2단계 IndieKitAds 라이브러리 완성. AdMob 4종 광고 (배너 / 전면 / 리워드 / Native — Native 는 안드로이드 선행) + 유럽 광고 동의창 (UMP) + Compose 진입점 (`BannerAdView`, `NativeAdView`) + AnalyticsBus 자동 연결. |
| `v0.1.0` | 2026-05-11 | 1단계 IndieKitAnalytics — Firebase Analytics 한 줄 추상화 (`logScreen` / `log` / `logLogin` / `logSignUp` / `logPurchase` / `setUserId` / `setUserProperty`). AnalyticsBus 등록 통로 마련. |
| `v0.0.1` | 2026-05-11 | 0단계 부트스트랩 — Gradle 멀티 모듈 골격 6개 (Core + 외부 5개), JUnit 4 자리, JitPack / Maven Local 경로, CI (Ubuntu + JDK 17). |

## 함께 쓰는 자매 저장소

- `indie-kit-ios` — iOS 쪽 같은 역할 (Swift Package Manager). 단계 번호와 진입점 이름이 1:1 대칭.
- `indie-kit` — 13명 담당 시스템 (앱 만드는 흐름 자체).
