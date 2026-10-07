/*
 * AppVersionCheck.kt — IndieKitNetwork
 *
 * 역할
 *  - 앱 버전 확인 (업데이트 알림) — junu 서버에 "지금 나온 버전" 을 묻고,
 *    창을 띄울지 (강제 / 선택 / 안 띄움) 를 돌려준다. 창 모양은 앱이 그린다 (앱의 알림창 부품 + 고양이).
 *
 * 왜 모듈에 (2026-10)
 *  - 앱마다 AppVersionChecker 를 따로 짰다 (요청 · 버전 비교 · "선택 업데이트는 버전마다 한 번" 기억).
 *    같은 규칙이라 모듈로 모으고, 앱은 한 줄로 부른다. 앱이 가진 AppVersionChecker 는
 *    수정 요청으로 하나씩 이걸로 옮긴다.
 *
 * 규칙 (module-network 스킬과 같음)
 *  - 요청: POST {서버}/compliance/app-version/  몸 {"slug": …, "os_type": "ANDROID"}, 인증 없음.
 *  - 답: {"status": "SUCCESS", "data": {"version": "1.2.0", "force_update": false}}.
 *    SUCCESS 가 아니거나 실패하면 **조용히 null** (창 안 띄움).
 *  - 서버 버전이 더 높을 때만: 강제면 FORCED (매번), 선택이면 서버 버전마다 한 번만 OPTIONAL.
 *  - 버전 비교: 점으로 나눈 숫자만. `-` · `+` 뒤는 버린다. 숫자가 아닌 칸이 있으면 비교하지 않는다. 모자란 칸은 0.
 *  - 서버: 디버그 빌드 (앱이 debuggable) dev.junu.co.kr, 출시 빌드 api.junu.co.kr (모듈이 고른다).
 *
 * 사용 방법 (앱 측)
 *  ```kotlin
 *  // 뿌리 컴포저블에서 (튜토리얼 · 캡처 · 시험 실행이 아닐 때) 한 번:
 *  LaunchedEffect(Unit) {
 *      prompt = AppVersionCheck.check(context, slug = BuildConfig.JL_SERVER_SLUG)   // null | OPTIONAL | FORCED
 *  }
 *  // 업데이트 버튼:
 *  AppVersionCheck.openStore(context)
 *  ```
 *
 * iOS 자매: `AppVersionCheck.swift` — 같은 규칙, 같은 이름.
 */

package kr.co.junu.indiekit.network

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 업데이트 창 종류. */
public enum class AppUpdatePrompt {
    /** 선택 — "업데이트" / "나중에". 서버 버전마다 한 번만. */
    OPTIONAL,
    /** 강제 — 닫을 수 없는 창, "업데이트" 한 버튼. 앱으로 돌아와도 다시 뜬다. */
    FORCED
}

/** 서버가 알려 준 최신 버전. */
@Serializable
public data class AppVersionInfo(
    val version: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false
)

/** 앱 버전 확인 (업데이트 알림). */
public object AppVersionCheck {

    /** 서버 주소 — 디버그 빌드. */
    public const val DEBUG_SERVER_URL: String = "https://dev.junu.co.kr"

    /** 서버 주소 — 출시 빌드 (내부 테스트 포함). */
    public const val RELEASE_SERVER_URL: String = "https://api.junu.co.kr"

    /** 서버에 묻는 경로. */
    public const val PATH: String = "/compliance/app-version/"

    /** "선택 업데이트 창을 이미 보여 준 서버 버전" 을 기억하는 키. iOS 자매와 같은 이름. */
    public const val LAST_SHOWN_OPTIONAL_KEY: String = "lastShownOptionalUpdateVersion"

    /** 기억을 담는 SharedPreferences 이름 (모듈 몫 — 앱 설정과 섞이지 않게). */
    internal const val PREFS_NAME: String = "indiekit_app_version"

    /** 이 빌드가 쓸 서버 주소 — 앱이 debuggable 이면 dev, 아니면 api. (AdUnitID 와 같은 기준) */
    public fun defaultServerURL(context: Context): String =
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) DEBUG_SERVER_URL
        else RELEASE_SERVER_URL

    // ────────────────────────────────────────────────────────────────────
    // 한 줄로
    // ────────────────────────────────────────────────────────────────────

    /**
     * 서버에 최신 버전을 묻고, 띄울 창을 돌려준다. 실패하면 조용히 null.
     *
     * 선택 업데이트를 돌려줄 때는 그 서버 버전을 기억해 두어 다음부터는 null 이 된다
     * (그래서 돌려받은 OPTIONAL 은 바로 보여 준다). 디스크 일은 뒷실에서 한다 — 어느 실에서 불러도 된다.
     *
     * @param slug 서버에 등록한 앱 이름 (`BuildConfig.JL_SERVER_SLUG`). 비면 묻지 않고 null.
     * @param currentVersion 지금 앱 버전. null 이면 패키지 정보의 versionName.
     * @param network 보낼 통로. null 이면 [defaultServerURL] · 5초 제한으로 만든다 (시험에서 바꿔 끼움).
     */
    public suspend fun check(
        context: Context,
        slug: String,
        currentVersion: String? = null,
        network: IndieKitNetwork? = null
    ): AppUpdatePrompt? {
        if (slug.isEmpty()) return null
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            val current = currentVersion ?: runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull() ?: return@withContext null

            val net = network ?: IndieKitNetwork(baseURL = defaultServerURL(appContext), timeoutMs = 5_000L)
            val info = fetch(slug, net) ?: return@withContext null

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val prompt = decide(info, current, prefs.getString(LAST_SHOWN_OPTIONAL_KEY, null))
            if (prompt == AppUpdatePrompt.OPTIONAL) {
                prefs.edit().putString(LAST_SHOWN_OPTIONAL_KEY, info.version).apply()
            }
            prompt
        }
    }

    /** 서버에 최신 버전만 묻는다. `status` 가 SUCCESS 가 아니거나 실패하면 null. */
    public suspend fun fetch(slug: String, network: IndieKitNetwork): AppVersionInfo? = try {
        val envelope: Envelope = network.post(path = PATH, body = VersionRequest(slug, "ANDROID"))
        if (envelope.status == "SUCCESS") envelope.data else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    /**
     * 띄울 창을 고른다 (서버에 묻지 않는 순수한 판단).
     *
     *  - 서버 버전이 더 높지 않으면 (또는 비교할 수 없으면) null.
     *  - 강제면 FORCED — 기억과 상관없이 매번.
     *  - 선택이면 이미 그 버전으로 창을 보여 줬으면 null, 아니면 OPTIONAL.
     */
    public fun decide(info: AppVersionInfo, currentVersion: String, lastShownOptional: String?): AppUpdatePrompt? {
        if (!isNewer(info.version, currentVersion)) return null
        if (info.forceUpdate) return AppUpdatePrompt.FORCED
        if (lastShownOptional == info.version) return null
        return AppUpdatePrompt.OPTIONAL
    }

    // ────────────────────────────────────────────────────────────────────
    // 버전 비교
    // ────────────────────────────────────────────────────────────────────

    /** "1.2.3-beta+5" → [1, 2, 3]. 숫자가 아닌 칸이 있으면 null (비교하지 않음). */
    public fun parse(version: String): List<Int>? {
        val core = version.trim().takeWhile { it != '-' && it != '+' }
        return core.split(".").map { part ->
            if (part.isEmpty() || !part.all { it.isDigit() }) return null
            part.toIntOrNull() ?: return null
        }
    }

    /** server 가 current 보다 높은지. 둘 중 하나라도 읽을 수 없으면 false. 모자란 칸은 0. */
    public fun isNewer(server: String, current: String): Boolean {
        val a = parse(server) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ────────────────────────────────────────────────────────────────────
    // 스토어
    // ────────────────────────────────────────────────────────────────────

    /** 플레이 스토어 앱 화면 주소 (스토어 앱). */
    public fun marketUri(packageName: String): Uri = Uri.parse("market://details?id=$packageName")

    /** 플레이 스토어 앱 화면 주소 (웹 — 스토어 앱이 없을 때). */
    public fun webStoreUrl(packageName: String): String =
        "https://play.google.com/store/apps/details?id=$packageName"

    /** 이 앱의 스토어 화면을 연다 — 스토어 앱이 없으면 웹으로. */
    public fun openStore(context: Context, packageName: String = context.packageName) {
        val market = Intent(Intent.ACTION_VIEW, marketUri(packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse(webStoreUrl(packageName)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(web) }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 내부 자료
    // ────────────────────────────────────────────────────────────────────

    @Serializable
    internal data class VersionRequest(val slug: String, @SerialName("os_type") val osType: String)

    @Serializable
    internal data class Envelope(val status: String = "", val data: AppVersionInfo? = null)
}
