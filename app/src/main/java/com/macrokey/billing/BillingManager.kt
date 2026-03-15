package com.macrokey.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

/**
 * BillingManager — handles Google Play In-App Purchase for MacroKey Pro.
 *
 * Setup:
 * 1. Add to build.gradle:
 *    implementation "com.android.billingclient:billing-ktx:6.1.0"
 *
 * 2. Create a product in Google Play Console:
 *    Product ID: "macrokey_pro"
 *    Type: One-time (in-app product)
 *    Price: $4.99
 *
 * 3. Initialize in your Activity:
 *    val billing = BillingManager(this) { success ->
 *        if (success) { /* unlock pro features */ }
 *    }
 *    billing.initialize()
 *
 * 4. Launch purchase:
 *    billing.launchPurchase(activity)
 */
class BillingManager(
    private val context: Context,
    private val onPurchaseResult: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "BillingManager"
        const val PRODUCT_ID = "macrokey_pro"
    }

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var isQueryingProduct = false
    private var isCheckingPurchases = false
    private val callbackLock = Any()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                } ?: onPurchaseResult(false)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                onPurchaseResult(false)
            }
            else -> {
                Log.e(TAG, "Purchase error: ${billingResult.debugMessage}")
                onPurchaseResult(false)
            }
        }
    }

    /**
     * Initialize the billing client and connect to Google Play.
     */
    fun initialize() {
        // Don't create a new client if one already exists and is connected
        val existing = billingClient
        if (existing != null && existing.isReady) {
            checkExistingPurchases()
            return
        }

        // Close existing connection before creating new one
        existing?.endConnection()

        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryProduct()
                    checkExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — will retry on next action")
                productDetails = null
                isQueryingProduct = false
            }
        })
    }

    /**
     * Query the product details from Google Play.
     */
    private fun queryProduct() {
        if (isQueryingProduct) return
        isQueryingProduct = true

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            synchronized(callbackLock) {
                isQueryingProduct = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = productDetailsList.firstOrNull()
                    Log.d(TAG, "Product found: ${productDetails?.title}")
                } else {
                    Log.e(TAG, "Product query failed: ${billingResult.debugMessage}")
                }
            }
        }
    }

    /**
     * Check if the user already owns the product (e.g., reinstall scenario).
     */
    private fun checkExistingPurchases() {
        if (isCheckingPurchases) return
        isCheckingPurchases = true

        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            synchronized(callbackLock) {
                isCheckingPurchases = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val ownedPurchase = purchases.firstOrNull {
                        it.products.contains(PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    if (ownedPurchase != null) {
                        TrialManager.setPurchased(context, ownedPurchase.purchaseToken)
                        onPurchaseResult(true)
                    }
                }
            }
        }
    }

    /**
     * Launch the Google Play purchase flow.
     */
    fun launchPurchase(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "Billing client not ready — reconnecting")
            initialize()
            onPurchaseResult(false)
            return
        }

        val details = productDetails
        if (details == null) {
            Log.e(TAG, "Product details not loaded yet")
            // Retry query
            queryProduct()
            onPurchaseResult(false)
            return
        }

        val offerToken = details.oneTimePurchaseOfferDetails
        if (offerToken == null) {
            Log.e(TAG, "No one-time offer found")
            onPurchaseResult(false)
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }

    /**
     * Handle a successful purchase — acknowledge it and unlock Pro.
     * Also handles PENDING state for delayed payment methods.
     */
    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    val params = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient?.acknowledgePurchase(params) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Purchase acknowledged")
                            TrialManager.setPurchased(context, purchase.purchaseToken)
                            onPurchaseResult(true)
                        } else {
                            Log.e(TAG, "Acknowledge failed: ${billingResult.debugMessage}")
                            onPurchaseResult(false)
                        }
                    }
                } else {
                    TrialManager.setPurchased(context, purchase.purchaseToken)
                    onPurchaseResult(true)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                Log.d(TAG, "Purchase pending — user will get access once payment completes")
                // Don't grant access yet, but don't report failure either
                // The purchase will complete later and checkExistingPurchases will pick it up
            }
            else -> {
                Log.w(TAG, "Purchase in unexpected state: ${purchase.purchaseState}")
                onPurchaseResult(false)
            }
        }
    }

    /**
     * Returns formatted price string (e.g., "$4.99").
     */
    fun getFormattedPrice(): String? =
        productDetails?.oneTimePurchaseOfferDetails?.formattedPrice

    /**
     * Clean up — call in Activity.onDestroy().
     */
    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
        productDetails = null
        isQueryingProduct = false
    }
}
