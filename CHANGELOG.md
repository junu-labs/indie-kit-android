# 바뀐 내용

[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식. SemVer 를 따른다.

자매 저장소: [`indie-kit-ios`](https://github.com/junu-labs/indie-kit-ios). iOS 와 단계 번호가 1:1 대칭이며, 같은 단계라도 데모 검증 시점이 다르면 태그 번호가 어긋날 수 있다.

## [0.9.2] - 2026-07-24

### 고침 (결제 — SolTi 통합 검증에서 발견. iOS 0.7.7 과 대칭)

- **실패한 조회가 권한을 지우던 문제.** queryPurchases 가 실패해도 빈 목록을 돌려줘, 이미 확보한 구독 권한을 재확인이 빈 값으로 덮어썼다. 이제 실패 (null) 와 진짜 없음 (빈 목록) 을 구분하고, INAPP·SUBS 두 조회가 모두 성공했을 때만 값을 교체한다 — 하나라도 실패면 기존 권한 유지.
- **restore() 가 뮤텍스를 우회해 경쟁 쓰기.** 결제 성공 직후의 재확인과 복원이 서로의 결과를 덮어쓸 수 있었다. 이제 모든 권한 갱신이 뮤텍스 안에서 한 줄로 선다.
- **최근 구매 스티키 추가.** 결제 성공 즉시 권한을 반영하고, Play 조회 결과에 반영되기 전까지 어떤 재확인도 지우지 못하게 붙잡아 둔다. 스티키는 구독 기간 근사로 스스로 만료 — 취소 후 만료 시엔 정상적으로 무료 복귀.
- **연결 끊김 후 재연결 없던 문제.** 이제 끊김 콜백에서 백오프 (0.5초→1초→2초, 최대 3번) 재연결하고, 매 재확인 직전에도 연결 생존을 확인한다.
- **acknowledge 재시도 추가.** 실패 시 유한 백오프 (1초→2초, 최대 3번) 재시도. 3번 다 실패해도 다음 재확인의 재승인 안전망이 받친다 (미승인 3일 뒤 자동 환불 방지).

### 검증

- clean 빌드 + 전체 단위 테스트 통과. 실기기 검증은 SolTi 통합에서 진행.

## [0.9.1] - 2026-07-23

### 바뀜 (툴체인 — Android Studio 업데이트 후 마저 올림)

- AGP 9.2.1 → 9.3.0, compileSdk 36 → 37 (모든 모듈), coreKtx 1.18.0 → 1.19.0 (1.19.0 이 compileSdk 37 을 요구).
- 이 태그를 받는 앱은 compileSdk 37 로 컴파일해야 한다 (Kotlin 2.4 이상 요구는 0.9.0 과 동일).

### 검증

- clean 빌드 + 전체 단위 테스트 통과. 네 앱 (SolTi / TouchCart / TapCounter / CrowdPick) 이 v0.9.1 로 빌드되는 것 확인.

## [0.9.0] - 2026-07-23

### 바뀜 (전체 의존성 정비 — 앱 파급 포함)

- 툴체인: AGP 9.1.1 → 9.2.1 (Android Studio 지원 한도에 맞춤), Kotlin 2.3.20 → 2.4.10, Gradle 9.6.1. **이 태그를 받는 앱은 Kotlin 2.4 이상으로 함께 올려야 한다.**
- okhttp 4.12.0 → 5.4.0 (메이저 — okhttp3 패키지 이름이 그대로라 사용처 코드 무변경).
- AGP 9 내장 Kotlin 으로 전환 — kotlin-android 플러그인 제거, Kotlin 버전은 루트 buildscript 로 고정 (동기화 때 뜨던 지원 중단 경고 해소). 앱이 받는 .aar 는 동일.
- 빌드 JDK 를 21 로 (jitpack.yml) — 산출물 바이트코드는 여전히 Java 17 이라 앱은 JDK 17 로도 사용 가능.

### 검증

- clean 빌드 + 전체 단위 테스트 통과.

## [0.8.2] - 2026-07-23

### 고침 (indie-kit-ads — Play Console "오래된 SDK (fragment 1.1.0)" 경고)

- Google Play Services (play-services-basement) 가 추이로 끌어오는 옛 androidx.fragment 1.1.0 을, 최신 1.8.9 를 직접 실어 밀어냄. 이 모듈을 쓰는 앱은 단출한 Compose 앱 (TapCounter 처럼 화면이동 라이브러리가 없는 앱) 이라도 자동 해결.

### 바뀜 (의존성 정비 — 앱 파급 없는 안전분)

- firebaseBom 34.16.0 / playServicesAds 25.4.0 / UMP 4.0.0 / composeBom 2026.06.01 / kotlinxSerialization 1.11.0 / kotlinxCoroutines 1.11.0 / androidxCredentials 1.6.0 / googleId 1.2.0 / androidxActivity 1.13.0 / kakaoSdk 2.24.0 / mavenPublish 0.37.0.

### 검증

- 전체 빌드 + 단위 테스트 통과.

## [0.8.1] - 2026-07-22

### 바뀜 (indie-kit-billing — Play Billing 7.1.1 → 9.1.0)

- 구글 정책 (2026-08-31 부터 8.0.0 이상 필수) 대응. 상품 조회 콜백이 목록을 감싼 상자 (QueryProductDetailsResult) 로 바뀐 한 곳만 수정 — 모듈 공개 API 는 그대로.

### 추가 (indie-kit-network — 사진 / 파일 올리기)

- `postMultipart` (multipart/form-data) + `MultipartFile` 자료형. 인증 첨부·401 갱신은 다른 호출과 동일. 앱이 손수 짜던 멀티파트 조립을 모듈로 흡수 (CrowdPick 검증분).

### 검증

- 단위 테스트 통과 (멀티파트 포함 9개).

## [0.8.0] - 2026-07-20

### 추가 (indie-kit-push — 푸시 모듈, 6단계)

- 알림 배관 전부를 한 줄씩으로: 권한 요청 (Android 13+ 팝업) / FCM 알림 주소 발급·갱신 / 우리 서버 기기 등록 (내용이 바뀔 때만 올리는 지문 방식, 실패해도 앱을 막지 않고 다음 앱 시작 때 재시도) / 도착 알림 띄우기 / 누름 전달 (onTap, handleIntent) / 기기 안 예약 알림 (부팅 복원 포함) / 시스템 알림 설정 열기. iOS 자매와 진입점 1:1 대칭. 설계는 `PUSH_PLAN.md`.
- 데모 앱으로 샌드박스 발송까지 실기기 검증 완료.
- 디버그 빌드 (앱이 debuggable) 에서만 토큰 원문을 Logcat 에 남긴다 — 라이브러리 자신의 BuildConfig 는 배포판에서 항상 release 라 사용하는 앱의 플래그를 본다.

### 바뀜 (indie-kit-ads — 배너를 폭 맞춤 (적응형) 으로. iOS 자매와 짝 맞춤)

- 고정 `AdSize.BANNER` (320×50) 를 폭 맞춤으로 전환 — 놓인 자리의 실제 폭을 재서 그 폭에 맞는 광고를 요청한다 (`getInlineAdaptiveBannerAdSize`, 높이 50 상한이라 `.height(50.dp)` 띠 규칙 그대로).
- 회전으로 폭이 바뀌면 새 폭 기준으로 광고를 다시 적재한다. AdView 는 광고 크기를 한 번 정하면 바꿀 수 없어 `key(placement, 폭)` 으로 AdView 를 재생성하고 옛것은 onDispose 에서 정리. 첫 폭은 바로, 바뀐 폭은 0.2초 잠잠해진 뒤에만 반영 (iOS 와 같은 규칙).
- 앱 쪽 변경 없음. 모듈만 새 버전으로 올려 다시 빌드하면 된다.

### 검증

- `:indie-kit-ads` / `:indie-kit-push` 컴파일 통과. 데모 앱 실기기 검증 (푸시 샌드박스 발송 / 배너 폭 맞춤 + 자리별 광고, AdMob 검증 도구 통과).

## [0.7.1] - 2026-06-25

### 고침 (빌드 — JitPack JDK 고정)

- `jitpack.yml` 추가로 빌드 JDK 를 openjdk17 로 고정했다. 이게 없으면 JitPack 기본 JDK(이미지에서 사라진 oracle 17.0.12)로 출시본 빌드가 깨진다. 코드 변경 없음 — 0.7.0 과 라이브러리 내용은 같고 출시 빌드 환경만 고친 판이다. (iOS 는 Swift Package 소스 빌드라 영향 없음 — v0.7.0 그대로.)

## [0.7.0] - 2026-06-25

### 바뀜 (IndieKitAds — 광고 ID 설정 정리. 기존 호출 코드 수정 필요)

- `configure` 의 단일 ID 매개변수 (`bannerAdUnitID` / `interstitialAdUnitID` / `rewardedAdUnitID` / `nativeAdUnitID`) 를 없앴다. 이제 자리 이름 → ID 묶음 (`bannerAdUnitIDs` 등) 한 가지로만 등록한다. 같은 "배너 ID" 가 두 군데로 갈라져 헷갈리던 문제를 없앤다. iOS 자매와 같은 API 모양 (짝 맞추기).
  - 자리 이름을 안 넘기고 띄우는 기본 광고는 새 상수 `IndieKitAds.DEFAULT_PLACEMENT` ("default") 키로 등록한다.
  - 호출 코드 바꾸는 법: `bannerAdUnitID = AdUnitID(release = "...")` → `bannerAdUnitIDs = mapOf(IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = "..."))`.
  - `BannerAdView()` / `showInterstitial(activity)` 등 자리 이름 없이 띄우는 코드는 그대로 — 내부에서 기본 자리를 찾는다.
- `AdUnitID` 에서 `debug` 자리를 없앴다. 디버그 빌드용 테스트 ID 는 라이브러리가 항상 알아서 채우므로 앱이 설정할 이유가 없었다. 이제 `AdUnitID(release = ...)` 출시 ID 하나만 받는다. (디버그 빌드는 release 값과 무관하게 항상 구글 테스트 ID.)

### 고침 (IndieKitAds — 출시 빌드에서 테스트 광고 노출 차단)

- 출시 빌드인데 운영(release) 광고 ID 가 비어 있으면, 이전엔 경고 로그만 남기고 **구글 테스트 광고를 그대로 띄웠다** (구글 정책 위반 / AdMob 계정 정지 / 스토어 심사 위험). 이제 이 경우 광고를 아예 표시하지 않는다 (`AdUnitID.resolve` 가 `String?` 를 돌려주고 release 빈 값이면 null → 배너 / 전면 / 리워드 / Native 모두 미표시).
- 디버그 빌드 동작은 그대로 — ID 가 비면 구글 테스트 ID 로 떨어져 개발 중 광고를 확인할 수 있다.

### 검증

- `testDebugUnitTest` 통과. `selectAdUnitID` 자리 선택 규칙 테스트를 새 시그니처 (기본 자리 키 / 기본 자리 미등록 시 빈 AdUnitID) 로 갱신.

## [0.6.0] - 2026-06-12

### 추가 (IndieKitAds — 자리 (placement) 별 광고 ID)

- 같은 종류의 광고를 화면마다 다른 ID 로 분리할 수 있게 됨 (예: 배너를 홈 / 상세 / 설정 화면에 각각 다른 ID 로). 자리별로 AdMob 보고서가 분리되어 어느 화면 광고가 돈을 버는지 보인다. iOS 자매와 같은 API 모양 (짝 맞추기).
  - `configure` 에 `bannerAdUnitIDs` / `interstitialAdUnitIDs` / `rewardedAdUnitIDs` / `nativeAdUnitIDs` (자리 이름 → `AdUnitID` 묶음) 매개변수 추가. 기존 단일 ID 매개변수는 "기본 자리" 로 그대로 동작 — 기존 호출 코드 수정 불필요.
  - `BannerAdView(placement = ...)`, `NativeAdView(placement = ...)`, `loadNativeAd(context, placement) { }`, `showInterstitial(activity, placement)`, `showRewarded(activity, placement)` — 자리 이름으로 띄움. 생략 시 기본 ID.
  - 등록 안 된 자리 이름은 경고 로그 후 기본 ID 로 대체 (크래시 없음).
  - 전면 / 리워드는 자리마다 적재기를 따로 두고 (`InterstitialAdLoader` / `RewardedAdLoader` 가 object 싱글턴 → 자리별 보관소로 바뀜) configure 때 기본 자리 + 등록한 모든 자리를 각각 미리 적재.
  - 광고 통계 이벤트 (`ad_loaded` / `ad_impression` / `ad_dismissed` / `ad_clicked` / `ad_reward_earned`) 에 `placement` 칸 추가 — Firebase 보고서에서 자리별 분리 가능.

### 검증

- 단위 테스트 2개 추가 — 자리 선택 규칙 (기본 / 등록된 자리 / 모르는 자리), configure 전 자리별 적재 상태 false. `testDebugUnitTest` 통과.
- 데모 앱 검증 완료 (2026-06-12, 에뮬레이터 Medium Phone API 36) — 기본 배너 / 자리 "home" 배너 / 미등록 자리 "no_such_place" 의 기본 ID 대체 (logcat 경고 확인) / 자리별 전면 ("workout_end") / 자리별 리워드 ("hint", 보상 콜백까지) / 네이티브 (자리 "feed", AdMob 네이티브 광고 검사기 "No implementation issues found") 모두 테스트 광고 정상 노출. JitPack 출시본 v0.6.0 좌표로 데모 빌드.

## [0.5.1] - 2026-06-05

### 추가 (IndieKitBilling — 구독 만료 정확성)

- `IndieKitBilling.refresh()` 공개 메서드 추가 — 보유 권한을 조용히 다시 확인한다. iOS 자매와 1:1 대칭.
  - `restore()` 와 역할이 다르다. `restore()` 는 사용자가 누르는 "구매 복원" 용이고, `refresh()` 는 `queryPurchasesAsync` 만 다시 불러 현재 보유 상태를 읽는 조용한 자동 재확인이다. 둘 다 다이얼로그를 띄우지 않지만 (Android 는 iOS 와 달리 `restore()` 도 암호창이 없음), 역할 구분은 같다. `refresh()` 는 `_isLoading` 스피너도 켜지 않아 포그라운드 복귀 (onResume) 마다 호출해도 안전하다.
  - 효과 — 사용자가 구독을 취소하고 만료가 지나면, 앱을 껐다 켜지 않고 포그라운드 복귀만으로도 `isPro` 가 false 로 정확히 떨어진다.

### 검증

- 단위 테스트 1개 추가 — `refresh()` 가 configure 전에도 안전 (총 7개 통과).
- 데모 앱 변경 (라이브러리 저장소 밖) — `MainActivity` 의 Activity 생명주기 `ON_RESUME` 에서 `IndieKitBilling.refresh()` 호출. `ProcessLifecycleOwner` 대신 Activity 생명주기를 쓴 이유는 데모에 `lifecycle-process` 의존성이 없고 단일 Activity 라서. 로컬 라이브러리 (composite build) 로 데모 컴파일 통과 확인.
- `VERSION_NAME` 을 `0.5.1` 로 맞춤 (이전 `0.2.7` 은 CHANGELOG 진척과 어긋나 있던 값 — 정렬).

## [0.5.0-rc1] - 2026-05-20

### 추가 (5단계 IndieKitAuth)

- 라이브러리 본체 작성 — iOS 자매 와 1:1 API.
  - `IndieKitAuth` `object` 싱글턴 — Credential Manager (Google) + kakao-sdk (Kakao) 위 얇은 래퍼.
  - 진입점: `configure(context, googleServerClientId, kakaoNativeAppKey, backend)` / `signIn(activity, provider)` / `signOut(activity)` / `unlink(activity)`.
  - 상태: `currentUser: StateFlow<AuthUser?>`, `isReady: StateFlow<Boolean>`.
  - 자료: `AuthProvider` enum (GOOGLE / KAKAO — 안드로이드는 Apple 없음), `AuthUser` data class, `SessionTokens`, `SessionExchangeAdapter` data class.
  - 오류: `IndieKitAuthError` sealed class — `ProviderNotConfigured` / `UserCancelled` / `MissingCredential` / `BackendExchange(underlying)` / `Underlying(underlying)`.
  - 외부 의존성: `androidx.credentials 1.3.0` + `credentials-play-services-auth 1.3.0` + `googleid 1.1.1` + `com.kakao.sdk:v2-user 2.20.6` + `kotlinx-coroutines-core 1.8.1` + `okhttp 4.12.0`.
  - 단위 테스트 10개 통과.
- `settings.gradle.kts` — Kakao 자체 Maven 저장소 (`https://devrepo.kakao.com/...`) 한 줄 추가. Kakao SDK 가 Google Maven / Central 에 없음.

### 검증 (실 폰 신원값 수신까지)

- 데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 의 실 폰 (안드로이드 디버그 APK) 에서 두 제공자 모두 통과:
  - Google — Credential Manager 시스템 시트 → 계정 선택 → 동의 → 결과 카드에 id / displayName / idToken 수신.
  - Kakao — 카카오톡 앱 또는 웹 폴백 → 동의 → 결과 카드에 id / displayName / email / idToken 수신.
- 데모 앱 변경 (라이브러리 저장소 밖):
  - `DemoApplication.kt` 에 `IndieKitAuth.configure(...)` 호출 — Google Web Client ID + Kakao Native App Key 채움. backend 는 이번 차례엔 null.
  - `MainActivity.kt` 에 `AuthSection` Composable 추가 — Google / Kakao 두 버튼 + 결과 카드 (id / email / displayName / idToken 앞 60자 / serverSession 상태) + 본문 미리보기 카드 + 로그아웃 / 회원 탈퇴.

### 검증 중 발견한 함정 (문서로 명시)

- **Android Credential Manager 는 `setServerClientId` 에 Web 클라이언트 ID 가 필요** — Android 클라이언트 ID 가 아님. Google Cloud 콘솔에서 "OAuth 클라이언트 ID" 를 두 가지 만들어야 함:
  - Android 클라이언트 (패키지명 + Debug SHA-1) — 콘솔 등록 / 검증용
  - 웹 애플리케이션 클라이언트 — idToken audience 식별용. 코드의 `googleServerClientId` 에 이 ID 가 들어감
- **Kakao SDK 저장소는 자체 Nexus 사용** — `https://devrepo.kakao.com/nexus/content/groups/public/`. `settings.gradle.kts` 의 `dependencyResolutionManagement.repositories` 에 한 줄 추가 안 하면 Gradle 이 의존성 못 찾음.

### 새 문서

- `AUTH_SETUP_Android.md` — Google + Kakao 콘솔 셋업 (Debug SHA-1 받는 법 + 키 해시 base64 변환 + 두 가지 Google 클라이언트 ID 차이) + Gradle 의존성 + 코드 호출 + 자주 막히는 곳. iOS 의 `AUTH_SETUP.md` 와 자매.

### 미해결 (다음 차례)

- 서버 세션 발급 검증 — `POST /auth/social-login/` 실 호출 + `currentUser.serverSession` 채워짐 확인. iOS 자매와 같이 주말 서버 셋업과 묶음.
- IndieKitNetwork 자동 연결 검증 — `tokenProvider = { IndieKitAuth.accessToken }` 슬롯 동작 확인.
- 정식 `v0.5.0` 태그 — 서버 검증 끝나면 발행. 이번은 `v0.5.0-rc1` 까지만.

## [0.4.0] - 2026-05-20

### 추가 (4단계 IndieKitBilling)

- 라이브러리 본체 작성 — iOS 자매 (`IndieKitBilling.shared`) 와 1:1 API.
  - `IndieKitBilling` `object` 싱글턴 — Play Billing v7.1.1 위 얇은 래퍼.
  - 진입점: `configure(context, products)` / `purchase(activity, productID)` / `restore()` / `owns(productID)` / `isPro` / `hasActiveSubscription` / `hasLifetime` / `expirationDate(productID)`.
  - 상태 노출: `entitlements: StateFlow<Set<String>>` / `products: StateFlow<List<ProductDetails>>` / `isReady` / `isLoading`. Compose 는 `collectAsState` 한 줄.
  - 핵심 흐름:
    - 결제 후 자동 acknowledge (Play 정책상 3일 내 호출 필수 — 미호출 시 자동 환불).
    - 보유 권한 새로 고침 — INAPP + SUBS 두 번 조회, 합집합.
    - 구독 만료 timestamp 근사 — purchaseTime + ProductDetails 의 billingPeriod (ISO 8601 → ms).
    - PurchasesUpdatedListener 콜백을 `CompletableDeferred` 로 suspend `purchase()` 함수와 연결.
  - 자료: `ProductDescriptor` / `BillingProductType` (AutoRenewableSubscription / NonConsumable) / `PurchaseResult` (Success / UserCancelled / Pending) / `IndieKitBillingError` (NotConfigured / ProductNotFound / VerificationFailed / Underlying / AlreadyInProgress).
- 외부 의존성 추가 (이 모듈에만):
  - `billing-ktx:7.1.1` (api) — 사용처가 ProductDetails / Purchase 객체를 직접 만지는 경우가 있음.
  - `kotlinx-coroutines-core:1.8.1` (api) — suspend 함수 + StateFlow.
- 단위 테스트 6개 (iOS 자매 5개 + Android 고유 `isoPeriodToMs` 1개).

### 검증 (모두 그린)

- `./gradlew :indie-kit-billing:build :indie-kit-billing:test` — 통과 (테스트 6개 / 0 실패).
- 데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 의 실 폰 + Play Console 라이선스 테스터 흐름으로 다음 모두 확인:
  - 평생 (NonConsumable) 구매 + 보유 중 표시.
  - 월간 자동 갱신 구독 구매 + 만료일 표시 (약 1개월 후).
  - 연간 자동 갱신 구독 구매 + 만료일 표시 (약 1년 후).
  - 복원 — 보유 권한 그대로 유지.
- 데모 앱 변경 (라이브러리 저장소 밖):
  - `DemoApplication.kt` 에 `IndieKitBilling.configure(...)` 호출 (applicationScope 안에서).
  - `MainActivity.kt` 에 결제 카드 + `BillingSection` / `PurchaseRow` Composable + 만료일 표시.
  - 데모 앱을 Play Console 내부 테스트 트랙에 첫 출시 (검증용 라이선스 테스터 흐름).

### 결정 사항

- **Play Billing v7.1.1 으로 시작 (v8 아님).** PLAN.md 는 "v8" 명시했지만 v8 의 정확한 API 차이를 검증하지 않은 채 짜면 빌드 실패 위험. 일단 v7.1.1 안정 최신으로 검증 통과 → v8 마이그레이션은 별도 단계.
- **`object` 싱글턴 + StateFlow.** iOS 의 `@Observable @MainActor IndieKitBilling.shared` 와 같은 위치. Compose 는 `collectAsState` 한 줄, 비-Compose 도 같은 진입점 그대로 사용 가능.
- **`launchBillingFlow` 동시 진입 방지.** `PurchasesUpdatedListener` 가 콜백이라 동시 구매가 섞이면 어느 결과인지 매핑 불가. `pendingPurchase` `AtomicReference` + `AlreadyInProgress` 에러로 명시.
- **구독 만료일 근사 계산.** Purchase 자체엔 만료일 없음. ProductDetails 의 billingPeriod (P1M / P1Y) + purchaseTime 으로 근사 — UI 표시용. 정확한 만료는 서버 측 RTDN — 출시 직전 백엔드 셋업과 같이.
- **서버 영수증 검증 미포함.** iOS 와 같음. 5단계 (Auth) 의 백엔드 셋업과 묶어 `receiptVerifier` 클로저 자리로 추가 예정.

## [0.3.0] - 2026-05-19

### 추가 (3단계 IndieKitNetwork)

- 라이브러리 본체 작성 — iOS 자매 (`IndieKitNetwork` struct) 와 1:1 API.
  - `IndieKitNetwork` class — 인스턴스를 직접 만들어 쓰는 형태. `baseURL` / `defaultHeaders` / `timeoutMs` / `tokenProvider` / `tokenRefresher` / `client` / `json` / `logger` / `authorizationHeader`.
  - `get<R>()` / `post<B, R>(body)` / `put<B, R>(body)` / `delete()` — `inline reified`, kotlinx.serialization 의 KSerializer 자동 처리.
  - `send(Request): RawResponse` — raw 출구. 인증 헤더 자동 첨부 + 401 자동 갱신은 그대로 작동.
  - 401 자동 갱신 흐름 — iOS 와 동일. 매 요청 직전 tokenProvider 호출 → `Authorization: Bearer` 첨부 → 401 응답 + tokenRefresher 있으면 갱신 한 번 → 같은 요청 재시도 → 두 번째에도 401 이면 `Unauthorized`.
  - `HTTPLogEntry` data class + `IndieKitNetworkError` sealed class (`InvalidUrl` / `Http` / `Decoding` / `Transport` / `Unauthorized`).
- 외부 의존성 추가 (이 모듈에만):
  - `okhttp:4.12.0` (api) — 사용처가 `Request` / `Response` 를 직접 만질 수 있음.
  - `kotlinx-serialization-json:1.7.3` (api) — 사용처가 자기 자료 클래스에 `@Serializable` 을 붙임.
  - `kotlinx-coroutines-core:1.8.1` (api) — `suspend` 함수 + `Dispatchers.IO`.
  - `kotlin-serialization` 플러그인 (`org.jetbrains.kotlin.plugin.serialization`, Kotlin 컴파일러와 같은 버전).
- 단위 테스트 8개 (iOS 자매와 1:1 대응). OkHttp `Interceptor` 로 fake response 시퀀스 주입.

### 검증 (모두 그린)

- `./gradlew :indie-kit-network:build :indie-kit-network:test` — 통과 (테스트 8개 / 0 실패).
- 데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 의 에뮬레이터에서 GET (jsonplaceholder) / POST junu app-version / 401 자동 갱신 흐름 모두 확인.
- 데모 앱 검증 동안 composite build ON (`settings.gradle.kts` 의 `includeBuild` 블록). JitPack 출시본 검증으로 돌리려면 그 블록 주석 처리 + `libs.versions.toml` 의 좌표 / 버전 원복.

### 결정 사항

- **OkHttp 단독 (Ktor 아님).** PLAN.md 의 "잔가지 결정" 6번 — SolTi / TapCounter 가 OkHttp 면 그대로 OkHttp. 의존성 단순, 사용처 `OkHttpClient` (interceptor / cookie jar 등) 재사용 가능.
- **`inline reified` 함수 + `@PublishedApi internal` 헬퍼.** `get<R>()` 같은 generic 함수가 호출 시점에 reified R 의 KSerializer 를 얻기 위해 inline 필수. 본문에서 호출하는 내부 헬퍼 (`buildRequest`, `encodeJson`, `decode`) 는 `@PublishedApi internal` 로 노출.
- **`RawResponse` 새 자료.** OkHttp 의 `Response` 는 body 를 한 번만 읽고 닫아야 하므로, 라이브러리가 body 를 미리 `ByteArray` 로 읽어 박아 돌려줌. 사용처가 close 책임 없음.
- **`tokenProvider` / `tokenRefresher` 는 `suspend` 람다.** iOS 가 `async` 클로저인 자매에 대응. 사용처가 access token 을 비동기 저장소 (DataStore 등) 에서 가져올 수 있게.

## [0.2.7] - 2026-05-12

### 고침

- **`NativeAdView` validator 통과 (사용자 단말 "No implementation issues found" 확인)**. 진짜 원인은 advertiser 자산이 아니라 자산 view 측정 실패였고, AdMob 안드로이드 SDK 의 native validator 가 측정 실패를 첫 자산 이름 (advertiser) 으로 일반화해 표시한 것. 검증 통과 출처: TouchCart (`Apps/TouchCart/Android/.../NativeAdRow.AdLoadedCard`).
- 라이브러리 기본 UI (`DefaultNativeAdContent`) 의 측정 패턴 3가지를 흡수:
  - `NativeAdView` modifier 에 `Modifier.fillMaxWidth().height(IntrinsicSize.Min)` 강제 — verticalScroll Column / Card 등 unbounded height 환경에서도 자식 측정만큼만 차지하게.
  - `if (nativeAd.mediaContent != null)` 체크 + `Modifier.fillMaxWidth().sizeIn(maxHeight = 120.dp)` + `ImageView.ScaleType.CENTER_CROP` — 응답에 mediaContent 있을 때만 등록 + 높이 120dp 안으로 제한 (AdMob 정책 ≥120dp 만족).
  - `loadNativeAd` 의 `AdLoader.Builder` 에 `NativeAdOptions.ADCHOICES_TOP_RIGHT` 명시 — AdChoices 위치 고정.
- 라이브러리 진입점 / 기본 UI / loadNativeAd 를 같은 패키지의 새 파일 `IndieKitNativeAd.kt` 로 분리. `NativeAdView.kt` 자체는 [Google 공식 compose_utils/NativeAdView.kt](https://github.com/googleads/googleads-mobile-android-examples/blob/main/kotlin/advanced/JetpackComposeDemo/app/src/main/java/com/google/android/gms/example/jetpackcomposedemo/formats/compose_utils/NativeAdView.kt) 와 한 글자도 안 다른 상태로 유지 (package + 머리 주석만 라이브러리 식별성).

### 빌드

- AGP 9.0.1 → 9.1.1, Gradle wrapper 9.1.0 → 9.3.1 — 데모 (IndieKitDemo_Android) 와 composite build (settings.gradle.kts 의 includeBuild) 호환을 위함. JitPack / 사용처 .aar 산출물 자체엔 영향 없음.

## [0.2.6] - 2026-05-12

### 고침

- `NativeAdView` (Compose 진입점) 의 `AndroidView` factory 에서 내부 **`NativeAdView` (View) 와 `ComposeView` 의 `layoutParams` 높이를 `MATCH_PARENT` → `WRAP_CONTENT`** 로 변경.
  - **진단 근거**: 사용자 안드로이드 폰 스크린샷에서 NativeAd 영역에 광고 자산이 안 그려지고 거대한 AdMob 로고가 화면을 거의 채우는 증상 확인. iOS 는 같은 라이브러리 디자인으로 정상 카드가 그려지고 validator 풍선이 "No implementation issues found" 통과. 즉 광고는 정상 적재되는데 안드로이드의 자산 view 측정이 실패해 SDK 가 fallback 로고를 표시하던 것.
  - **원인**: 데모 `MainActivity` 가 `verticalScroll Column > Card > Column > Box > Column > NativeAdView(fillMaxWidth())` 안에서 호출. `verticalScroll` 은 자식에 unbounded height 환경을 제공하는데, 라이브러리 내부에서 NativeAdView (View) 와 ComposeView 의 layoutParams 가 `MATCH_PARENT × MATCH_PARENT` 라 unbounded 높이를 무한 측정 → 자식 자산 view 측정 실패 → SDK 가 자산 boundary 검증 실패 → fallback 로고 + "Advertiser asset outside ..." validator 경고 (SDK 가 측정 실패를 첫 자산 이름으로 일반화).
  - **해결**: layoutParams 높이를 `WRAP_CONTENT` 로 변경해 자식 자산 측정 결과를 따르게. Google 공식 데모는 `Scaffold > Surface(fillMaxSize)` 환경이라 `MATCH_PARENT × MATCH_PARENT` 도 통과했지만, 라이브러리는 어느 환경 (scroll / Box / Card 안) 에서도 동작해야 하므로 `MATCH_PARENT × WRAP_CONTENT` 가 안전한 기본값.

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
- 1단계 IndieKitCore 추가 — 5개 모듈이 같이 쓰는 도구 묶음.
  - `IKLogger` — `kr.co.junu.indiekit` subsystem + 카테고리 6개 (analytics / ads / network / billing / auth / core), `android.util.Log` 기반.
  - `IndieKitConfigurationError` — sealed class (NotConfigured / MissingRequiredValue / AlreadyConfigured), 한국어 메시지.
  - `AnalyticsBus` — 광고 → 통계 약한 연결 통로. ReentrantLock 보호, 등록 안 된 상태에서도 record 호출 안전.
  - `AnalyticsValue` — sealed class (StringValue / IntValue / DoubleValue / BoolValue) + `analyticsParams` 헬퍼 (iOS 의 ExpressibleByLiteral 대응).
  - `ConcurrencyHelpers` — 빈 자리, 단계가 진행되며 추출 후보 발견 시 채운다.
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
