// 역할
//  - 멀티 모듈 빌드의 루트. 어떤 라이브러리 코드도 여기 두지 않는다.
//  - 각 모듈에서 alias 로 골라 쓸 수 있도록 플러그인을 apply false 로 등록만 한다.
//
// 주의사항
//  - 모듈에서 plugins { alias(libs.plugins.XXX) } 가 동작하려면 반드시 여기 apply false 로 선언되어 있어야 한다.

// AGP 9 내장 Kotlin 이 쓸 Kotlin Gradle Plugin 버전을 libs.versions.toml 의 kotlin (2.4.10) 으로 고정.
//  - 내장 Kotlin 은 기본으로 AGP 가 묶은 KGP 를 쓰는데, 여기서 명시적으로 올려 앱과 같은 버전으로 맞춘다.
//  - 이 한 줄이 옛 kotlin-android 플러그인이 하던 "버전 고정" 역할을 대신한다.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}
