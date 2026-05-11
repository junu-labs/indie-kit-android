// 역할
//  - 멀티 모듈 빌드의 루트. 어떤 라이브러리 코드도 여기 두지 않는다.
//  - 각 모듈에서 alias 로 골라 쓸 수 있도록 플러그인을 apply false 로 등록만 한다.
//
// 주의사항
//  - 모듈에서 plugins { alias(libs.plugins.XXX) } 가 동작하려면 반드시 여기 apply false 로 선언되어 있어야 한다.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.maven.publish) apply false
}
