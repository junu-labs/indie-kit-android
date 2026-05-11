/*
 * ConcurrencyHelpers.kt — IndieKitCore
 *
 * 역할
 *  - 5개 모듈이 자주 쓰는 동시성 처리 헬퍼 모음.
 *  - 단계 0 (부트스트랩) ~ 단계 1 (Analytics) 시점엔 빈 자리. 단계가 더 진행되면 공통 패턴이 보이는 대로 여기로 추출.
 *
 * 주요 개념
 *  - 자주 등장할 후보:
 *     - "비동기 호출을 메인 스레드 (Main Dispatcher) 로 옮겨 UI 업데이트"
 *     - "ViewModel scope 안에서 launch 실패 → 로깅 + 재시도"
 *     - "여러 비동기 호출 결과 첫 번째만 받기 (race)"
 *  - 채워 넣는 시점: 같은 패턴이 2개 이상 모듈에서 반복될 때.
 *
 * 주의사항
 *  - 헬퍼는 신중히 추가한다. "한 번 쓰는 코드" 는 모듈 안에 그대로 두는 게 더 명확.
 *  - iOS 자매 (`ConcurrencyHelpers` enum) 와 같은 빈 자리.
 */

package kr.co.junu.indiekit.core

/**
 * 동시성 헬퍼 묶음 자리.
 *
 * 단계가 진행되면서 추출할 만한 패턴이 발견되면 여기에 추가한다.
 * 현재는 빈 object 로 자리만 잡아 둠.
 */
public object ConcurrencyHelpers {
    // 단계 2~5 중 추출 후보가 보이면 여기에 채운다.
}
