package com.reminderwidget

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

object BillingManager {
    const val SKU_PRO = "remindly_pro_yearly"

    @Volatile var isSubscribed = false
        private set

    private var billingClient: BillingClient? = null

    fun init(ctx: Context) {
        billingClient = BillingClient.newBuilder(ctx.applicationContext)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { p ->
                        if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
                            billingClient?.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                            ) {}
                        }
                    }
                    isSubscribed = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                }
            }
            .enablePendingPurchases()
            .build()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) checkSubscription()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun checkSubscription() {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, purchases ->
            isSubscribed = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SKU_PRO)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()
        client.queryProductDetailsAsync(params) { _, detailsList ->
            val details = detailsList.firstOrNull() ?: return@queryProductDetailsAsync
            val token   = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@queryProductDetailsAsync
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(token)
                        .build()
                )).build()
            activity.runOnUiThread { client.launchBillingFlow(activity, flowParams) }
        }
    }
}
