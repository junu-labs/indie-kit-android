/*
 * PlaceholderTest.kt — indie-kit-network
 *
 * 역할
 *  - Placeholder 의 표식이 실제로 false 인지 확인.
 *  - 0단계 빌드 / 테스트 골격이 살아 있다는 가장 작은 신호.
 */

package kr.co.junu.indiekit.network

import org.junit.Assert.assertFalse
import org.junit.Test

class PlaceholderTest {

    @Test
    fun `placeholder marker is false during bootstrap`() {
        assertFalse(Placeholder.isPlaceholder)
    }
}
