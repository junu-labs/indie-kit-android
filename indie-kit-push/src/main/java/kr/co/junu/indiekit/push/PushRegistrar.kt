/*
 * PushRegistrar.kt — IndieKitPush
 *
 * 역할
 *  - 알림 주소 (FCM 등록 토큰) 를 우리 서버에 올리는 일을 도맡는 담당.
 *  - "바뀌었을 때만 올린다 / 실패해도 앱을 막지 않는다 / 다음 앱 시작 때 다시 시도한다"
 *    — PUSH_PLAN.md 6번 항목의 세 규칙이 전부 여기 구현되어 있다.
 *  - iOS 자매 (PushRegistrar actor) 와 1:1 대칭.
 *
 * 주요 개념
 *  - 지문 (fingerprint): 등록 본문의 JSON 문자열 (열쇠 순서 고정).
 *    마지막 성공 지문과 같으면 서버를 두드리지 않는다.
 *  - Mutex 인 이유: 토큰 수신 (FCM 서비스) / 사용자 연결 (앱 어디서든) / 앱 시작 재시도가
 *    서로 다른 스레드에서 겹쳐 들어와도 상태 (저장소 읽고 쓰기 + 전송) 가 꼬이지 않게.
 *
 * 동작 흐름
 *  1. updateToken / updateUser 가 저장소에 새 값을 남기고
 *  2. syncIfNeeded 가 "지금 저장된 값" 으로 본문을 만들어
 *  3. 지문이 지난번과 다를 때만 POST → 성공하면 지문 갱신.
 *
 * 주의사항
 *  - 토큰이 아직 없으면 조용히 아무것도 안 한다.
 *  - 전송 실패는 경고 로그만 — 다음 syncIfNeeded (보통 다음 앱 시작) 가 다시 시도.
 *  - HTTP 는 OS 기본 (HttpURLConnection) 만 쓴다 — 이 모듈에 OkHttp 를 끌고 오지 않기 위함.
 */

package kr.co.junu.indiekit.push

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.co.junu.indiekit.core.IKLogger
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** 기기 등록 담당. 토큰 / 사용자 변경을 받아 두고, 바뀌었을 때만 서버에 올린다. */
internal class PushRegistrar(
    context: Context,
    private val registration: PushRegistration
) {

    private val appContext = context.applicationContext
    private val store = PushDeviceStore(appContext)

    /** 전송 작업이 도는 마당 — IO 스레드, 하나 실패해도 다른 작업에 안 번짐. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 저장소 읽고 쓰기 + 전송을 한 번에 하나씩만. */
    private val mutex = Mutex()

    // MARK: 값 갱신

    /** FCM 이 새 알림 주소를 줬을 때. 저장 후 바로 서버 동기화 시도. */
    fun updateToken(token: String) {
        scope.launch {
            mutex.withLock {
                store.token = token
                syncLocked()
            }
        }
    }

    /** 사용자 연결 / 해제 (linkUser / unlinkUser). 저장 후 바로 서버 동기화 시도. */
    fun updateUser(userId: String?) {
        scope.launch {
            mutex.withLock {
                store.userId = userId
                syncLocked()
            }
        }
    }

    /** 지금 저장된 값으로 동기화 시도 — configure 때 한 번 불린다 (지난 실행 실패분 재시도). */
    fun syncIfNeeded() {
        scope.launch {
            mutex.withLock { syncLocked() }
        }
    }

    // MARK: 서버 동기화 (mutex 안에서만 호출)

    private fun syncLocked() {
        // 토큰이 아직 없으면 올릴 게 없다 (FCM 발급 전).
        val token = store.token ?: return

        val body = DeviceRegistrationBody(
            app = registration.appId,
            platform = "android",
            token = token,
            deviceId = store.deviceId,
            userId = store.userId,
            locale = Locale.getDefault().toString(),
            appVersion = appVersion(),
            osVersion = Build.VERSION.SDK_INT.toString()
        )

        val fingerprint = body.toJson()
        if (fingerprint == store.lastUploadFingerprint) {
            IKLogger.push.debug("기기 등록 내용이 지난번과 같음 — 서버 호출 생략")
            return
        }

        try {
            val connection = URL(registration.url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Content-Type", "application/json")
                for ((key, value) in registration.headers) {
                    connection.setRequestProperty(key, value)
                }
                connection.outputStream.use { it.write(fingerprint.toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                if (code in 200..299) {
                    store.lastUploadFingerprint = fingerprint
                    IKLogger.push.info("기기 등록 성공 (app=${registration.appId})")
                } else {
                    IKLogger.push.warning("기기 등록 실패 (응답 코드 $code) — 다음 앱 시작 때 재시도")
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            // 네트워크 끊김 등 — 앱 사용을 막지 않고 조용히 넘어간다.
            IKLogger.push.warning("기기 등록 전송 오류: ${error.message} — 다음 앱 시작 때 재시도")
        }
    }

    /** 앱 버전 (versionName). 없으면 "0". */
    private fun appVersion(): String {
        return try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0"
        } catch (ignored: Exception) {
            "0"
        }
    }
}
