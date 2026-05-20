// 역할
//  - indie-kit-android 멀티 모듈 빌드의 입구.
//  - 6개 모듈 (Core 1개 + 외부 노출 5개) 을 Gradle 에 등록한다.
//  - 외부 저장소 정책: google() + mavenCentral() 만. JitPack / 사용자 저장소는 라이브러리 사용처 (앱) 가 따로 추가.
//
// 사용 방법
//  - 새 모듈 추가 시 include(":indie-kit-XXX") 한 줄 추가하면 된다.
//
// 주의사항
//  - FAIL_ON_PROJECT_REPOS: 어떤 모듈의 build.gradle.kts 에서 repositories { ... } 를 또 적으면 빌드 실패.
//    저장소 정책은 여기 한 곳에서만 관리하기 위함.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// 도구 체인 정책
//  - jvmToolchain(17) 을 강제하는 대신, Gradle 데몬이 도는 JDK (보통 Android Studio JBR = 21) 로 컴파일한다.
//  - 결과 바이트코드는 모듈 build.gradle.kts 의 compileOptions / compilerOptions 가 JVM 17 으로 강제.
//  - Foojay 자동 다운로드는 Gradle 9.x 와 아직 호환이 깨져 있어 (JvmVendorSpec.IBM_SEMERU 부재) 0단계엔 사용하지 않는다.
//  - CI 는 actions/setup-java@v4 가 JDK 17 자체를 깔기 때문에 별도 보조 도구가 필요 없다.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kakao SDK (com.kakao.sdk:*) 는 Google Maven / Central 에 없고 자체 Nexus 저장소를 쓴다.
        // 5단계 (IndieKitAuth) 에서 v2-user 등을 끌어 쓰려면 이 자리 필요.
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
    }
}

rootProject.name = "indie-kit-android"

include(
    ":indie-kit-core",
    ":indie-kit-analytics",
    ":indie-kit-ads",
    ":indie-kit-network",
    ":indie-kit-billing",
    ":indie-kit-auth"
)
