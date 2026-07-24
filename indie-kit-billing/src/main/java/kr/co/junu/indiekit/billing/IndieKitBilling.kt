/*
 * IndieKitBilling.kt — IndieKitBilling
 *
 * 역할
 *  - Google Play Billing v9 위에 얇은 래퍼. 자동 갱신 구독 + 비소진형 1회성 결제를 같은 그릇으로.
 *  - 구매 후 자동 acknowledge (Play 정책상 3일 내 호출 안 하면 자동 환불).
 *  - 보유 권한 (entitlements) 를 StateFlow 로 노출 — Compose 에서 collectAsState 한 줄.
 *
 * 주요 개념
 *  - `object` 싱글턴 — Play Billing 의 BillingClient 가 프로세스 전역이라 인스턴스 여러 개 가질 의미 없음.
 *    iOS 자매의 `IndieKitBilling.shared` 와 같은 패턴.
 *  - `entitlements: StateFlow<Set<String>>` — 사용자가 *지금* 보유한 productID 집합 (구독 + 비소진형 합집합).
 *  - `isPro` / `hasActiveSubscription` / `hasLifetime` — 동기 헬퍼. entitlements.value 보고 즉시 답.
 *  - PurchasesUpdatedListener 콜백 → suspend purchase() 의 결과 매핑은 CompletableDeferred 를 통해.
 *
 * 사용 방법
 *  ```kotlin
 *  // 앱 시작 시 (Application.onCreate 또는 첫 Activity onCreate):
 *  lifecycleScope.launch {
 *      IndieKitBilling.configure(
 *          context = applicationContext,
 *          products = listOf(
 *              ProductDescriptor("kr.co.junu.solti.premium.monthly", BillingProductType.AutoRenewableSubscription),
 *              ProductDescriptor("kr.co.junu.solti.premium.yearly",  BillingProductType.AutoRenewableSubscription),
 *              ProductDescriptor("kr.co.junu.solti.lifetime",        BillingProductType.NonConsumable)
 *          )
 *      )
 *  }
 *
 *  // 화면 안 (Compose):
 *  val entitlements by IndieKitBilling.entitlements.collectAsState()
 *  if (IndieKitBilling.isPro) Text("프로 사용자")
 *
 *  // 구매 / 복원:
 *  val result = IndieKitBilling.purchase(activity, "kr.co.junu.solti.premium.yearly")
 *  IndieKitBilling.restore()
 *  ```
 *
 * 주의사항
 *  - 결제 이벤트 통계는 앱 책임 — 라이브러리가 자동으로 안 보냄. 가격 / 통화 정보가 함께 가야 표준이라.
 *  - 소진형 (consumable) 미지원 — 잔량 추적이 앱별 DB / 서버 정책이 달라 표준화 X.
 *  - 서버 영수증 검증은 미포함 — 5단계 (Auth) 의 백엔드 셋업과 묶어 클로저 자리로 추가 예정.
 */

package kr.co.junu.indiekit.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.co.junu.indiekit.core.IKLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * IndieKitBilling — Play Billing 위 얇은 결제 래퍼.
 *
 * iOS 자매 (`IndieKitBilling.shared`) 와 같은 진입점 이름 / 동일 흐름.
 */
public object IndieKitBilling {

    /** 단계 4 진입 표식 — placeholder 가 실 구현으로 교체됨. */
    public const val isPlaceholder: Boolean = false

    // ─────────────────────────────────────────────────────────────────────
    // 외부 공개 상태 — StateFlow 로 노출. Compose 는 collectAsState() 로 받음.
    // ─────────────────────────────────────────────────────────────────────

    private val _entitlements = MutableStateFlow<Set<String>>(emptySet())
    /** 사용자가 *지금* 보유한 productID 모음 (구독 살아 있는 것 + 비소진형 보유 모두). */
    public val entitlements: StateFlow<Set<String>> = _entitlements.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    /** Play Store 에서 받아온 상품 정보 (가격 / 이름 / 설명 등). configure 후 채워짐. */
    public val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    /** 앱 시작 시 첫 상태 확인이 끝났는지. true 가 되기 전엔 entitlements 에 의존하면 안 됨. */
    public val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    /** 구매 / 복원 진행 중 여부. UI 의 로딩 spinner 분기에 사용. */
    public val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────
    // 동기 헬퍼 — entitlements.value 보고 즉시 답.
    // Compose 사용자는 entitlements 를 collectAsState 한 뒤 이 헬퍼를 호출.
    // ─────────────────────────────────────────────────────────────────────

    /** 특정 productID 를 지금 보유 중인지. */
    public fun owns(productID: String): Boolean = entitlements.value.contains(productID)

    /** 자동 갱신 구독 중 하나라도 살아 있는지. */
    public val hasActiveSubscription: Boolean
        get() = entitlements.value.any { productTypes[it] is BillingProductType.AutoRenewableSubscription }

    /** 비소진형 (평생 결제) 권한을 하나라도 가졌는지. */
    public val hasLifetime: Boolean
        get() = entitlements.value.any { productTypes[it] is BillingProductType.NonConsumable }

    /** 가장 많이 쓰는 헬퍼 — 구독 또는 평생 권한 중 하나라도 있으면 true. */
    public val isPro: Boolean
        get() = hasActiveSubscription || hasLifetime

    /**
     * 특정 자동 갱신 구독의 만료 timestamp (ms epoch).
     * 비소진형 / 미보유 productID 면 null.
     *
     * 주의: Play Billing 의 Purchase 자체엔 만료일이 없음 — ProductDetails 의 billing period 로 근사 계산.
     * 정확한 만료는 Play Console / 서버 RTDN. 본 헬퍼는 데모 / UI 표시용 근사.
     */
    public fun expirationDate(productID: String): Long? = expirationCache[productID]

    // ─────────────────────────────────────────────────────────────────────
    // 내부 상태
    // ─────────────────────────────────────────────────────────────────────

    /** productID → 종류 매핑. configure 에서 채움. */
    private val productTypes = mutableMapOf<String, BillingProductType>()

    /** productID → 만료 timestamp (자동 갱신 구독 한정). refreshEntitlements 에서 채움. */
    private val expirationCache = mutableMapOf<String, Long>()

    /** Play Billing 본체. configure 에서 만들고 startConnection. */
    private var billingClient: BillingClient? = null

    /** 진행 중인 구매의 결과를 기다리는 Deferred. PurchasesUpdatedListener 가 complete. */
    private val pendingPurchase = AtomicReference<CompletableDeferred<PurchaseResult>?>(null)

    /** configure 동시 호출 방지 + refreshEntitlements 동시 진행 방지. */
    private val mutex = Mutex()

    /**
     * 연결 시도 직렬화 — startConnection 이 동시에 두 번 돌지 않게.
     * 위의 mutex 와 별도인 이유: 재확인 (mutex 보유 중) 안에서도 재연결이 필요할 수 있어
     * 같은 자물쇠를 쓰면 순서가 꼬인다. 이 자물쇠는 오직 연결 시도에만 쓴다.
     */
    private val connectionMutex = Mutex()

    /**
     * 최근 구매 스티키 — productID → 만료 timestamp (ms epoch. 비소진형은 Long.MAX_VALUE).
     *
     * 방금 산 구매가 Play 의 queryPurchasesAsync 결과에 반영되기 전 (테스트 트랙 · 반영 지연) 에
     * 재확인이 권한을 도로 지우지 못하게 union 대상으로 붙잡아 둔다. 재확인이 몇 번을 돌아도
     * 스티키가 다시 들어가므로 "누가 마지막에 쓰느냐" 순서 싸움이 없다.
     * 만료 지난 항목은 refreshEntitlementsInternal 이 스스로 정리한다 — 취소 후 만료도 정확.
     * iOS 자매 (0.7.7) 의 recentPurchases 와 같은 패턴.
     */
    private val recentPurchases = ConcurrentHashMap<String, Long>()

    /**
     * 백그라운드 작업 scope.
     * SupervisorJob — 한 자식 실패가 형제에 영향 X.
     * Dispatchers.Main.immediate — UI 갱신 곁들이는 경우가 많아 main 우선.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * PurchasesUpdatedListener — Play Billing 이 구매 결과를 통지하는 콜백.
     * 결제 다이얼로그 닫힘 / 외부 갱신 / 가족 공유 추가 등 모든 변화가 여기로.
     */
    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { handlePurchasesUpdate(result, purchases) }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 처음 설정
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 라이브러리에 다룰 상품 목록을 등록한다. 앱 시작 시 한 번 호출.
     *
     * 동작
     *  1. 상품 ID + 종류 매핑 저장.
     *  2. BillingClient 만들고 startConnection.
     *  3. Play Store 에서 ProductDetails 적재 (INAPP / SUBS 분리 조회).
     *  4. 현재 보유한 entitlements 첫 검사 (queryPurchasesAsync).
     *  5. `isReady` true 로.
     */
    public suspend fun configure(context: Context, products: List<ProductDescriptor>) {
        mutex.withLock {
            // 종류 매핑.
            productTypes.clear()
            for (p in products) productTypes[p.id] = p.type

            // BillingClient 만들기 — applicationContext 사용 (Activity context 누수 방지).
            val client = BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .build()
                )
                .build()
            billingClient = client

            try {
                connectClient(client)
                loadProducts(client, products)
                refreshEntitlementsInternal(client)
                _isReady.value = true
                IKLogger.billing.info("IndieKitBilling 처음 설정 완료 (상품 ${products.size} 개)")
            } catch (e: Throwable) {
                IKLogger.billing.error("IndieKitBilling 처음 설정 실패: ${e.message}", e)
                throw e
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 구매
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 등록된 상품 ID 로 구매를 시도한다.
     *
     * @param activity Play Billing 의 결제 다이얼로그가 떠야 할 Activity. iOS 와 달리 명시 필수.
     * @param productID configure 에 등록된 product ID.
     *
     * @return success / userCancelled / pending.
     * @throws IndieKitBillingError.NotConfigured / ProductNotFound / AlreadyInProgress / Underlying.
     */
    public suspend fun purchase(activity: Activity, productID: String): PurchaseResult {
        val client = billingClient ?: throw IndieKitBillingError.NotConfigured
        val product = _products.value.find { it.productId == productID }
            ?: throw IndieKitBillingError.ProductNotFound(productID)

        val deferred = CompletableDeferred<PurchaseResult>()
        if (!pendingPurchase.compareAndSet(null, deferred)) {
            throw IndieKitBillingError.AlreadyInProgress
        }

        _isLoading.value = true
        try {
            val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product)

            // 자동 갱신 구독은 offerToken 필수 (Play Billing v6+ 규칙).
            // 비소진형 (INAPP) 은 offerToken 없음.
            if (product.productType == BillingClient.ProductType.SUBS) {
                val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    ?: throw IndieKitBillingError.Underlying(
                        IllegalStateException("구독 상품 $productID 에 사용 가능한 offer 가 없습니다.")
                    )
                builder.setOfferToken(offerToken)
            }

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()

            val launchResult = client.launchBillingFlow(activity, params)
            if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                // launch 자체 실패 — deferred 정리하고 에러.
                pendingPurchase.compareAndSet(deferred, null)
                throw IndieKitBillingError.Underlying(
                    IllegalStateException("launchBillingFlow 실패: ${launchResult.debugMessage}")
                )
            }

            // 결과는 PurchasesUpdatedListener 가 complete.
            return deferred.await()
        } finally {
            _isLoading.value = false
            pendingPurchase.compareAndSet(deferred, null)
        }
    }

    /**
     * PurchasesUpdatedListener 콜백 처리.
     *
     * - OK + purchases: acknowledge + entitlements 갱신 → Success.
     * - USER_CANCELED: UserCancelled.
     * - ITEM_ALREADY_OWNED: 권한만 갱신 후 Success (중복 구매 의도면 사실상 성공).
     * - 그 외: Underlying 예외로 deferred 깨뜨림.
     */
    private suspend fun handlePurchasesUpdate(result: BillingResult, purchases: List<Purchase>?) {
        val deferred = pendingPurchase.get()

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (!purchases.isNullOrEmpty()) {
                    for (p in purchases) {
                        if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (!p.isAcknowledged) acknowledgeWithRetry(p)
                            // 스티키 기록 + 즉시 반영 — 뒤이은 재확인 결과를 기다리지 않고
                            // 결제 직후 바로 isPro 가 true 로 뜨게 하고, 어떤 재확인도
                            // 이 권한을 지우지 못하게 붙잡아 둔다.
                            rememberRecentPurchase(p)
                        }
                    }
                    refreshEntitlements()

                    // PENDING 상태 (부모 동의 등) 인 구매가 섞여 있으면 pending 로 응답.
                    val hasPending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                    deferred?.complete(if (hasPending) PurchaseResult.Pending else PurchaseResult.Success)
                } else {
                    deferred?.complete(PurchaseResult.UserCancelled)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                deferred?.complete(PurchaseResult.UserCancelled)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                refreshEntitlements()
                deferred?.complete(PurchaseResult.Success)
            }
            else -> {
                IKLogger.billing.warning("구매 실패: ${result.debugMessage} (code=${result.responseCode})")
                deferred?.completeExceptionally(
                    IndieKitBillingError.Underlying(
                        IllegalStateException("구매 실패: ${result.debugMessage}")
                    )
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 복원
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 이전 구매 복원 — Play 의 현재 구매 상태로 entitlements 를 다시 채움.
     *
     * iOS 의 AppStore.sync 와 달리, Play Billing 은 queryPurchasesAsync 만으로 충분 —
     * 이미 구글 계정에 묶여 있어 외부 sync 불필요.
     */
    public suspend fun restore() {
        val client = billingClient ?: throw IndieKitBillingError.NotConfigured
        _isLoading.value = true
        try {
            // mutex 안에서 실행 — 결제 성공 직후의 재확인과 경쟁해 서로의 결과를
            // 덮어쓰지 못하게 모든 권한 갱신을 한 줄로 세운다.
            val ok = mutex.withLock { refreshEntitlementsInternal(client) }
            if (ok) {
                IKLogger.billing.info("복원 완료. 보유 권한 ${entitlements.value.size} 개")
            } else {
                IKLogger.billing.warning("복원 조회 실패 — 기존 권한 유지 (${entitlements.value.size} 개)")
            }
        } finally {
            _isLoading.value = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 조용한 재확인 (다이얼로그 없음)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 보유 권한을 조용히 다시 확인한다. 결제 / 복원 다이얼로그를 띄우지 않는다.
     *
     * restore() 와의 구분
     *  - restore() = **사용자 버튼**. 이전 구매를 명시적으로 다시 끌어온다. (iOS 자매는 여기서
     *    App Store 암호창이 뜸 — Android 는 queryPurchasesAsync 만이라 암호창은 안 뜨지만,
     *    "사용자가 누르는 복원" 이라는 역할은 같다. _isLoading 스피너도 켠다.)
     *  - refresh() = **조용한 자동 재확인**. queryPurchasesAsync (INAPP+SUBS) 로 현재 보유 상태만
     *    다시 읽는다. _isLoading 을 켜지 않아 UI 방해가 없으므로, 앱이 포그라운드로 돌아올 때마다
     *    (onResume / ON_RESUME) 호출해도 안전하다.
     *
     * 쓰임새 — 구독 취소 후 만료 시점이 지나면, 앱을 껐다 켜지 않고 포그라운드 복귀만으로도
     * isPro 가 false 로 정확히 떨어지게 한다.
     *
     * configure 전 (billingClient == null) 에 불려도 안전 — 내부 refreshEntitlements 가 조용히 return.
     * iOS 자매의 IndieKitBilling.shared.refresh() 와 1:1 대칭.
     */
    public suspend fun refresh() {
        refreshEntitlements()
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 헬퍼 — Play Billing 콜백을 suspend 로 wrap
    // ─────────────────────────────────────────────────────────────────────

    /** BillingClient 의 startConnection 을 suspend 로. */
    private suspend fun connectClient(client: BillingClient): Unit =
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        if (cont.isActive) cont.resumeWithException(
                            IndieKitBillingError.Underlying(
                                IllegalStateException("BillingClient 연결 실패: ${result.debugMessage}")
                            )
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // 연결 끊김 — 곧바로 백오프 재연결을 시도한다.
                    // (조회 직전에도 ensureConnected 가 한 번 더 살아 있는지 확인하므로 이중 안전망.)
                    IKLogger.billing.warning("BillingClient 연결 끊김 — 백오프 재연결 시작")
                    scope.launch {
                        billingClient?.let { ensureConnected(it) }
                    }
                }
            })
        }

    /**
     * 연결이 살아 있는지 확인하고, 끊겨 있으면 유한 백오프 (0.5초 → 1초 → 2초) 로
     * 최대 3번 재연결을 시도한다.
     *
     * @return true = 연결 살아 있음 (또는 재연결 성공). false = 3번 다 실패.
     *
     * connectionMutex 로 직렬화 — 연결 끊김 콜백과 조회 직전 확인이 동시에 들어와도
     * startConnection 이 겹쳐 돌지 않는다. 자물쇠를 얻은 뒤 isReady 를 다시 확인하므로
     * 앞사람이 이미 살려 놨으면 바로 통과한다.
     */
    private suspend fun ensureConnected(client: BillingClient): Boolean {
        if (client.isReady) return true
        connectionMutex.withLock {
            if (client.isReady) return true
            var delayMs = 500L
            repeat(RECONNECT_MAX_ATTEMPTS) { attempt ->
                try {
                    connectClient(client)
                    IKLogger.billing.info("BillingClient 재연결 성공 (시도 ${attempt + 1})")
                    return true
                } catch (e: Throwable) {
                    IKLogger.billing.warning("BillingClient 재연결 실패 (시도 ${attempt + 1}/$RECONNECT_MAX_ATTEMPTS): ${e.message}")
                    if (attempt < RECONNECT_MAX_ATTEMPTS - 1) {
                        delay(delayMs)
                        delayMs *= 2
                    }
                }
            }
        }
        return false
    }

    /** 재연결 / acknowledge 재시도 횟수 상한. */
    private const val RECONNECT_MAX_ATTEMPTS = 3

    /** INAPP / SUBS 상품 정보를 따로 조회해 합친다. */
    private suspend fun loadProducts(client: BillingClient, descriptors: List<ProductDescriptor>) {
        val inappIds = descriptors.filter { it.type is BillingProductType.NonConsumable }.map { it.id }
        val subsIds = descriptors.filter { it.type is BillingProductType.AutoRenewableSubscription }.map { it.id }

        val loaded = mutableListOf<ProductDetails>()
        if (inappIds.isNotEmpty()) {
            loaded += queryProducts(client, inappIds, BillingClient.ProductType.INAPP)
        }
        if (subsIds.isNotEmpty()) {
            loaded += queryProducts(client, subsIds, BillingClient.ProductType.SUBS)
        }
        _products.value = loaded
        IKLogger.billing.info("상품 정보 적재: ${loaded.size} 개")
    }

    /** queryProductDetailsAsync 를 suspend 로. */
    private suspend fun queryProducts(
        client: BillingClient,
        ids: List<String>,
        type: String
    ): List<ProductDetails> = suspendCancellableCoroutine { cont ->
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                }
            )
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            // Play Billing 9 부터 콜백 두 번째 값이 상품 목록 자체가 아니라 그것을 감싼 상자로 바뀜.
            // (상자 안에는 못 받아온 상품 목록도 함께 들어 있으나 여기선 받은 목록만 씀.)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (cont.isActive) cont.resume(queryResult.productDetailsList)
            } else {
                IKLogger.billing.warning("queryProductDetailsAsync 실패: ${result.debugMessage}")
                // 실패해도 빈 목록으로 진행 — 검증 단계에서 ProductNotFound 로 잡힘.
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    /**
     * acknowledge 를 유한 백오프 (1초 → 2초) 로 최대 3번 시도한다.
     *
     * 미승인 구매는 Play 정책상 3일 뒤 자동 환불되므로 한 번 실패로 끝내면 위험하다.
     * 3번 다 실패해도 다음 재확인 (refreshEntitlementsInternal) 이 미승인 구매를 다시
     * acknowledge 하는 안전망이 있어 무한 재시도는 하지 않는다.
     */
    private suspend fun acknowledgeWithRetry(purchase: Purchase) {
        var delayMs = 1_000L
        repeat(RECONNECT_MAX_ATTEMPTS) { attempt ->
            if (acknowledgePurchase(purchase)) {
                if (attempt > 0) IKLogger.billing.info("acknowledge 성공 (재시도 ${attempt + 1}번째)")
                return
            }
            if (attempt < RECONNECT_MAX_ATTEMPTS - 1) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        IKLogger.billing.warning(
            "acknowledge ${RECONNECT_MAX_ATTEMPTS}번 모두 실패 — 다음 재확인 때 다시 시도 (3일 내 미승인 시 자동 환불 주의)"
        )
    }

    /** Play Billing 의 acknowledgePurchase 를 suspend 로. @return 승인 성공 여부. */
    private suspend fun acknowledgePurchase(purchase: Purchase): Boolean =
        suspendCancellableCoroutine { cont ->
            val client = billingClient
            if (client == null) {
                if (cont.isActive) cont.resume(false)
                return@suspendCancellableCoroutine
            }
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { result ->
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!ok) {
                    IKLogger.billing.warning("acknowledge 실패: ${result.debugMessage} (code=${result.responseCode})")
                }
                if (cont.isActive) cont.resume(ok)
            }
        }

    /**
     * queryPurchasesAsync 를 suspend 로. INAPP / SUBS 따로 호출.
     *
     * @return 성공 시 구매 목록 (없으면 빈 목록). **실패 시 null** — 호출부가
     *         "조회가 실패한 것" 과 "정말 구매가 없는 것" 을 구분할 수 있어야
     *         실패한 조회의 빈 결과가 이미 확보한 권한을 지우는 사고를 막는다.
     */
    private suspend fun queryPurchases(
        client: BillingClient,
        type: String
    ): List<Purchase>? = suspendCancellableCoroutine { cont ->
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(type)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (cont.isActive) cont.resume(purchases)
            } else {
                IKLogger.billing.warning("queryPurchases 실패: ${result.debugMessage} (code=${result.responseCode})")
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /** 외부에서 부르는 refresh — mutex 안에서 실행. */
    private suspend fun refreshEntitlements() {
        val client = billingClient ?: return
        mutex.withLock {
            refreshEntitlementsInternal(client)
        }
    }

    /**
     * INAPP + SUBS 보유 권한을 조회해 entitlements 와 만료 캐시를 갱신.
     *
     * 동작
     *  - 조회 전 연결 생존 확인 (끊겼으면 백오프 재연결).
     *  - **두 조회 (INAPP + SUBS) 가 모두 성공했을 때만 값을 교체한다.**
     *    하나라도 실패 (null) 면 기존 권한을 그대로 유지 — 실패한 조회의 빈 결과가
     *    이미 확보한 구독 권한을 지우는 사고 방지.
     *  - PURCHASED 상태인 구매만 인정.
     *  - 등록되지 않은 productID 는 무시 (configure 에서 등록 안 된 외부 결제 보호).
     *  - 아직 acknowledge 안 된 구매가 있으면 여기서 acknowledge (재시도 포함).
     *  - 자동 갱신 구독은 만료 timestamp 근사 (purchaseTime + billing period).
     *  - 마지막에 최근 구매 스티키를 union — 방금 산 구매가 Play 결과에 아직 안 올라와도
     *    지워지지 않게. 만료 지난 스티키는 정리하므로 취소 후 만료 시엔 정상적으로 빠진다.
     *
     * @return true = 갱신 성공. false = 조회 실패로 기존 값 유지.
     */
    private suspend fun refreshEntitlementsInternal(client: BillingClient): Boolean {
        // 연결이 죽었으면 조회가 전부 실패 응답만 돌려준다 — 먼저 살린다.
        if (!ensureConnected(client)) {
            IKLogger.billing.warning("재확인 건너뜀 — BillingClient 재연결 실패. 기존 권한 유지")
            return false
        }

        val newEntitlements = mutableSetOf<String>()
        val newExpirationCache = mutableMapOf<String, Long>()

        // INAPP — 비소진형. 조회 실패면 여기서 끝 (기존 값 유지).
        val inappPurchases = queryPurchases(client, BillingClient.ProductType.INAPP)
        if (inappPurchases == null) {
            IKLogger.billing.warning("INAPP 조회 실패 — 권한 갱신 중단, 기존 값 유지")
            return false
        }

        // SUBS — 자동 갱신 구독. 마찬가지로 실패면 기존 값 유지.
        val subsPurchases = queryPurchases(client, BillingClient.ProductType.SUBS)
        if (subsPurchases == null) {
            IKLogger.billing.warning("SUBS 조회 실패 — 권한 갱신 중단, 기존 값 유지")
            return false
        }

        for (p in inappPurchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            for (productID in p.products) {
                if (productTypes[productID] is BillingProductType.NonConsumable) {
                    newEntitlements.add(productID)
                }
            }
            if (!p.isAcknowledged) acknowledgeWithRetry(p)
        }

        for (p in subsPurchases) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            for (productID in p.products) {
                if (productTypes[productID] is BillingProductType.AutoRenewableSubscription) {
                    newEntitlements.add(productID)

                    // 만료 근사 — purchaseTime + billing period.
                    val product = _products.value.find { it.productId == productID }
                    val periodIso = product?.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.billingPeriod
                    if (periodIso != null) {
                        val periodMs = isoPeriodToMs(periodIso)
                        if (periodMs > 0L) {
                            newExpirationCache[productID] = p.purchaseTime + periodMs
                        }
                    }
                }
            }
            if (!p.isAcknowledged) acknowledgeWithRetry(p)
        }

        // 최근 구매 스티키 반영 — 만료 지난 것부터 정리한 뒤 남은 것을 union.
        val now = System.currentTimeMillis()
        recentPurchases.entries.removeAll { it.value <= now }
        for ((productID, expiry) in recentPurchases) {
            if (newEntitlements.add(productID)) {
                IKLogger.billing.info("최근 구매 스티키로 인정 — $productID (Play 조회 반영 대기 중)")
            }
            if (expiry != Long.MAX_VALUE && newExpirationCache[productID] == null) {
                newExpirationCache[productID] = expiry
            }
        }

        expirationCache.clear()
        expirationCache.putAll(newExpirationCache)
        _entitlements.value = newEntitlements
        return true
    }

    /**
     * 방금 성공한 구매를 스티키에 기록하고 entitlements 에 즉시 반영한다.
     *
     * 스티키 만료 = 구독은 purchaseTime + billing period 근사, 비소진형은 무기한.
     * (근사 기간을 못 구하면 무기한 — 앱 재시작 시 메모리와 함께 사라지므로 안전.)
     * 스티키가 있는 동안엔 어떤 재확인도 이 권한을 지우지 못하고,
     * 만료가 지나면 재확인이 스스로 정리해 정상적으로 무료로 돌아간다.
     */
    private fun rememberRecentPurchase(purchase: Purchase) {
        for (productID in purchase.products) {
            val type = productTypes[productID] ?: continue
            val expiry = when (type) {
                is BillingProductType.NonConsumable -> Long.MAX_VALUE
                is BillingProductType.AutoRenewableSubscription -> {
                    val periodIso = _products.value.find { it.productId == productID }
                        ?.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.billingPeriod
                    val periodMs = periodIso?.let { isoPeriodToMs(it) } ?: 0L
                    if (periodMs > 0L) purchase.purchaseTime + periodMs else Long.MAX_VALUE
                }
            }
            recentPurchases[productID] = expiry
            _entitlements.value = _entitlements.value + productID
            IKLogger.billing.info("구매 즉시 반영 — $productID (스티키 만료 ${if (expiry == Long.MAX_VALUE) "무기한" else expiry.toString()})")
        }
    }

    /**
     * ISO 8601 duration (P1M / P1Y / P1W / P7D 등) → milliseconds 근사.
     * 1M = 30일, 1Y = 365일 단순 변환. 정확한 달력 계산 아님.
     * UI 의 "만료: ..." 표시에 충분한 근사 — 정확한 만료는 서버 측 RTDN.
     */
    @Suppress("MagicNumber")
    internal fun isoPeriodToMs(iso: String): Long {
        val pattern = Regex("""P(\d+)([DWMY])""")
        val match = pattern.matchEntire(iso) ?: return 0L
        val (numStr, unit) = match.destructured
        val num = numStr.toLongOrNull() ?: return 0L
        val dayMs = 24L * 60L * 60L * 1000L
        return when (unit) {
            "D" -> num * dayMs
            "W" -> num * 7L * dayMs
            "M" -> num * 30L * dayMs
            "Y" -> num * 365L * dayMs
            else -> 0L
        }
    }
}
