# indie-kit-android — 푸시 모듈 (indie-kit-push) 설계

> **상태 (2026-07-19):** 설계 완료, 개발 대기.
> **단계:** 6 (통계 1 → 광고 2 → 네트워크 3 → 결제 4 → 로그인 5 → 푸시 6)
> **자매 문서:** `indie-kit-ios/PUSH_PLAN.md` — 같은 진입점 이름, 같은 서버 약속.
> **목표 태그:** `v0.8.0`

## 1. 왜 만드는가

알림 기능은 어느 앱이든 배관 코드가 거의 같다 — 권한 요청, 기기의 알림 주소 받기, 주소가 바뀌면 갱신, 그 주소를 우리 서버에 올리기, 도착한 알림 띄우기. 이걸 앱마다 다시 짜지 않도록 한 번 잘 만들어 둔다.

알림의 *내용* (무슨 문구를 언제 보내는지, 눌렀을 때 어느 화면으로 가는지) 은 앱마다 다르므로 모듈이 맡지 않는다. 모듈은 배관만 맡는다.

## 2. 정해진 갈림길

| 갈림길 | 정해진 답 | 의미 |
|---|---|---|
| 보내는 방법 | **Firebase 메시징 (FCM)** | Android 는 구글의 알림 전송 서비스가 사실상 유일한 방법이다. 우리 서버는 애플용 (iOS, 직접) 과 구글용 (Android, FCM) 두 벌의 보내는 코드를 유지한다. |
| 로컬 예약 알림 | **함께 넣기** | 서버 없이 기기 안에서 예약하는 알림 (예: 매일 저녁 8시 운동 리마인드) 도 이 모듈이 지원한다. 권한 요청과 알림 띄우기 배관이 서버 푸시와 같기 때문. |
| 등록 조건 | **로그인 없이도 등록** | 알림 주소는 기기만으로 익명 등록한다. 나중에 로그인하면 그 사용자와 연결한다. 로그인 없는 앱 (TapCounter, TouchCart) 도 전체 공지를 보낼 수 있다. |

## 3. 모듈이 하는 일 / 하지 않는 일

하는 일 (배관)

1. 알림 권한 요청 (Android 13 부터 필요) + 현재 권한 상태 조회
2. Firebase 메시징에서 기기의 알림 주소 (등록 토큰) 받기 + 바뀔 때 감지
3. 알림 주소를 우리 서버에 올리기 (익명 기기 등록, 로그인 시 사용자 연결)
4. 알림 채널 만들기 (기본 채널 1개, 이름은 configure 때 앱이 정함)
5. 앱이 켜져 있을 때 도착한 알림을 직접 띄우기 (Android 는 앱이 앞에 있으면 자동으로 안 뜸)
6. 알림을 눌렀을 때 내용물을 앱에 전달 (앱이 화면 이동을 결정)
7. 기기 안 예약 알림 — 등록 / 취소 / 목록 조회
8. 시스템 알림 설정 화면 열기

하지 않는 일 (앱 몫)

1. 알림 문구와 보내는 시점 결정
2. 알림을 누른 뒤 어느 화면으로 갈지 (모듈이 넘겨준 내용물을 보고 앱이 이동)
3. 서버에서 실제로 보내는 일 (우리 서버 `junu_server_2025` 의 몫 — 6번 항목의 약속만 지키면 됨)

## 4. 공개 진입점

다른 모듈과 같은 모양, iOS 와 같은 이름 — 앱 시작 시 `configure` 한 번, 이후 어디서든 호출.

```kotlin
import com.junulabs.indiekit.push.IndieKitPush

// 1. Application.onCreate 에서 한 번:
IndieKitPush.configure(
    context = this,
    registration = PushRegistration(
        url = "https://api.junulabs.com/api/push/devices/",
        appId = "solti"                   // 서버가 앱을 구분하는 이름
    ),
    channelName = "알림",                  // 기본 알림 채널 이름 (설정 앱에 표시됨)
    onTap = { payload ->
        // 알림을 눌러 앱이 열렸을 때. payload.route 를 보고 앱이 화면 이동.
        router.go(payload.route)
    }
)

// 2. 적절한 시점 (온보딩 끝 / 설정 화면) 에 권한 요청 — Activity 필요:
val granted = IndieKitPush.requestPermission(activity)
// granted 가 true 면 모듈이 알아서: 알림 주소 받기 → 서버에 올리기 까지 진행.
// Android 12 이하는 권한 개념이 없으므로 항상 true.

// 3. 로그인 / 로그아웃 시 (로그인 쓰는 앱만):
IndieKitPush.linkUser("u_123")     // 이 기기를 해당 사용자와 연결
IndieKitPush.unlinkUser()          // 연결 해제 (익명 기기로 되돌림)

// 4. 기기 안 예약 알림:
IndieKitPush.scheduleLocal(
    id = "workout-reminder",
    title = "운동할 시간이에요",
    body = "오늘의 타이머가 기다리고 있어요",
    trigger = PushTrigger.Daily(hour = 20, minute = 0)   // 매일 저녁 8시
)
IndieKitPush.cancelLocal(id = "workout-reminder")
val pending = IndieKitPush.pendingLocal()                // 예약된 알림 목록

// 5. 보조 도구:
val status = IndieKitPush.permissionStatus()   // 허용 / 거부 / 아직 안 물음
IndieKitPush.openSystemSettings()              // 시스템 알림 설정 화면 열기
```

예약 알림의 `trigger` 는 세 가지: `Daily(hour, minute)` (매일 같은 시각), `Once(instant)` (한 번), `Weekly(dayOfWeek, hour, minute)` (매주 같은 요일). iOS 와 같은 세 종류.

## 5. 앱에 붙이는 방법

iOS 와 달리 AppDelegate 연결이 없다. 대신 다음 세 가지가 자동 / 반자동으로 붙는다.

1. **알림 수신 서비스** — 모듈이 `FirebaseMessagingService` 상속 서비스를 자기 매니페스트에 선언해 둔다. 앱은 아무것도 안 해도 주소 갱신 / 수신이 모듈로 들어온다.
2. **누름 감지** — 알림을 누르면 앱의 시작 Activity 가 열리며 내용물이 intent 에 실려 온다. 앱의 시작 Activity 에서 한 줄만 넘겨준다.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    IndieKitPush.handleIntent(intent)     // 알림으로 열렸으면 onTap 이 불린다. 아니면 아무 일 없음.
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    IndieKitPush.handleIntent(intent)     // 앱이 이미 떠 있는 상태에서 눌렀을 때
}
```

3. **Firebase 설정 파일** — 앱 모듈에 `google-services.json` 필요 (통계 모듈과 같은 파일을 같이 쓴다. 이미 통계를 쓰는 앱이면 추가 작업 없음).

## 6. 서버에 올리는 약속 (기기 등록)

두 플랫폼이 같은 끝점, 같은 형식을 쓴다. 우리 서버 (`junu_server_2025`) 에 새 담당 (push 앱) 을 만들어 받는다.

```
POST /api/push/devices/
Content-Type: application/json

{
  "app":         "solti",            // 어느 앱인가
  "platform":    "android",          // ios | android
  "token":       "dQw4...",          // 알림 주소 (Android 는 FCM 등록 토큰)
  "device_id":   "F3A9-...",         // 설치마다 하나 만드는 고정 기기 이름 (모듈이 생성·보관)
  "user_id":     null,               // 로그인 전엔 null, linkUser 후엔 사용자 이름
  "locale":      "ko-KR",
  "app_version": "1.2.0",
  "os_version":  "34"                // Android 는 API 레벨
}
```

규칙

1. 같은 `device_id` 로 다시 오면 덮어쓴다 (새로 만들지 않는다) — 주소 갱신 / 사용자 연결이 모두 이 한 끝점으로 해결.
2. 모듈은 마지막으로 올린 내용을 기억해 두고, **바뀌었을 때만** 다시 올린다 (앱 켤 때마다 서버를 두드리지 않는다).
3. 올리기에 실패하면 조용히 넘어가고 다음 앱 시작 때 다시 시도한다 (알림 등록 실패가 앱 사용을 막으면 안 됨).

서버가 보낼 때는 Firebase 관리 도구 (firebase-admin) 로 보낸다. 앱마다 Firebase 프로젝트가 따로이므로 **앱 수만큼 서비스 계정 키가 서버 설정에 필요**하다. 서버 쪽 구현은 이 문서 범위 밖 (서버 저장소에서 진행).

## 7. 알림 내용물 형식

서버가 보내는 알림과 모듈이 앱에 넘겨주는 내용물의 약속. 두 플랫폼 동일.

```
알림 = 제목 + 본문 + 데이터
데이터 안에 "route" 열쇠 하나는 예약됨 — 앱이 화면 이동에 쓴다.
```

FCM 으로 보낼 때의 실제 모양 — 알림 부분과 데이터 부분을 둘 다 싣는다:

```json
{
  "message": {
    "token": "dQw4...",
    "notification": { "title": "새 기능 소식", "body": "위젯이 추가됐어요" },
    "data": { "route": "settings/whatsnew", "campaign": "v3.2-widget" }
  }
}
```

둘 다 싣는 이유: 앱이 꺼져 있거나 뒤에 있으면 시스템이 `notification` 부분으로 알아서 띄워 주고, 앱이 앞에 있으면 모듈이 `data` 까지 합쳐 직접 띄운다. 어느 쪽이든 누르면 모듈이 `PushPayload(title, body, route, extras)` 로 만들어 `onTap` 에 넘긴다. 예약 알림을 눌렀을 때도 같은 `onTap` 이 같은 모양으로 불린다.

## 8. 외부 의존성

| 의존성 | 왜 |
|---|---|
| `firebase-messaging` | 알림 주소 받기 + 수신. 통계 모듈과 같은 Firebase 라 설정 파일도 같이 씀 |
| `indie-kit-core` | 로깅 / 에러 (기존 내부 묶음) |

예약 알림은 OS 기본 도구 (AlarmManager + BroadcastReceiver + NotificationManager) 만 쓴다 — 추가 의존성 없음.

## 9. 만드는 순서

1. 라이브러리 본체 — 권한 / 주소 받기 / 등록 / 채널 / 띄우기 / 예약 알림
2. 데모 앱에 붙여 검증 — 권한 팝업, 예약 알림 울림, 누름 → onTap 확인. 서버 푸시는 Firebase 콘솔의 시험 발송으로 확인 (서버 없이 가능)
3. 서버 저장소에 기기 등록 끝점 + 보내기 담당 추가 (별도 세션, `Server/`)
4. 실기기에서 끝까지 검증 — 등록 → 서버에서 발송 → 기기 도착 → 누름 → 화면 이동
5. 태그 `v0.8.0` — README / PLAN.md / CHANGELOG.md 갱신. JitPack 첫 요청 빌드 주의 (jitpack.yml 그대로)
6. 첫 실전 통합 (아마 SolTi)

## 10. 주의사항

1. **Android 13 부터 알림 권한이 필수다.** 권한 없이는 알림이 안 뜬다 (에러도 없이 조용히). `requestPermission(activity)` 를 부르는 걸 앱 통합 점검 목록에 넣는다.
2. **예약 알림의 시각 정확도.** 정확한 시각 알람은 Android 12 부터 특별 권한이 필요하고 스토어 정책 심사 대상이다. 기본은 **몇 분 오차를 허용하는 방식** (setWindow) 으로 하고, 분 단위 정확도가 꼭 필요한 앱만 옵션으로 정확 알람을 켠다 — 리마인드 용도엔 기본으로 충분.
3. **알림 주소는 앱을 지웠다 깔면 바뀐다.** 오래된 주소로 보내면 FCM 이 "등록 안 됨" 오류를 돌려준다 — 서버가 그 주소를 지우는 처리까지 해야 한다 (서버 작업 때 반영).
4. **에뮬레이터도 서버 푸시를 받을 수 있다** (구글 Play 이미지 + 구글 계정 로그인 상태). iOS 시뮬레이터보다 검증이 쉬우므로 데모 검증 때 실제 발송까지 확인한다.
5. **서비스 계정 키는 서버에만 둔다.** 모듈 / 앱 / 이 저장소 어디에도 넣지 않는다. 키 보관은 `Docs/keystore/` 규칙을 따른다.

## 11. 데모 앱에 로컬로 붙일 때 (다음 세션용 메모)

데모 앱 (`Apps/IndieKitExample/indieKitDemo_Android/`) 세션에서 할 일. 2026-07-19 조사 결과.

1. 데모의 `settings.gradle.kts` 에 **로컬 연결 (composite build) 블록이 주석으로 준비**되어 있다 (지금은 JitPack 출시본 검증 중이라 꺼 둔 상태). 주석을 풀고, substitute 목록에 한 줄 추가:
   `substitute(module("kr.co.junu:indie-kit-push")).using(project(":indie-kit-push"))`
2. 데모 `app/build.gradle.kts` 의 dependencies 에 `implementation("kr.co.junu:indie-kit-push:...")` 추가 (좌표 버전은 데모의 libs.versions.toml 방식 따름 — substitute 가 로컬 모듈로 바꿔치므로 버전 숫자는 형식만 맞으면 됨).
3. Application.onCreate 에 configure, 시작 Activity 의 onCreate / onNewIntent 에 `IndieKitPush.handleIntent(intent)` (5번 항목 코드 그대로). 서버 끝점은 아직 없으므로 아무 URL 이나 — "등록 실패 → 경고 로그만, 앱 정상" 도 검증 항목.
4. 푸시 데모 화면: 권한 요청 / 권한 상태 / 예약 알림 등록·취소·목록 / 마지막 onTap 내용 표시.
5. 서버 푸시 수신: 데모 앱의 Firebase 콘솔 > Messaging > 시험 메시지 (기기 토큰 지정) 로 서버 없이 확인 가능. 토큰은 Logcat (`IndieKit.push`) 만 뒤지지 말고 데모 화면에 표시해 두면 편하다 — FirebaseMessaging.getInstance().token 을 화면에 보여 주는 줄 추가 권장.
6. 에뮬레이터는 구글 Play 이미지 + 구글 계정 로그인 상태여야 FCM 수신이 된다.
