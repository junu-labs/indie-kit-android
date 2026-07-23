// 역할
//  - co.junu.indiekit.auth 패키지의 안드로이드 라이브러리.
//  - Google (Credential Manager + Sign in with Google) + Kakao (kakao-sdk) 로그인 + 우리 서버 세션 발급 어댑터.
//  - 안드로이드는 Apple 로그인을 안 다룸 — iOS 자매 와 같은 API 의 GOOGLE / KAKAO 두 케이스만.
//
// 외부 의존성 (5단계 진입)
//  - androidx.credentials + credentials-play-services-auth: Credential Manager 본체 + Play Services 어댑터.
//  - googleid: GetGoogleIdOption + GoogleIdTokenCredential.
//  - kakao v2-user: 카카오 로그인 (카카오톡 앱 / 웹뷰 분기) + 토큰 / 사용자 정보 API.
//  - kotlinx-coroutines-core: suspend 함수 (Credential Manager + Kakao 콜백 감싸기).
//  - kotlinx-serialization-json: SessionExchangeAdapter 의 응답 디코딩 도우미.
//
// 발행 좌표
//  - kr.co.junu:indie-kit-auth:{VERSION_NAME}.

import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "kr.co.junu.indiekit.auth"
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
    api(project(":indie-kit-core"))

    // Credential Manager — Google "Sign in with Google" 의 표준 진입점.
    //  사용처 (앱) 가 GoogleIdTokenCredential / GetGoogleIdOption 을 직접 만질 일은 없어 implementation.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // 카카오 로그인 — UserApi / AuthApi / OAuthToken / User 자료가 라이브러리 사용자에게도 노출되는 경우가 있어 api.
    api(libs.kakao.user)

    // suspend 함수 / withContext(Dispatchers.IO).
    api(libs.kotlinx.coroutines.core)

    // SessionExchangeAdapter 가 응답을 디코딩할 때 사용처가 @Serializable data class 를 정의하므로 api 노출.
    api(libs.kotlinx.serialization.json)

    // SessionExchangeAdapter 가 우리 서버로 POST 호출 — OkHttp 직접 사용.
    // (IndieKitNetwork 와 같은 인스턴스를 공유할 수도 있지만 기본은 자체 인스턴스.)
    implementation(libs.okhttp)

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
