/*
 * ProductDescriptor.kt — IndieKitBilling
 *
 * 역할
 *  - 앱이 라이브러리에 등록할 상품의 식별자 + 종류 한 쌍.
 *
 * 주요 개념
 *  - Play Billing 의 ProductType 은 INAPP / SUBS 두 종.
 *  - 인디 앱 시나리오에서는 자동 갱신 구독 (SUBS) 과 비소진형 1회성 (INAPP) 만 다룬다.
 *  - 소진형 (consumable) 은 잔량 추적이 앱마다 달라 라이브러리에 표준화 어려움 — 미지원.
 *
 * iOS 자매 (ProductDescriptor / BillingProductType) 와 1:1 대응.
 */

package kr.co.junu.indiekit.billing

/**
 * 라이브러리가 다루는 상품 종류.
 *
 * `IndieKitBilling.entitlements` 에 같은 그릇으로 들어감 — `isPro` 계산 시 둘 합집합.
 */
public sealed class BillingProductType {

    /** 자동 갱신 구독 (월간 / 연간 등). Play Billing 의 SUBS. */
    public data object AutoRenewableSubscription : BillingProductType()

    /** 비소진형 1회성 결제 — "평생 사용 / 광고 영구 제거". Play Billing 의 INAPP (consumable 아님). */
    public data object NonConsumable : BillingProductType()
}

/**
 * 상품 한 개의 기술자 — Play Console 의 product ID + 종류.
 */
public data class ProductDescriptor(
    public val id: String,
    public val type: BillingProductType
)
