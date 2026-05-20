# IndieKitAuth Android — 로그인 셋업 안내

이 문서는 안드로이드 앱에 IndieKitAuth (Google + Kakao 로그인) 를 처음 붙일 때 필요한 콘솔 등록과 Gradle / 코드 설정을 한 자리에 모은 것이다. 데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 검증에서 실제로 거친 흐름 그대로.

자매 문서 — iOS 의 `~/Developer/JunuLabs/System/indie-kit-ios/AUTH_SETUP.md`. 같은 단계 / 같은 진입점 이름.

## 한눈에 보는 흐름

| 제공자 | 외부 콘솔 등록 | Gradle / 코드 | configure 인수 |
|---|---|---|---|
| Google | Android 클라이언트 ID (패키지 + SHA-1) + Web 클라이언트 ID 두 가지 | Credential Manager + googleid 의존성 | `googleServerClientId` (Web 클라이언트 ID) |
| Kakao  | 안드로이드 플랫폼 (패키지 + 키 해시) + Kakao 로그인 활성화 + OpenID Connect ON | kakao v2-user 의존성 + 자체 Maven 저장소 | `kakaoNativeAppKey` |

안드로이드는 Apple 로그인을 다루지 않음 (iOS 자매에는 Apple 포함). Apple 로그인이 필요하면 Custom Tabs + OAuth 2.0 흐름을 별도 모듈로 추가.

## 0. 사전 작업 — 패키지명 + Debug SHA-1 얻기

Android 콘솔 등록 전 두 가지가 필요하다.

### 패키지명

`app/build.gradle.kts` 의 `applicationId` 줄. 예: `kr.co.junu.indiekitdemo`.

Android 는 소문자 관례. iOS 처럼 대소문자 함정은 없지만 어쨌든 콘솔과 코드의 값이 정확히 일치해야 함.

### Debug SHA-1

시스템에 Java 가 안 깔려 있으면 시스템 keytool 명령이 실패한다. Android Studio 의 번들 JDK 를 한 줄 환경변수로 잡고 Gradle 의 `signingReport` 작업을 쓰면 두 가지 (debug + release) 의 SHA-1 / SHA-256 / MD5 가 한 번에 출력된다.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd <안드로이드 앱 루트>
./gradlew signingReport
```

출력 중 `Variant: debug` 블록의 `SHA1:` 한 줄이 우리가 쓸 값.

```
Variant: debug
Config: debug
Store: /Users/.../.android/debug.keystore
Alias: AndroidDebugKey
SHA1: E8:F8:7B:74:A4:EC:53:65:8D:15:37:E8:51:22:5E:AB:DD:16:69:7D
SHA-256: ...
```

릴리즈 SHA-1 도 같이 나오는데, 데모 검증 단계엔 디버그면 충분. 출시 단계에 릴리즈 / Play App Signing SHA-1 도 콘솔에 추가 등록.

## 1. Google 셋업

### 콘솔 작업 — 두 가지 클라이언트 ID 만들기

안드로이드 Credential Manager 는 두 개의 OAuth 클라이언트가 필요하다.

**1) Android 클라이언트 ID — 패키지 + SHA-1 검증용**

1. https://console.cloud.google.com → 프로젝트 (예: `IndieKitDemo`)
2. **API 및 서비스 → 사용자 인증 정보** → **+ 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**
3. 애플리케이션 유형: **Android**
4. 이름: `IndieKitDemo Android`
5. 패키지 이름: `kr.co.junu.indiekitdemo`
6. SHA-1 인증서 디지털 지문: 위에서 받은 값 그대로
7. 만들기 → 받은 ID 는 콘솔에 남아 있지만 **코드에는 안 들어감**

**2) Web 애플리케이션 클라이언트 ID — idToken audience 식별용**

1. 같은 화면에서 다시 **+ 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**
2. 애플리케이션 유형: **웹 애플리케이션**
3. 이름: `IndieKitDemo Web`
4. 승인된 JavaScript 출처 / 리디렉션 URI 는 **둘 다 비워둠** (Credential Manager 는 직접 호출 안 함)
5. 만들기 → 받은 클라이언트 ID 가 **코드의 `googleServerClientId` 자리에 들어감**

두 클라이언트 ID 의 앞 숫자 (프로젝트 번호) 는 같고, 뒤 무작위 부분이 다르다. 예:
- Android: `393350003604-5eoflpu17ttifdor1aaqh6rmif8v56fq.apps.googleusercontent.com`
- Web:     `393350003604-p9onnlkq84tvepk0e01cagt9hk0ck7if.apps.googleusercontent.com`

Web 클라이언트는 만들 때 "클라이언트 보안 비밀번호 (Client Secret)" 도 같이 나오는데, **안드로이드 클라이언트는 secret 을 사용하지 않는다** (audience 검증용으로 ID 만 필요). secret 은 한 번 보여주고 다시 못 보니 안전한 곳에 별도 보관만.

### Gradle 의존성

`libs.versions.toml`:
```toml
androidxCredentials = "1.3.0"
googleId = "1.1.1"

androidx-credentials = { group = "androidx.credentials", name = "credentials", version.ref = "androidxCredentials" }
androidx-credentials-play-services-auth = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "androidxCredentials" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleId" }
```

`indie-kit-auth/build.gradle.kts`:
```kotlin
implementation(libs.androidx.credentials)
implementation(libs.androidx.credentials.play.services.auth)
implementation(libs.googleid)
```

### 코드 호출

```kotlin
IndieKitAuth.configure(
    context = applicationContext,
    googleServerClientId = "393350003604-p9onnlkq84tvepk0e01cagt9hk0ck7if.apps.googleusercontent.com", // Web 클라이언트 ID
    kakaoNativeAppKey = null,
    backend = null,
)

// Activity 안 코루틴
val user = IndieKitAuth.signIn(activity, AuthProvider.GOOGLE)
```

### 자주 막히는 곳

- **시트가 안 뜨고 바로 닫힘** — 패키지명 또는 SHA-1 불일치. 콘솔에 등록한 두 값과 실제 빌드의 값이 정확히 일치해야 함.
- **"사용 가능한 계정이 없음"** — 폰에 Google 계정이 동기화 안 됨. 설정 → 계정 → Google 계정 추가.
- **`Android Client ID`로 잘못 채움** — Credential Manager 의 `setServerClientId` 는 Web 클라이언트 ID 필요. Android 클라이언트 ID 를 넣으면 audience mismatch 로 검증 실패.

## 2. Kakao 셋업

### 콘솔 작업

1. https://developers.kakao.com → 기존 앱 (또는 새로 만들기) → **앱 설정 → 플랫폼 키**
2. 우측 **Android 플랫폼 등록**
3. 입력:
   - 패키지명: `kr.co.junu.indiekitdemo`
   - 마켓 URL / 스토어 ID: 비워두기 (출시 후 채움)
   - **키 해시**: Debug SHA-1 의 base64 인코딩 값
4. 저장
5. **제품 설정 → 카카오 로그인 → 일반**: 활성화 ON + OpenID Connect 활성화 ON (idToken 받으려면 필수)
6. **동의 항목**: 닉네임 "필수 동의" + 이메일은 선택 (받고 싶으면 "선택 동의")

### 키 해시 만들기

SHA-1 의 16진수 표현을 base64 로 변환. 한 줄 명령:

```bash
echo "E8:F8:7B:74:A4:EC:53:65:8D:15:37:E8:51:22:5E:AB:DD:16:69:7D" | tr -d ':' | xxd -r -p | base64
```

결과 예: `6Ph7dKTsU2WNFTfoUSJeq90WaX0=`. 이 값을 카카오 콘솔의 "키 해시" 자리에 넣는다.

### Gradle 의존성 — 자체 저장소 추가

Kakao SDK 는 Google Maven 이나 Maven Central 에 없고 자체 Nexus 저장소를 사용한다. `settings.gradle.kts` 에 한 줄 추가:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}
```

`libs.versions.toml`:
```toml
kakaoSdk = "2.20.6"
kakao-user = { group = "com.kakao.sdk", name = "v2-user", version.ref = "kakaoSdk" }
```

`indie-kit-auth/build.gradle.kts`:
```kotlin
api(libs.kakao.user)
```

### 코드 호출

```kotlin
IndieKitAuth.configure(
    context = applicationContext,
    googleServerClientId = null,
    kakaoNativeAppKey = "31de3507f753e036c2e65a3f8994d018",
    backend = null,
)

// Activity 안 코루틴
val user = IndieKitAuth.signIn(activity, AuthProvider.KAKAO)
```

라이브러리가 `configure(context, kakaoNativeAppKey)` 호출 시 `KakaoSdk.init(context, kakaoNativeAppKey)` 를 자동 실행. 별도 초기화 불필요.

### 자주 막히는 곳

- **`SdkError code=2` + "Android keyHash mismatch"** — 콘솔의 키 해시와 실제 빌드의 SHA-1 base64 가 다름. Debug 빌드 / Release 빌드 / Play App Signing 각 SHA-1 가 모두 다르니 필요한 만큼 콘솔에 키 해시를 여러 줄 등록.
- **`AuthFailureReason.Misconfigured`** — 카카오 로그인 → 일반 → OpenID Connect 가 OFF. ON 으로.
- **카카오톡 앱이 안 깔린 폰에서 웹 폴백 안 됨** — 정상 동작. 카카오 SDK 가 자동 분기 (앱 있으면 앱, 없으면 웹). 사용자가 본인 폰에 카카오톡 설치 여부만 확인.

## 3. 서버 세션 발급 — SessionExchangeAdapter

iOS 와 같은 패턴. 라이브러리 본체는 백엔드 형식을 모름 — `SessionExchangeAdapter` 의 두 람다 (`translateRequest` / `translateResponse`) 로 앱이 자기 백엔드 형식을 채움.

```kotlin
val adapter = SessionExchangeAdapter(
    url = "https://www.junu.co.kr/auth/social-login/",
    translateRequest = { provider, idToken, _ ->
        // 앱의 백엔드 형식에 맞춰 JSON 문자열로.
        Json.encodeToString(MyRequest.serializer(), MyRequest(
            login_type = when (provider) {
                AuthProvider.GOOGLE -> "GGL"
                AuthProvider.KAKAO  -> "KKO"
            },
            id_token = idToken ?: "",
            bundle_id = "kr.co.junu.indiekitdemo",
            os_type = "ANDROID",
            device_name = Build.MODEL,
        ))
    },
    translateResponse = { body ->
        val resp = Json.decodeFromString<MyResponse>(body.decodeToString())
        SessionTokens(access = resp.data.access_token, refresh = resp.data.refresh_token)
    },
)
```

라이브러리가 자동으로 `signIn(...)` 호출 후 `backend.exchange(...)` 도 한 번 더 호출 → 응답을 `currentUser.serverSession` 으로 채워줌.

## 4. IndieKitNetwork 와 자동 연결

iOS 자매와 같은 식.

```kotlin
val net = IndieKitNetwork(
    baseURL = "https://api.junu.co.kr",
    tokenProvider = { IndieKitAuth.accessToken },
    tokenRefresher = { /* refresh token 으로 재발급 또는 재로그인 */ },
)
```

이 자동 연결 검증은 별도 차례.

## 5. 회원 탈퇴 흐름

`IndieKitAuth.unlink(activity)` 가 하는 일:

| 어디 | 무엇이 끊김 |
|---|---|
| 제공자 측 (Google / Kakao) | "이 앱이 사용자 정보에 접근할 권한" 사라짐. 다음 로그인 때 동의 화면 다시 봄. |
| 로컬 앱 | `currentUser` 가 null. 토큰 비움. |

**여기까지가 라이브러리 일.**

진짜 회원탈퇴는 우리 서버의 User row 도 삭제해야 함. 별도로 `DELETE /auth/delete/` 같은 끝점 호출 필요. junu 서버는 이미 `POST /auth/delete/` 끝점이 있어 거기로 호출. 출시 앱에서는 두 가지 (제공자 unlink + 서버 row 삭제) 를 같이 묶어야 사용자가 "탈퇴" 라고 인식하는 행동이 된다.

## 검증 끝나면 확인할 자리

데모 앱이 통과 기준으로 보여줘야 할 것들:
- 결과 카드에 `provider` (`google` / `kakao`), `id`, `email`, `displayName`, `idToken` 앞부분, `serverSession` (백엔드 연결 시 채워짐) 표시
- 로그아웃 / 회원 탈퇴 버튼 동작
- 본문 미리보기 카드에 서버로 보낼 JSON 표시 (디버깅용)

`MainActivity.kt` 의 `describeAuthError(_:)` 헬퍼가 IndieKitAuthError 의 case + underlying 의 도메인 / 메시지를 화면에 풀어 보여 줌 — 진단에 유용.
