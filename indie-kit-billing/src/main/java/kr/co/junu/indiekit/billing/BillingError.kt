/*
 * BillingError.kt — IndieKitBilling
 *
 * 역할
 *  - 결제 모듈이 던지는 예외 + 구매 결과 자료.
 *
 * iOS 자매 (BillingError enum + PurchaseResult enum) 와 1:1 대응.
 *  - notConfigured    ↔ NotConfigured
 *  - productNotFound  ↔ ProductNotFound
 *  - verificationFailed ↔ VerificationFailed
 *  - underlying       ↔ Underlying
 *  - (안드로이드 추가) alreadyInProgress — 동시 구매 진입 방지.
 */

package kr.co.junu.indiekit.billing

/**
 * 결제 흐름에서 발생할 수 있는 예외.
 *
 * `Exception` 을 상속 — 호출 측이 try / catch 또는 coroutine 안에서 자연스럽게 전파.
 */
public sealed class IndieKitBillingError(message: String) : Exception(message) {

    /** `configure(context, products)` 가 한 번도 안 호출됨. */
    public data object NotConfigured : IndieKitBillingError(
        "IndieKitBilling.configure(context, products) 가 호출되지 않았습니다."
    )

    /** 등록되지 않은 productID 로 구매 / 조회 시도. */
    public class ProductNotFound(
        public val productID: String
    ) : IndieKitBillingError("상품 $productID 가 등록되어 있지 않습니다. configure 의 products 에 포함했는지 확인하세요.")

    /** Google 서명 검증 실패 — 위변조 구매 가능성. */
    public data object VerificationFailed : IndieKitBillingError(
        "결제 검증에 실패했습니다. 다시 시도해 주세요."
    )

    /** 그 외 알 수 없는 에러 (Play Billing 자체 에러를 감쌈). */
    public class Underlying(
        public val underlying: Throwable
    ) : IndieKitBillingError(underlying.localizedMessage ?: underlying.javaClass.simpleName)

    /**
     * 이미 다른 구매가 진행 중일 때 새 구매 시도.
     * iOS 와 달리 안드로이드 Play Billing 은 동시 launchBillingFlow 가 불가 — 명시적으로 막음.
     */
    public data object AlreadyInProgress : IndieKitBillingError(
        "다른 구매가 진행 중입니다. 완료를 기다려 주세요."
    )
}

/**
 * 구매 시도 결과.
 *
 * iOS 자매 (PurchaseResult enum) 와 1:1 대응.
 */
public sealed class PurchaseResult {
    /** 결제 + 검증 + acknowledge 완료. */
    public data object Success : PurchaseResult()

    /** 사용자가 결제창을 닫음. */
    public data object UserCancelled : PurchaseResult()

    /** 부모 동의 등으로 보류 — Play Billing 의 PENDING. */
    public data object Pending : PurchaseResult()
}
