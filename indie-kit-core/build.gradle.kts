// 역할
//  - co.junu.indiekit.core 패키지의 안드로이드 라이브러리.
//  - 외부 5개 모듈 (analytics / ads / network / billing / auth) 이 같이 쓰는 도구 묶음의 자리.
//  - README 에는 노출하지 않음 (이름 그대로 "Core" — 사용자가 직접 import 할 일 없음).
//
// 주요 개념
//  - 다른 모듈은 implementation 이 아닌 api(project(":indie-kit-core")) 로 끌어 써서,
//    Core 의 공개 타입 (1단계 이후 추가될 IKLogger / IndieKitConfigurationError / AnalyticsBus 등) 을
//    모듈 사용처에서 그대로 보이게 한다 (iOS 의 @_exported import IndieKitCore 패턴 대응).
//
// 0단계 약속
//  - 외부 의존성 0개. junit 1개 (테스트용) 외엔 없다.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.core"
    compileSdk = 37

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
            // Robolectric 없이 가벼운 JVM 단위 테스트로 끝낼 수 있게 해 준다.
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
    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-core",
        version = providers.gradleProperty("VERSION_NAME").get()
    )
    // release variant 만 발행. sources jar 는 같이, javadoc jar 는 끔.
    //  - JavaDoc 끄는 사유: AGP 9.0 의 기본 javaDoc 작업 (Dokka 기반) 이 Kotlin 2.3 metadata 를
    //    못 읽어 Firebase 같은 최신 라이브러리 의존 모듈에서 빌드 실패. JitPack 경로는 javadoc jar 불필요.
    //    Maven Central 이전 시 Dokka 직접 설정으로 다시 켠다.
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false
        )
    )
}
