/*
 * Placeholder.kt — indie-kit-network
 *
 * 역할
 *  - 0단계 부트스트랩의 결과 표식.
 *  - 3단계 진입 시 IndieKitNetwork (OkHttp 위 얇은 래퍼 + 401 자동 토큰 갱신) 가 들어오면 이 파일은 삭제된다.
 *
 * 외부 노출 X (internal). 라이브러리 사용자에겐 보이지 않는다.
 */

package kr.co.junu.indiekit.network

internal object Placeholder {
    /**
     * 0단계 동안 항상 false. 3단계 이후엔 이 object 자체가 사라진다.
     */
    const val isPlaceholder: Boolean = false
}
