// 역할
//  - co.junu.indiekit.analytics 패키지의 안드로이드 라이브러리.
//  - Firebase Analytics 한 줄 추상화 + AnalyticsBus 자동 등록.
//
// 1단계 (현재)
//  - IndieKitAnalytics 진입점 (configure / log* / set*) 과 FirebaseAnalyticsRecorder 가 들어 있다.
//  - 외부 의존성: firebase-bom + firebase-analytics. google-services 플러그인은 사용처 (앱) 가 적용.
//
// 발행 좌표
//  - co.junu:indie-kit-analytics:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.analytics"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 주의: publishing { singleVariant(...) } 는 여기 두지 않는다.
    //       vanniktech maven-publish 플러그인이 이미 release variant 을 자동으로 잡고 sources jar 도 붙인다.

    testOptions {
        unitTests {
            // android.util.Log 등 안드로이드 SDK 정적 호출이 단위 테스트에서 throw 하지 않고 default (no-op) 반환.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    // Kotlin 컴파일러는 Gradle 데몬 JDK (Android Studio JBR 21) 위에서 돌고, 바이트코드만 JVM 17 로.
    // jvmToolchain 을 쓰지 않는 사유는 settings.gradle.kts 의 도구 체인 정책 주석 참고.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Core 의 공개 타입 (AnalyticsValue / AnalyticsBus / IKLogger 등) 을 사용처에 노출.
    api(project(":indie-kit-core"))

    // Firebase Analytics — BOM 으로 묶어 Crashlytics 등 형제 모듈도 같은 시점.
    //  - implementation 사용 사유: 사용처 앱이 자기 BOM (보통 더 최신) 을 자유롭게 잡을 수 있도록 강제하지 않음.
    //  - 사용처는 firebase-analytics 자체는 이 라이브러리를 통해 transitive 로 받는다 (implementation 이라 compile 시간엔 안 보이지만 runtime classpath 에 있음).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-analytics",
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
