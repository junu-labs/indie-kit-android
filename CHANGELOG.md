# 바뀐 내용

[Keep a Changelog](https://keepachangelog.com/ko/1.1.0/) 형식. SemVer 를 따른다.

자매 저장소: [`indie-kit-ios`](https://github.com/junu-labs/indie-kit-ios). iOS 와 단계 번호가 1:1 대칭이며, 같은 단계라도 데모 검증 시점이 다르면 태그 번호가 어긋날 수 있다.

## [0.9.6] - 2026-10-27

### 고침 (광고 — AdMob 일치율 이슈 다시, 터치카트 "두 번째 레시피" 보고에서 출발)

- **채울 광고가 없을 때 곧바로 다시 요청하던 문제.** 전면 · 리워드 광고 받기가 실패하면 (채울 광고 없음 · 그물 끊김) 다음 `prepare…` / `show…` 에서 곧바로 다시 요청했다. 터치카트처럼 "광고가 준비될 때까지 3초마다 `prepareRewarded`" 를 부르면 실패한 요청이 줄줄이 쌓여 AdMob 일치율 (일치한 요청 / 요청) 이 떨어졌다. 이제 **실패 뒤에는 30초 · 1분 · 2분 … (최대 10분) 동안 같은 자리에 다시 요청하지 않는다** (기기 켜진 뒤 시간으로 잼). 받으면 처음으로 돌아간다.
- **"다음 차례용" 으로 받아 둔 광고가 버려지던 것 — 새 길.** 드문 차례 (하루 한 번 무료 뒤 두 번째부터 광고 등) 는 미리 받아 두어도 그 차례가 안 오면 광고가 안 보이고 버려진다 (노출 / 요청이 낮아짐). 새 함수로 **누른 순간에 받아서 받는 대로 띄울** 수 있다 — 요청한 광고는 받으면 곧바로 보여지므로 버려지는 요청이 없다.

### 추가 (광고)

- `IndieKitAds.loadAndShowRewarded(activity, placement, timeoutMillis = 8000, onResult)` — 받아 둔 리워드 광고가 있으면 곧바로, 없으면 지금 받아서 띄운다. 시간 안에 못 받거나 실패하면 null. 늦게 온 광고는 다음 차례에 쓴다. 어느 실에서 불러도 된다.
- `IndieKitAds.loadAndShowInterstitial(activity, placement, timeoutMillis = 5000, onDismiss)` — 같은 방식의 전면 광고. 못 받으면 건너뛰고 onDismiss. 다음 차례용으로 미리 받지 않는다. 받는 사이 화면이 닫히면 (Activity finishing) 띄우지 않는다.
- 기존 `prepare…` / `show…` 는 그대로다 (위의 "실패 뒤 기다림" 만 더해짐). iOS 자매와 같은 이름.

### 고침 (광고 — 배너 둘레 검은 띠 다시, 좁은 광고 소재일 때. 터치카트 3.3.2 시험 1번 · 회귀 시험 38)

- **폭 맞춤 배너에 칸보다 좁은 소재가 오면 AdView 안쪽이 검게 칠해지던 문제.** 0.9.5 는 AdView *바깥* 둘레를 투명으로 했지만, 자리 폭 그대로 폭 맞춤 (`getInlineAdaptiveBannerAdSize`) 으로 요청하면 구글이 칸보다 좁은 소재 (시험 광고의 유튜브 소재 "AdMob has a YouTube channel") 를 보낼 때 AdView 안쪽 구글 WebView 가 남는 곳을 검게 칠했다 — 앱이 BannerAdView 폭을 320dp 로 묶어도 그 칸 안에서 생겼다 (캡처 37-long-list-top 의 양옆 띠). 칠하는 것이 WebView 안 HTML 이라 바탕색으로는 못 막는다. 이제 **배너는 기본으로 표준 320×50 고정 크기 (`AdSize.BANNER`) 로 요청한다** — 고정 크기 요청엔 그 크기에 꼭 맞는 소재만 와서 안쪽에 남는 곳이 없다. 둘레는 0.9.5 처럼 투명 (앱이 칠한 광고 자리 바탕색). 자리가 320dp 보다 좁으면 그 폭의 폭 맞춤으로 (예전과 같음). 고정 크기라 회전해도 AdView 를 다시 만들지 않는다.
- 예전 폭 맞춤을 꼭 쓰려는 앱은 `BannerAdView(modifier, placement, adaptive = true)` (새 인자, 기본 false).

### 해당 없음

- iPhone 비정상 종료를 MetricKit 으로 받아 서버로 (IndieKitNetwork `CrashReports`) — 애플 전용. Android 비정상 종료는 공장이 Play Console 에서 하루 한 번 읽는다 (lib/android_crash_reports.py).
- 앱 안 회사 이름 JunuLabs → JUNU — indie-kit 에는 화면에 보이는 회사 이름 문구가 없다 (설정 화면 저작권 줄은 각 앱 코드 안). 앱마다 고쳐야 한다.

### 앱에서 바꿀 것

- 꼭 바꿀 것은 없다 — 고치지 않아도 빌드되고 돈다. 배너는 판만 올리면 320×50 고정으로 바뀐다 (광고 자리 높이 50dp 그대로 맞음). 터치카트 `AdSlot.kt` 의 폭 320dp 묶음은 이제 없어도 되지만 둬도 같다.
- **일치율 · 노출률을 올리려면 (권장)**: 드문 차례의 광고는 미리 받지 말고 누른 순간 받아서 띄운다.
  - 리워드: 화면이 열릴 때 부르던 `prepareRewarded(…)` 와 "준비될 때까지 3초마다 다시 부르기" 고리 (`LaunchedEffect` 의 while) 를 빼고, 단추를 누르면 단추를 "불러오는 중" 으로 두고 `IndieKitAds.loadAndShowRewarded(act, placement = …) { reward -> … }`. 단추는 광고가 받아졌는지와 상관없이 (오늘 광고 횟수가 남았으면) 보인다. 터치카트 `ui/recipe/RecipeScreen.kt` 가 해당.
  - 전면: 드물게 오는 차례면 `prepareInterstitial` 대신 그 자리에서 `loadAndShowInterstitial(activity) { … }` (받는 동안 "잠시만요").
- 설정 화면 저작권 줄 회사 이름 (JunuLabs → JUNU) 은 앱마다 고친다.

### 검증

- 단위 시험 `full screen ad state backs off after failure` · `full screen ad state backoff grows and resets on load` · `load and show wait finishes once` · `banner requests standard size by default` · `banner request adaptive when narrow or chosen` · `banner standard request stable across rotation` 더함 / `./gradlew testDebugUnitTest assembleRelease` 통과 (광고 31).

## [0.9.5] - 2026-10-17

### 고침 (광고 — AdMob 보고서에서 노출 / 요청 50% 미만, 일치율 60%대로 나온 건에서 출발)

- **안 보일 전면 · 리워드 광고를 켤 때마다 요청하던 문제.** `configure` 뒤 SDK 준비가 끝나면 등록한 전면 · 리워드 자리를 모두 미리 받아 두었다. 그런데 앱은 전면 광고를 "성취 순간" 같은 드문 때에만 띄우므로 대부분의 실행에서 받아 둔 광고가 한 번도 안 보이고 버려졌고, AdMob 에는 요청만 쌓여 노출률 · 일치율이 낮게 잡혔다. 이제 **켤 때 미리 받지 않는다.** 앱이 `showInterstitial` / `showRewarded` 를 불렀을 때 받아 둔 광고가 없으면 그 차례는 건너뛰고 (흐름은 예전처럼 곧바로 이어짐) 다음 차례에 쓸 광고를 그때 요청한다. 광고가 닫힌 뒤 곧바로 다음 광고를 받던 것도 멈췄다 — 다음 차례가 올 때 받는다.
- **오래된 광고를 띄우던 문제.** 구글 기준으로 받은 지 1시간이 지난 전면 · 리워드 광고는 띄워도 노출로 잡히지 않는다. 이제 받은 지 55분이 넘은 광고는 띄우지 않고 버린 뒤 새로 요청한다 (기기 켜진 뒤 시간으로 재므로 시계를 바꿔도 흔들리지 않음).
- **같은 자리에 요청이 겹칠 수 있던 문제.** 요청 중 · 표시 중에는 새 요청을 보내지 않는다. SDK 시작 (뒷실) 이 아직 안 끝났으면 요청을 끝난 뒤로 미룬다.

### 추가

- `IndieKitAds.prepareInterstitial(placement)` / `IndieKitAds.prepareRewarded(placement)` — 띄울 차례가 가까울 때 앱이 불러 미리 받아 둔다 (예: 오늘 할 일이 하나 남았을 때, "광고 보고 받기" 단추가 있는 화면이 열릴 때). 어느 실에서 불러도 되고, 여러 번 불러도 요청은 한 번. 앱 시작 직후처럼 띄울지 모를 때 부르면 일치율이 다시 떨어지므로 그때는 부르지 않는다.

### 고침 (광고 — 하루세팅 · 탭카운터 시험에서 배너 둘레 검은 띠가 보인 건에서 출발)

- **광고가 자리보다 작으면 남는 곳이 검은 띠로 보이던 문제.** `BannerAdView` 의 AdView 가 자리 폭 전체를 차지하는데 남는 곳의 바탕을 정하지 않아, 320×50 · 468×60 (시험 광고) 같은 광고가 오면 양옆이 검게 칠해졌다 (광고 규칙 "검은 띠 금지" 위반). 이제 AdView 바탕을 투명으로 두고, 투명한 그릇 (`BannerContainerLayout`) 안에 **받은 광고 크기만큼만, 자리 가운데에** 둔다. 둘레에는 앱이 칠한 광고 자리 바탕색이 그대로 보인다. 광고가 자리보다 크면 자리 안으로 줄여 잡는다.

### 고침 (광고 — 유럽 동의를 받은 뒤에 광고 SDK 시작, 0.9.3 의 "아직 안 따랐다" 숙제)

- **동의 예약과 SDK 시작이 나란히 가던 순서.** 구글 권고는 유럽 사용자 동의 (UMP) 를 받은 뒤 광고 SDK 를 시작하는 것인데, 0.9.3 부터 동의 예약 직후 곧바로 SDK 를 시작해 동의 전에 광고 요청이 나갈 틈이 있었다. 이제 **동의 절차가 끝나고 (창 닫힘 / 창 필요 없음 / 갱신 실패) 동의가 광고 요청을 허락할 때만** (`canRequestAds`) SDK 를 시작한다. 지난 실행에서 받아 둔 동의가 있으면 절차 결과를 기다리지 않고 곧바로 시작해 (구글 예제와 같음) 다시 켜는 사람은 늦지 않는다. 시작은 한 번만 (`AdsStartGate`).
- **0.9.3 의 "첫 화면이 멈추지 않게" 는 그대로.** SDK 시작은 여전히 뒷실에서, 저장된 동의 읽기도 configure 를 부른 순간이 아니라 화면 그리는 실의 다음 차례로 미룬다. 앱은 예전처럼 `Application.onCreate` 에서 `configure(this)` 를 불러도 된다. 0.9.4 의 "화면이 뜬 뒤 configure 해도 동의 절차가 돈다" 도 그대로.
- **배너 · Native 도 SDK 준비 뒤에만 광고를 요청한다.** 예전엔 configure 직후 요청했다. 이제 준비 전엔 빈 자리로 기다리다 준비되는 순간 스스로 다시 그려져 요청한다. `loadNativeAd` 를 직접 부른 것도 준비 뒤로 미룬다. 유럽 사용자가 처음 켤 때는 동의창에 답한 뒤에 배너가 뜬다.
- 동의가 필요한데 허락을 못 받으면 그 실행에서는 광고를 시작하지 않는다 (정책 먼저). 설정의 "광고 설정 바꾸기" (`requestConsentForm`) 로 답하면 그때 시작한다. `requestConsent = false` 인 앱, 그리고 동의 절차를 걸 수 없는 별난 context (Application 이 아님) 는 예전처럼 곧바로 시작한다 (뒤의 것은 경고 로그).

### 추가 (네트워크 — 앱 버전 확인을 모듈로)

- `AppVersionCheck.check(context, slug)` — 앱마다 따로 짜던 AppVersionChecker 를 한 줄로. junu 서버 (`POST /compliance/app-version/`, 앱이 debuggable 이면 dev, 아니면 api — 모듈이 고름) 에 묻고 `null` / `OPTIONAL` / `FORCED` 를 돌려준다. 실패 · SUCCESS 아님이면 조용히 null. 선택 업데이트는 서버 버전마다 한 번만 (모듈 몫 SharedPreferences 에 기억). 디스크 · 그물 일은 뒷실에서 — 어느 실에서 불러도 된다. 창 모양은 앱 몫 (알림창 부품 + 고양이).
- 쪼갠 도구도 공개: `fetch` (묻기만) · `decide` (창 고르기) · `parse` / `isNewer` (버전 비교 — iOS 와 같은 규칙) · `openStore(context)` (스토어 앱, 없으면 웹) · `marketUri` / `webStoreUrl`. 자료형 `AppUpdatePrompt`, `AppVersionInfo`. iOS 자매와 같은 이름.

### 바뀜 (광고 — 모듈 초기화 때 "기본 광고 ID" 와 배너 ID 가 같게 들어가던 건에서 출발)

- **광고 ID 를 "자리 이름 → ID" 묶음으로 받아 기본 ID 와 배너 ID 가 헷갈리던 것.** 앱은 `bannerAdUnitIDs = mapOf(IndieKitAds.DEFAULT_PLACEMENT to AdUnitID(release = 배너ID))` 처럼 "기본 자리" 라는 이름에 배너 ID 를 넣어야 했고, 광고를 안 쓰는 종류는 빈 묶음을 넘겨도 디버그에서 시험 광고를 요청할 수 있었다. 새 `configure(context, bannerIDs, interstitialIDs, rewardedIDs, nativeIDs, requestConsent, consentTestSettings)` 는 **광고 종류마다 ID 목록** 만 받는다. 기본 ID 는 따로 없다 — 목록의 첫 ID 가 자리 이름 없이 부르는 광고이고, 두 번째부터는 그 ID 글자를 자리 이름으로 넘겨 부른다 (`BannerAdView(placement = "ca-app-pub-…/222")`).
- **광고 ID 가 없는 경우.** 빈 목록 = 그 종류를 안 쓰는 앱 — 띄우라고 해도 아무것도 안 하고 **디버그에서도 시험 광고조차 요청하지 않는다** (배너 자리는 빈 칸, 전면은 곧바로 onDismiss, 보상은 null). 목록에 빈 글자 (`listOf("")`, 열쇠 파일 값이 아직 빔) = 쓰긴 하는데 출시 ID 가 아직 없음 — 디버그는 시험 광고, 출시는 미표시 (빈 글자를 출시 ID 로 요청하지 않는다). 배너 · 전면 · 보상은 꼭 적게 했다 (안 쓰면 `emptyList()`).
- 옛 `configure(context, bannerAdUnitIDs = …)` 는 `@Deprecated` 로 남겼다 — 동작은 예전 그대로 (빌드 경고만 뜬다). 실 규칙 (어느 실에서 불러도 됨, SDK 시작은 뒷실) · 동의 → SDK 시작 순서는 두 함수가 같은 몸통을 쓴다.

### 바뀜 (빌드 설정 정리 — PLAN.md "14. 미결정 / TODO" 매듭. 기능 변화 없음)

- 쓰지 않던 카탈로그 줄 두 개를 뺐다: `kotlin-android` 플러그인 (0.9.0 부터 AGP 내장 Kotlin 이라 아무 모듈도 안 씀), `okhttp-mockwebserver` (시험은 자체 Interceptor 로 응답을 꾸밈).
- 결정만 적은 것 (PLAN.md 14): Hilt 는 안 넣는다 (object 싱글톤 — Hilt 앱은 자기 쪽에서 감쌈) · AdMob 은 `+` 범위 없이 정확한 판 하나로 고정 · 내장 Kotlin / 새 DSL 은 이미 옮겨 있어 AGP 10 때 따로 할 일 없음 · `jvmToolchain(17)` 은 되돌리지 않는다 (빌드 JDK 21 하나 + 바이트코드 17 고정이 짐이 적음).
- 앱이 받는 .aar 는 같다.

### 앱에서 바꿀 것

- 꼭 바꿀 것은 없다 — 고치지 않아도 빌드되고 돈다. 다만 그대로 두면 **각 실행의 첫 전면 광고 차례는 건너뛴다** (그때 받아 두고 다음 차례에 띄움). 첫 차례부터 띄우려면 띄울 차례 바로 앞 단계에서 `prepareInterstitial()` 을 부른다. 리워드 광고를 쓰는 앱은 단추가 있는 화면이 열릴 때 `prepareRewarded()` 를 부른다 (안 부르면 첫 단추 누름에 보상이 null).
- 쓰지 않는 리워드 ID 를 `rewardedAdUnitIDs` 에 넣어 둔 앱도 이제 요청이 나가지 않는다 (예전엔 켤 때마다 나갔다).
- 광고 자리 (AdSlot) 바탕은 앱이 칠해 둔다 (이미 규칙). 모듈은 이제 둘레를 투명으로 두므로 그 색이 보인다.
- 앱 버전 확인은 옮기고 싶은 앱부터 수정 요청으로: 앱의 `AppVersionChecker` 를 `prompt = AppVersionCheck.check(context, BuildConfig.JL_SERVER_SLUG)` 한 줄로 바꾸고, 업데이트 버튼은 `AppVersionCheck.openStore(context)`. 판 목록에 `indie-kit-network` 가 있어야 한다. 앱이 자기 prefs 에 기억해 둔 "이미 본 선택 업데이트" 는 옮겨지지 않아, 옮긴 첫 실행에 같은 선택 창이 한 번 더 뜰 수 있다.
- 광고 시작을 새 configure 로 — 꼭은 아니지만 옛 것은 빌드 경고가 뜬다. 예: `IndieKitAds.configure(context = app, bannerIDs = listOf(BuildConfig.AD_BANNER_ID), interstitialIDs = listOf(BuildConfig.AD_INTERSTITIAL_ID), rewardedIDs = emptyList(), requestConsent = true)`. 기획서에 없는 광고 종류는 `emptyList()`. 탭카운터 (`TapCounterApp.kt`, 배너만) · 하루세팅 (`modules/Modules.kt`, 배너 + 전면 + 보상 자리) 이 해당 — 하루세팅의 `one(id)` (빈 값이면 emptyMap) 도구는 필요 없어진다.

### 검증

- 단위 시험 `full screen ads are not preloaded on configure` · `full screen ad state does not duplicate requests` · `full screen ad state expires old ads` · `full screen ad state presenting and dismiss` · `full screen ad state failure allows retry` · `prepare before configure is ignored` 더함.
- 단위 시험 `ads start gate waits for consent` · `ads start gate starts only once` · `ads start gate without consent starts immediately` · `ads are not ready for compose before configure` · `banner smaller than slot is centered` · `banner larger than slot is clamped` · `banner unknown size fills slot` (광고) · 버전 비교 · 창 고르기 · 버전 묻기 (요청 모양 / 실패면 null) · 스토어 주소 (네트워크) 더함.
- `./gradlew testDebugUnitTest assembleRelease` 통과 (광고 22 · 네트워크 14, 앞 요청의 시험 포함).
- 단위 시험 `placement map from ID list` · `empty ID list means format unused` · `blank ID means no release ID yet` (광고 ID 목록) 더함 / `./gradlew testDebugUnitTest assembleRelease` 통과 (광고 25).

## [0.9.4] - 2026-08-07

### 고침 (광고 — TouchCart 에뮬레이터 검사에서 유럽 동의창이 한 번도 안 뜨는 건에서 출발)

- **configure 를 화면 뒤로 미룬 앱에서 유럽 동의 절차가 통째로 안 돌던 문제.** 동의 절차를 거는 자리가 "화면이 **새로** 만들어지는 순간" 하나만 듣고 첫 신호에 스스로 빠졌다. 그래서 앱이 첫 안내가 동의창에 덮이지 않게 configure 를 안내 뒤로 미루면 (TouchCart), 신호가 이미 지나간 뒤라 그 실행에서 절차가 아예 안 돌았다 — 화면이 하나뿐이고 회전해도 다시 안 만드는 앱이면 기회가 다시 안 온다. 이제 iOS 자매와 같은 원리로 **절차가 도는 그 순간 "지금 떠 있는 화면" 을 스스로 찾는다**: 새 추적자 (`CurrentActivityTracker`) 가 앱이 켜지는 순간 (어떤 화면보다 먼저, 매니페스트 자동 합침) 심어져 화면을 지켜보고, 동의 절차는 화면이 이미 있으면 즉시 / 아직 없으면 첫 화면이 잡히는 순간 진행한다. Application.onCreate 에서 configure 를 부르는 기존 앱들은 뒤 갈래를 그대로 타므로 동작 변화가 없다.

### 추가 (동의창 시험 길 — 한국에서는 동의창이 원래 안 떠 시험할 길이 없던 것)

- **시험용 나라 강제 설정.** `configure(consentTestSettings = ConsentTestSettings(geography = ConsentTestGeography.EEA))` 로 유럽에 있는 것처럼 꾸며 동의창이 뜨는 모습을 에뮬레이터 / 시험 기기에서 확인할 수 있다. `NOT_EEA` 는 반대 경로 (안 뜨는 쪽) 확인용. 에뮬레이터는 자동으로 시험 기기 인정, 실기기는 `testDeviceHashedIds` 에 Logcat 의 기기 번호를 넣는다.
- **동의 기록 초기화.** `IndieKitAds.resetConsentForTesting(context)` — 동의창은 한 번 답하면 다시 안 뜨므로, 지우고 앱을 재시작하면 다시 뜬다.
- 두 기능 모두 **디버그 빌드에서만 동작** — 출시 빌드에서는 무시하고 경고만 남겨, 시험 설정이 실사용자에게 새어 나가는 사고를 모듈이 막는다.

### 검증

- clean 빌드 (디버그 / 출시) + 전체 단위 테스트 통과. 에뮬레이터 통합 검사는 TouchCart 쪽에서 진행.

## [0.9.3] - 2026-08-05

### 고침 (광고 — StudyDay 에뮬레이터 검사에서 앱이 켜자마자 꺼진 건에서 출발)

- **앱 켤 때 화면이 멎던 문제.** `MobileAds.initialize` 를 부른 실에서 그대로 돌려, 앱이 `Application.onCreate` 에서 `configure` 를 부르면 화면 그리는 실이 붙잡혔다. 첫 실행처럼 SDK 설정을 새로 받아와야 하는 때는 이 한 줄이 오래 걸려, 첫 화면을 그리지 못한 채 폰이 "응답 없음" 으로 앱을 끈다. 이제 **모듈이 스스로 뒷실로 비켜 가고**, SDK 준비가 끝나면 미리 적재만 화면 그리는 실로 되돌린다. 앱은 예전처럼 `configure(this)` 를 그냥 부르면 된다 — 앱이 실을 만들 필요가 없다.
- **동의창 예약이 SDK 시작 뒤에 걸리던 순서.** 예약을 `MobileAds.initialize` **앞** 으로 옮겼다. 예약 자체는 순식간에 끝나므로 화면을 안 붙잡는다. 순서가 중요한 까닭: 뒤에 두면 SDK 시작이 걸리는 만큼 예약이 늦어져 첫 Activity 가 이미 만들어진 뒤가 될 수 있고, 화면이 하나뿐인 앱에서는 알려 줄 Activity 가 다시 안 생겨 유럽 동의 절차가 통째로 안 돈다. (이 위험 때문에 StudyDay 가 앱 안에 우회 60줄을 두고 있었다 — 이제 걷어낼 수 있다.)
- **같은 광고 번호로 요청을 두 번 보내던 문제.** 전면 / 리워드 적재기가 자리 이름 없음 (`null`) 과 `DEFAULT_PLACEMENT` ("default") 를 다른 칸으로 나눠 보관했는데, 광고 번호를 고르는 자리는 둘 다 "default" 키를 봤다. 그래서 기본 자리를 등록한 앱 (SolTi / TouchCart / TapCounter) 은 켤 때마다 같은 광고를 두 번 불러왔고, 그중 하나는 화면에서 부를 길이 없어 안 쓰였다. 이제 두 이름이 한 칸을 같이 쓴다.
- **등록도 안 한 기본 자리를 미리 불러오던 문제.** 시험용 빌드에서는 `AdUnitID.resolve` 가 등록 여부와 상관없이 늘 시험용 번호를 채우므로, 앱이 쓰지도 않는 전면 광고가 켤 때마다 불려 왔다. 이제 **앱이 실제로 등록한 자리만** 미리 적재한다.
- **배너 / Native 가 빈 칸으로 굳던 문제.** `isConfigured` 는 화면을 다시 그리게 만드는 값이 아니라, 설정 전에 한 번 그려진 배너가 설정이 끝나도 빈 칸으로 남았다. 화면용으로 지켜볼 수 있는 자리를 따로 두어, 설정이 끝나는 순간 스스로 다시 그려진다.
- **Native 광고가 다시 그려질 때 새 광고를 버리던 문제.** "이미 걷혔다" 는 표식을 화면 전체가 기억해, 자리 이름이 바뀌어 효과가 다시 돌면 앞 표식이 남아 새로 불러온 광고까지 버렸다. 이제 그 표식이 효과 한 번에만 딸린다.

### 알아 둘 것

- 매니페스트의 `OPTIMIZE_INITIALIZATION` / `OPTIMIZE_AD_LOADING` 표시는 **넣지 않았다.** 구글 문서상 SDK 24.0.0 부터 기본으로 켜져 있고 (이 모듈은 25.4.0), 구글 설명대로 시작 일의 *일부* 만 옮길 뿐이라 위 뒷실이 여전히 필요하다.
- 유럽 동의를 *받고 나서* SDK 를 시작하라는 구글 권고는 아직 안 따랐다 (지금은 동의 예약과 SDK 시작이 나란히 간다). 멎는 문제와 별개인 정책 사안이라 따로 잡는다.

### 검증

- clean 빌드 + 전체 단위 테스트 통과. 실기기 / 에뮬레이터 검사는 앱 쪽에서 진행.

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
