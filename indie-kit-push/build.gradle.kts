// 역할
//  - kr.co.junu.indiekit.push 패키지의 안드로이드 라이브러리.
//  - 알림 배관 전부: 권한 요청 / FCM 알림 주소 발급·갱신 / 우리 서버 기기 등록 /
//    도착 알림 띄우기 / 누름 전달 / 기기 안 예약 알림.
//
// 6단계 (현재)
//  - IndieKitPush 진입점 + FCM 수신 서비스 + 예약 알림 알람 리시버 + 부팅 복원 리시버.
//  - 외부 의존성: firebase-bom + firebase-messaging, androidx-activity (권한 팝업 결과 수신),
//    kotlinx-coroutines (suspend 진입점). google-services 플러그인은 사용처 (앱) 가 적용.
//
// 발행 좌표
//  - kr.co.junu:indie-kit-push:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.push"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // android.util.Log 등 안드로이드 SDK 정적 호출이 단위 테스트에서 throw 하지 않고 default (no-op) 반환.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    // Kotlin 컴파일러는 Gradle 데몬 JDK 위에서 돌고, 바이트코드만 JVM 17 로.
    // jvmToolchain 을 쓰지 않는 사유는 settings.gradle.kts 의 도구 체인 정책 주석 참고.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Core 의 공개 타입 (IKLogger 등) 을 사용처에 노출.
    api(project(":indie-kit-core"))

    // FCM — 알림 주소 발급 + 수신 서비스. BOM 으로 통계 모듈과 같은 시점.
    // implementation 사용 사유: 사용처 앱이 자기 BOM (보통 더 최신) 을 자유롭게 잡을 수 있도록 강제하지 않음.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // 권한 팝업 (Android 13+) 결과 수신 — ComponentActivity.activityResultRegistry.
    implementation(libs.androidx.activity)

    // NotificationManagerCompat / ContextCompat 등.
    implementation(libs.androidx.core.ktx)

    // suspend 진입점 (requestPermission / scheduleLocal 등).
    // api 노출 사유: suspend 함수 시그니처가 사용처 컴파일에 coroutines 타입을 요구.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-push",
        version = providers.gradleProperty("VERSION_NAME").get()
    )
    // Javadoc jar 끔 — Core 모듈 build.gradle.kts 의 같은 블록 주석 참고.
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false
        )
    )
}
