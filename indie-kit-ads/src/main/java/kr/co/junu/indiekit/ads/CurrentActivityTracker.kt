/*
 * CurrentActivityTracker.kt — IndieKitAds
 *
 * 역할
 *  - "지금 떠 있는 화면 (Activity)" 을 모듈이 스스로 알 수 있게 하는 추적자.
 *  - 유럽 광고 동의창 (UMP) 은 띄울 화면이 반드시 필요한데, 앱이 configure 를
 *    화면이 이미 만들어진 *뒤* 에 부르면 (예: 첫 안내가 끝난 다음) "화면이 새로
 *    만들어지는 순간" 을 기다리는 방식으로는 그 실행에서 기회가 다시 오지 않는다.
 *    화면이 하나뿐이고 회전해도 다시 안 만드는 앱이면 영영 못 잡는다.
 *  - 그래서 앱이 켜질 때 (어떤 화면보다 먼저) 이 추적자를 심어 두고, 동의 절차가
 *    돌아야 하는 순간에 "지금 화면" 을 바로 꺼내 쓴다.
 *    iOS 자매가 topViewController() 로 그 순간의 맨 위 화면을 찾는 것과 같은 원리.
 *
 * 주요 개념
 *  - 심는 시점: 아래 IndieKitAdsInitProvider (ContentProvider) 가 맡는다.
 *    안드로이드는 앱의 ContentProvider 들을 Application.onCreate 보다도 먼저 깨우므로,
 *    첫 화면이 만들어지기 전에 추적이 확실히 시작된다. 앱 쪽 코드는 한 줄도 필요 없다
 *    (매니페스트 자동 합침). Firebase 등 주요 라이브러리가 쓰는 표준 방식.
 *  - 화면은 WeakReference 로만 쥔다 — 화면이 닫힌 뒤에도 모듈이 붙잡아 메모리가
 *    새는 일을 막는다.
 *
 * 주의사항
 *  - install 은 여러 번 불려도 한 번만 심는다 (ContentProvider + configure 양쪽에서
 *    부르므로 중복 방어 필수).
 */

package kr.co.junu.indiekit.ads

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference

/** 지금 떠 있는 화면 (Activity) 추적자. object 싱글턴. */
internal object CurrentActivityTracker {

    /** 중복 심기 방지 표식. install 이 두 곳 (ContentProvider / configure) 에서 불리므로 필요. */
    @Volatile
    private var installed = false

    /** 가장 최근에 만들어지거나 앞에 나온 화면. 약한 참조 — 닫힌 화면을 붙잡지 않는다. */
    @Volatile
    private var currentRef: WeakReference<Activity>? = null

    /** 지금 떠 있는 화면. 없으면 (아직 안 만들어짐 / 모두 닫힘) null. */
    val current: Activity?
        get() = currentRef?.get()

    /**
     * 추적을 시작한다. 여러 번 불려도 한 번만 심는다.
     *
     * @param app Application. 화면 수명 신호 (만들어짐 / 앞에 나옴 / 닫힘) 를 여기서 받는다.
     */
    @Synchronized
    fun install(app: Application) {
        if (installed) return
        installed = true

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // 만들어짐 / 시작됨 / 앞에 나옴 — 셋 다 "지금 화면" 갱신.
            // 만들어짐만 들으면, 추적 시작 전에 이미 만들어진 화면을 영영 못 잡기 때문.
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentRef = WeakReference(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                currentRef = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                currentRef = WeakReference(activity)
            }

            override fun onActivityDestroyed(activity: Activity) {
                // 닫힌 화면이 마침 "지금 화면" 이면 비운다. 다른 화면이 이미 자리를
                // 물려받았으면 (앞에 나옴 신호가 먼저 옴) 건드리지 않는다.
                if (currentRef?.get() === activity) {
                    currentRef = null
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }
}

/**
 * 추적자를 앱 시작 순간 (어떤 화면보다 먼저) 심는 자동 시동 장치.
 *
 * 안드로이드는 매니페스트에 적힌 ContentProvider 를 Application.onCreate 보다 먼저
 * 깨운다. 이 성질을 빌려 "앱 쪽 코드 없이" 추적을 시작한다. 자료 제공 기능은 전혀
 * 없으므로 아래 다섯 함수는 모두 빈 껍데기다.
 *
 * 매니페스트 등록은 이 모듈의 AndroidManifest.xml 에 있고, 사용처 앱의 매니페스트와
 * 자동으로 합쳐진다 (authorities 는 앱 패키지 이름을 앞에 붙여 앱마다 겹치지 않는다).
 */
public class IndieKitAdsInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        // 이 시점의 context 는 Application (또는 그 감싸개). 못 얻는 별난 환경이면
        // 건너뛴다 — configure 쪽 install 이 뒤늦게라도 다시 심는다.
        val app = context?.applicationContext as? Application
            ?: context as? Application
            ?: return true
        CurrentActivityTracker.install(app)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
