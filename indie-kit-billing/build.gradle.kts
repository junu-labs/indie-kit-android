// 역할
//  - kr.co.junu.indiekit.billing 패키지의 안드로이드 라이브러리.
//  - 4단계 — Google Play Billing v7 (7.1.1) 래퍼 + 자동 갱신 구독 + 비소진형 1회성 결제 + 자동 acknowledge.
//
// 외부 의존성
//  - play-billing-ktx (api): BillingClient / ProductDetails / Purchase 자체가 사용처에 노출되는 형이라 api.
//  - kotlinx-coroutines-core (api): suspend 함수 + StateFlow.
//
// 발행 좌표
//  - kr.co.junu:indie-kit-billing:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.billing"
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
    // Core 의 공개 타입 (IKLogger 등) 을 사용처에 노출.
    api(project(":indie-kit-core"))

    // Play Billing — 본체.
    //  - api 사용 사유: 사용처가 ProductDetails / Purchase 등 객체를 직접 만지는 경우가 있음.
    api(libs.play.billing.ktx)

    // kotlinx-coroutines-core — suspend 함수 + StateFlow.
    //  - api 사용 사유: 사용처가 entitlements: StateFlow 를 collect 하려면 coroutines 가 compile classpath 에 있어야 한다.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-billing",
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
