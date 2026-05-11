// 역할
//  - co.junu.indiekit.auth 패키지의 안드로이드 라이브러리.
//  - 5단계 진입 시 Google + Kakao + Apple (Custom Tabs OAuth) 로그인 + 우리 서버 세션 발급 어댑터가 들어간다.
//
// 0단계 약속
//  - 외부 의존성 0개 (Google ID / Kakao SDK 는 5단계에서 추가).
//  - Placeholder.kt 만 들어 있다.
//
// 발행 좌표
//  - co.junu:indie-kit-auth:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.auth"
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
    api(project(":indie-kit-core"))
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-auth",
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
