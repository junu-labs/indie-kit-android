/*
 * IndieKitPush.kt — IndieKitPush
 *
 * 역할
 *  - 푸시 모듈의 단일 진입점. 알림 배관 전부를 한 줄씩으로:
 *    권한 요청 / FCM 알림 주소 발급·갱신 / 우리 서버 기기 등록 / 도착 알림 띄우기 /
 *    누름 전달 / 기기 안 예약 알림 / 시스템 알림 설정 열기.
 *  - 알림의 내용 (문구 / 시점 / 누른 뒤 화면 이동) 은 앱 몫 — 모듈은 배관만 (PUSH_PLAN.md 3번 항목).
 *  - iOS 자매 (IndieKitPush) 와 진입점 이름 1:1 대칭.
 *
 * 주요 개념
 *  - 처음 설정: Application.onCreate 에서 한 번 `IndieKitPush.configure(...)`.
 *    configure 는 권한 팝업을 띄우지 않는다 — 앱이 적절한 순간에 `requestPermission(activity)` 호출.
 *  - onTap: 서버 푸시든 예약 알림이든, 누르면 같은 모양 (PushPayload) 으로 이 클로저가 불린다.
 *    메인 스레드에서 불린다 (화면 이동 코드를 바로 써도 안전).
 *  - handleIntent: 알림을 눌러 열린 시작 Activity 가 intent 를 넘겨주는 통로 (PUSH_PLAN.md 5번 항목).
 *
 * 사용 방법
 *  ```kotlin
 *  // Application.onCreate 에서 한 번:
 *  IndieKitPush.configure(
 *      context = this,
 *      registration = PushRegistration(url = 서버끝점, appId = "solti"),
 *      channelName = "알림",
 *      onTap = { payload -> router.go(payload.route) }
 *  )
 *  // 시작 Activity 의 onCreate / onNewIntent 에서:
 *  IndieKitPush.handleIntent(intent)
 *  // 온보딩 끝 등 적절한 순간 (코루틴 안):
 *  val granted = IndieKitPush.requestPermission(activity)
 *  ```
 *
 * 주의사항
 *  - 앱 모듈에 google-services.json 필요 (통계 모듈과 같은 파일을 같이 쓴다).
 *  - Android 13 부터 권한 없이는 알림이 조용히 안 뜬다 — requestPermission 호출을 잊지 말 것.
 */

package kr.co.junu.indiekit.push

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kr.co.junu.indiekit.core.IKLogger
import kotlin.coroutines.resume

/** 알림 권한의 현재 상태 — 세 갈래로 단순화. iOS 자매 (PushPermissionStatus) 와 대칭. */
public enum class PushPermissionStatus {
    /** 아직 한 번도 안 물어봄 (requestPermission 을 부르면 시스템 팝업이 뜬다). */
    NOT_ASKED,
    /** 허용됨. */
    GRANTED,
    /** 거부됨 (다시 팝업을 띄울 수 없을 수 있다 — openSystemSettings 로 설정 화면 안내). */
    DENIED
}

/** 예약된 기기 안 알림 한 건의 요약 (pendingLocal 조회 결과). iOS 자매와 대칭. */
public data class PendingLocalNotification(
    /** 등록 때 앱이 정한 이름. */
    val id: String,
    val title: String,
    val body: String
)

/** 푸시 모듈의 단일 진입점. */
public object IndieKitPush {

    // MARK: 내부 상태

    @Volatile private var appContext: Context? = null
    @Volatile private var registrar: PushRegistrar? = null
    @Volatile private var onTap: ((PushPayload) -> Unit)? = null

    /** onTap 을 메인 스레드로 모아 주는 손잡이. */
    private val mainHandler = Handler(Looper.getMainLooper())

    // MARK: 처음 설정

    /**
     * 푸시 모듈을 한 번 처음 설정한다. Application.onCreate 에서 부른다.
     *
     * 동작
     *  1. 알림 채널을 만든다 (channelName 이 시스템 설정 화면에 보이는 이름).
     *  2. FCM 에서 알림 주소를 받아 우리 서버에 등록한다 (바뀌었을 때만 실제 전송).
     *  3. 저장된 예약 알림들의 알람을 다시 건다 (앱 강제 종료 / 업데이트로 사라진 경우 대비).
     *
     * 권한 팝업은 **띄우지 않는다**. 앱이 적절한 순간에 `requestPermission(activity)` 를 부른다.
     *
     * @param context 아무 Context (applicationContext 만 보관한다).
     * @param registration 우리 서버 끝점 + 앱 이름. PushRegistration 참고.
     * @param channelName 기본 알림 채널의 이름 — 시스템 설정 화면에 사용자에게 보인다.
     * @param smallIconRes 알림 작은 아이콘 리소스. 0 (기본) 이면 앱 아이콘 사용.
     * @param onTap 알림을 눌러 앱이 열렸을 때 불리는 클로저. 메인 스레드에서 불린다.
     */
    public fun configure(
        context: Context,
        registration: PushRegistration,
        channelName: String = "알림",
        smallIconRes: Int = 0,
        onTap: (PushPayload) -> Unit
    ) {
        val app = context.applicationContext
        if (appContext != null) {
            IKLogger.push.debug("IndieKitPush.configure 재호출 — 설정을 새 값으로 교체")
        }
        appContext = app
        this.onTap = onTap

        val store = PushDeviceStore(app)
        store.channelName = channelName
        if (smallIconRes != 0) store.smallIconRes = smallIconRes
        NotificationPresenter.ensureChannel(app)

        val newRegistrar = PushRegistrar(app, registration)
        registrar = newRegistrar

        // FCM 알림 주소 확보 — 권한과 무관하게 발급된다 (권한은 "띄우기" 에만 필요).
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    logTokenIfDebuggable(app, token)
                    newRegistrar.updateToken(token)
                }
                .addOnFailureListener { error ->
                    IKLogger.push.warning("FCM 알림 주소 발급 실패: ${error.message} — 다음 앱 시작 때 재시도")
                }
        } catch (error: Exception) {
            // Firebase 시작 전 (google-services.json 누락 등) — 죽지 않고 로그만.
            IKLogger.push.error("FCM 접근 실패 — google-services.json 확인 필요: ${error.message}")
        }

        // 지난 실행에서 못 올린 등록 내용 재시도 (내용이 같으면 아무 일 없음).
        newRegistrar.syncIfNeeded()

        // 예약 알림 알람 복원 — 알람은 같은 id 로 다시 걸면 갈아끼워져 겹칠 걱정 없음.
        for (stored in store.scheduledNotifications()) {
            LocalAlarmScheduler.schedule(app, stored)
        }

        IKLogger.push.info("IndieKitPush 처음 설정 완료 (app=${registration.appId})")
    }

    // MARK: 권한

    /**
     * 알림 권한을 요청한다 (Android 13 부터 시스템 팝업, 12 이하는 팝업 없이 현재 상태 반환).
     *
     * @param activity 팝업 결과를 받을 Activity. Compose 앱의 Activity 는 모두 ComponentActivity.
     * @return 허용이면 true. 이미 허용 상태였으면 팝업 없이 true.
     */
    public suspend fun requestPermission(activity: ComponentActivity): Boolean {
        val store = PushDeviceStore(activity.applicationContext)
        store.permissionAsked = true

        // Android 12 이하 — 권한 개념이 없다. 설정에서 끈 경우만 false.
        if (Build.VERSION.SDK_INT < 33) {
            return NotificationManagerCompat.from(activity).areNotificationsEnabled()
        }

        // 이미 허용 상태면 팝업 없이 바로.
        val current = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (current == PackageManager.PERMISSION_GRANTED) return true

        // 팝업을 띄우고 결과를 기다린다.
        return suspendCancellableCoroutine { continuation ->
            var launcher: ActivityResultLauncher<String>? = null
            launcher = activity.activityResultRegistry.register(
                "indiekit-push-permission",
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                launcher?.unregister()
                IKLogger.push.info(if (granted) "알림 권한 허용" else "알림 권한 거부됨")
                if (continuation.isActive) continuation.resume(granted)
            }
            continuation.invokeOnCancellation { launcher?.unregister() }
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** 현재 권한 상태를 조회한다 (팝업 없음). configure 전이면 NOT_ASKED. */
    public fun permissionStatus(): PushPermissionStatus {
        val context = appContext ?: return PushPermissionStatus.NOT_ASKED
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return PushPermissionStatus.GRANTED
        }
        // 꺼져 있는 경우 — 한 번이라도 물어봤으면 (또는 팝업 개념이 없는 12 이하면) 거부로 본다.
        return if (PushDeviceStore(context).permissionAsked || Build.VERSION.SDK_INT < 33) {
            PushPermissionStatus.DENIED
        } else {
            PushPermissionStatus.NOT_ASKED
        }
    }

    // MARK: 사용자 연결

    /**
     * 이 기기를 로그인한 사용자와 연결한다 (로그인 성공 직후 호출).
     * 서버의 같은 기기 줄에 user_id 만 채워진다 — 익명 시절 등록과 겹치지 않는다.
     */
    public fun linkUser(userId: String) {
        val current = registrar
        if (current == null) {
            IKLogger.push.warning("configure 전에 linkUser 호출 — 무시합니다")
            return
        }
        current.updateUser(userId)
    }

    /** 사용자 연결을 해제한다 (로그아웃 직후 호출). 익명 기기로 되돌아간다. */
    public fun unlinkUser() {
        val current = registrar
        if (current == null) {
            IKLogger.push.warning("configure 전에 unlinkUser 호출 — 무시합니다")
            return
        }
        current.updateUser(null)
    }

    // MARK: 알림 누름 전달

    /**
     * 알림을 눌러 열린 intent 를 모듈에 넘겨준다.
     * 시작 Activity 의 onCreate 와 onNewIntent 양쪽에서 부른다 (PUSH_PLAN.md 5번 항목).
     * 알림으로 열린 게 아니면 아무 일도 안 한다. 같은 intent 를 두 번 넘겨도 한 번만 처리.
     */
    public fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val bundle = intent.extras ?: return

        when {
            // 1. 모듈이 직접 띄운 알림 (앞 화면 수신 / 예약 알림) 의 누름.
            bundle.getBoolean(NotificationPresenter.EXTRA_MARKER, false) -> {
                val dataKeys = bundle.getStringArray(NotificationPresenter.EXTRA_DATA_KEYS) ?: emptyArray()
                val extras = dataKeys.mapNotNull { key ->
                    bundle.getString(key)?.let { key to it }
                }.toMap()
                val payload = PushPayload(
                    title = bundle.getString(NotificationPresenter.EXTRA_TITLE) ?: "",
                    body = bundle.getString(NotificationPresenter.EXTRA_BODY) ?: "",
                    route = bundle.getString(NotificationPresenter.EXTRA_ROUTE),
                    extras = extras,
                    isLocal = bundle.getBoolean(NotificationPresenter.EXTRA_IS_LOCAL, false)
                )
                intent.removeExtra(NotificationPresenter.EXTRA_MARKER)   // 두 번 처리 방지
                dispatchTap(payload)
            }

            // 2. 앱이 뒤에 있을 때 시스템 알림판이 대신 띄운 서버 푸시의 누름.
            //    FCM 이 데이터 열쇠들을 intent 에 실어 준다 (제목 / 본문은 안 실어 줌 — PushPayload 주석 참고).
            bundle.containsKey("google.message_id") -> {
                var route: String? = null
                val extras = mutableMapOf<String, String>()
                for (key in bundle.keySet()) {
                    // FCM 관리용 열쇠는 거른다.
                    if (key.startsWith("google.") || key.startsWith("gcm.") ||
                        key == "from" || key == "collapse_key" || key == "message_type"
                    ) continue
                    val value = bundle.getString(key) ?: continue
                    if (key == "route") route = value else extras[key] = value
                }
                intent.removeExtra("google.message_id")   // 두 번 처리 방지
                dispatchTap(PushPayload(title = "", body = "", route = route, extras = extras, isLocal = false))
            }
        }
    }

    /** 예약 알림 갱신 열쇠 — FCM 서비스가 새 알림 주소를 받았을 때 (configure 전일 수도 있다). */
    internal fun internalOnNewToken(context: Context, token: String) {
        logTokenIfDebuggable(context, token)
        val current = registrar
        if (current != null) {
            current.updateToken(token)
        } else {
            // configure 전 (앱 프로세스를 FCM 이 깨운 경우) — 저장만 해 두면 다음 configure 가 올린다.
            PushDeviceStore(context).token = token
            IKLogger.push.debug("configure 전 알림 주소 수신 — 저장만 해 둠")
        }
    }

    // MARK: 기기 안 예약 알림

    /**
     * 기기 안 예약 알림 한 건을 등록한다. 같은 id 로 다시 등록하면 갈아끼운다.
     *
     * @param id 앱이 정하는 이름 (예: "workout-reminder"). 취소 / 재등록의 열쇠.
     * @param route 눌렀을 때 onTap 으로 넘어갈 화면 이동 열쇠. 없으면 null.
     * @param trigger 언제 울릴까 — Daily / Once / Weekly (PushTrigger 참고).
     * @param exact 분 단위 정확도가 꼭 필요할 때만 true — 앱 매니페스트에 SCHEDULE_EXACT_ALARM
     *              선언 + 사용자 허용이 있어야 하며, 없으면 몇 분 오차 방식으로 내려간다.
     */
    public fun scheduleLocal(
        id: String,
        title: String,
        body: String,
        route: String? = null,
        trigger: PushTrigger,
        exact: Boolean = false
    ) {
        val context = appContext
        if (context == null) {
            IKLogger.push.warning("configure 전에 scheduleLocal 호출 — 무시합니다")
            return
        }
        val stored = StoredLocalNotification(id, title, body, route, trigger, exact)
        PushDeviceStore(context).saveScheduled(stored)
        LocalAlarmScheduler.schedule(context, stored)
        IKLogger.push.info("예약 알림 등록: $id")
    }

    /** 예약 알림 한 건을 취소한다. 없는 id 면 아무 일 없음. */
    public fun cancelLocal(id: String) {
        val context = appContext ?: return
        PushDeviceStore(context).removeScheduled(id)
        LocalAlarmScheduler.cancel(context, id)
        IKLogger.push.info("예약 알림 취소: $id")
    }

    /** 예약된 알림 전부를 취소한다. */
    public fun cancelAllLocal() {
        val context = appContext ?: return
        val store = PushDeviceStore(context)
        for (stored in store.scheduledNotifications()) {
            LocalAlarmScheduler.cancel(context, stored.id)
        }
        store.clearScheduled()
        IKLogger.push.info("예약 알림 전체 취소")
    }

    /** 지금 예약되어 있는 알림 목록. */
    public fun pendingLocal(): List<PendingLocalNotification> {
        val context = appContext ?: return emptyList()
        return PushDeviceStore(context).scheduledNotifications().map {
            PendingLocalNotification(id = it.id, title = it.title, body = it.body)
        }
    }

    // MARK: 보조 도구

    /**
     * 시스템의 이 앱 알림 설정 화면을 연다.
     * 권한이 DENIED 인 사용자에게 "설정에서 켜 주세요" 안내 후 이동시킬 때 쓴다.
     */
    public fun openSystemSettings() {
        val context = appContext ?: return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // MARK: 내부 헬퍼

    /**
     * 앱이 디버그 빌드 (debuggable) 일 때만 토큰 원문을 Logcat 에 남긴다 — 서버 등록 대조 시험용.
     *
     * 라이브러리 자신의 BuildConfig.DEBUG 는 배포판 (JitPack release) 에서 항상 false 라 못 쓴다.
     * 대신 "사용하는 앱" 의 debuggable 플래그를 본다 — 앱을 디버그로 빌드했을 때만 찍힌다.
     */
    private fun logTokenIfDebuggable(context: Context, token: String) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            IKLogger.push.debug("알림 주소 값: $token")
        }
    }

    /** onTap 을 메인 스레드에서 부른다. */
    private fun dispatchTap(payload: PushPayload) {
        val tap = onTap
        if (tap == null) {
            IKLogger.push.warning("configure 전에 알림 누름 수신 — onTap 전달 못함")
            return
        }
        IKLogger.push.info("알림 누름 (route=${payload.route ?: "-"}, local=${payload.isLocal})")
        mainHandler.post { tap(payload) }
    }
}
