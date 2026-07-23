// 역할
//  - co.junu.indiekit.ads 패키지의 안드로이드 라이브러리.
//  - 배너 / 전면 / 리워드 / Native 광고 + 유럽 광고 동의창 (UMP) + Compose 진입점 (BannerAdView, NativeAdView).
//
// 2단계 (현재)
//  - 외부 의존성: play-services-ads (AdMob), user-messaging-platform (UMP), Compose BOM.
//  - Compose 의존성은 사용처가 라이브러리 진입점 (BannerAdView, NativeAdView*) 을 직접 쓰므로 api 노출.
//  - AdMob (play-services-ads) 도 api — 사용처가 NativeAd 타입을 직접 만질 수 있어야 함 (Native 커스텀 UI 모드).
//  - UMP 는 implementation — 사용자가 UMP 타입 직접 만질 일 없음, IndieKitAds.requestConsentForm 만 호출.
//
// 발행 좌표
//  - co.junu:indie-kit-ads:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.ads"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        // BannerAdView / NativeAdView 가 Compose 진입점이라 필수.
        compose = true
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
    // Core 의 공개 타입 (AnalyticsBus / IKLogger 등) 사용처에 노출.
    api(project(":indie-kit-core"))

    // Compose BOM — 사용처 앱이 자기 BOM 을 따로 쓰면 그쪽 우선.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    // AdMob — Native 커스텀 모드에서 사용처가 NativeAd 타입 직접 사용. api 노출.
    api(libs.play.services.ads)

    // UMP — 사용자가 직접 UMP 타입 만질 일 없음. implementation.
    implementation(libs.user.messaging.platform)

    // androidx.core — Drawable.toBitmap 확장 함수 (Native 광고의 아이콘 표시).
    implementation(libs.androidx.core.ktx)

    // androidx.fragment — 코드에서 직접 쓰진 않는다. play-services-ads 가 끌어오는
    // Google Play Services (play-services-basement) 가 옛 fragment 1.1.0 을 추이로 딸려 와
    // Play Console 이 "오래된 SDK" 경고를 낸다. 최신 fragment 를 직접 실어 밀어낸다.
    // 이 모듈을 쓰는 앱은 (단출한 Compose 앱이라도) 자동으로 최신 fragment 가 번들에 실린다.
    implementation(libs.androidx.fragment)

    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = "kr.co.junu",
        artifactId = "indie-kit-ads",
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
