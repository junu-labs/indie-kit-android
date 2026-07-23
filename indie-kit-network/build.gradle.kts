// 역할
//  - kr.co.junu.indiekit.network 패키지의 안드로이드 라이브러리.
//  - 3단계 — OkHttp 위 얇은 래퍼 + 401 응답 시 인증값 자동 갱신 흐름.
//
// 외부 의존성
//  - okhttp (api): HTTP 본체. raw Request / Response 출구가 사용처에 노출됨.
//  - kotlinx-serialization-json (api): @Serializable 자료의 JSON 인코딩 / 디코딩.
//
// 발행 좌표
//  - kr.co.junu:indie-kit-network:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // 3단계 — @Serializable 코드 생성. Kotlin 컴파일러와 같은 버전.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.network"
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
    // Core 의 공개 타입 (IKLogger 등) 을 사용처에 노출.
    api(project(":indie-kit-core"))

    // OkHttp — 본체.
    //  - api 사용 사유: send(Request) raw 출구로 사용처가 직접 Request / Response 객체를 다룬다.
    api(libs.okhttp)

    // kotlinx-serialization-json — JSON 인코딩 / 디코딩.
    //  - api 사용 사유: 사용처가 자기 자료 클래스에 @Serializable 을 붙이려면 어노테이션이 compile classpath 에 있어야 한다.
    api(libs.kotlinx.serialization.json)

    // kotlinx-coroutines-core — suspend 함수 + Dispatchers.IO.
    //  - api 사용 사유: 사용처가 suspend get/post/put/delete 를 부르려면 coroutines 가 compile classpath 에 있어야 한다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    // OkHttp 응답 모킹은 자체 Interceptor 로 처리 — mockwebserver 의존성은 불필요.
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-network",
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
