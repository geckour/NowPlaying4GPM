package com.geckour.nowplaying4gpm.api

import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import com.android.vending.billing.IInAppBillingService
import com.geckour.nowplaying4gpm.api.model.SkuDetail
import com.geckour.nowplaying4gpm.util.parseOrNull
import com.geckour.nowplaying4gpm.util.json
import com.geckour.nowplaying4gpm.util.withCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BillingApiClient(private val service: IInAppBillingService) {

    enum class ResponseCode(val code: Int) {
        RESPONSE_OK(0)
    }

    companion object {
        const val API_VERSION = 3
        const val BILLING_TYPE = "inapp"
        const val BUNDLE_KEY_RESPONSE_CODE = "RESPONSE_CODE"
        const val BUNDLE_KEY_SKU_DETAIL_LIST = "DETAILS_LIST"
        const val BUNDLE_KEY_PURCHASE_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST"
        const val BUNDLE_KEY_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST"
        const val BUNDLE_KEY_BUY_INTENT = "BUY_INTENT"
        const val BUNDLE_KEY_PURCHASE_DATA = "INAPP_PURCHASE_DATA"
        const val QUERY_KEY_SKU_DETAILS = "ITEM_ID_LIST"
    }

    suspend fun getPurchasedItems(context: Context): List<String> = withContext(Dispatchers.IO) {
        withCatching {
            service.getPurchases(
                API_VERSION,
                context.packageName,
                BILLING_TYPE,
                null
            ).getStringArrayList(BUNDLE_KEY_PURCHASE_ITEM_LIST)
        } ?: emptyList<String>()
    }

    suspend fun getSkuDetails(context: Context, vararg skus: String): List<SkuDetail> =
        withContext(Dispatchers.IO) {
            withCatching {
                service.getSkuDetails(
                    API_VERSION,
                    context.packageName,
                    BILLING_TYPE,
                    Bundle().apply {
                        putStringArrayList(
                            QUERY_KEY_SKU_DETAILS,
                            ArrayList(skus.toList())
                        )
                    }
                ).let {
                    if (it.getInt(BUNDLE_KEY_RESPONSE_CODE) == ResponseCode.RESPONSE_OK.code) {
                        it.getStringArrayList(BUNDLE_KEY_SKU_DETAIL_LIST).orEmpty().mapNotNull {
                            json.parseOrNull<SkuDetail>(it)
                        }
                    } else emptyList()
                }
            } ?: emptyList()
        }

    fun getBuyIntent(context: Context, sku: String): PendingIntent? =
        service.getBuyIntent(API_VERSION, context.packageName, sku, BILLING_TYPE, null)?.let {
            if (it.containsKey(BUNDLE_KEY_RESPONSE_CODE)
                && it.getInt(BUNDLE_KEY_RESPONSE_CODE) == 0
            )
                it.getParcelable(BUNDLE_KEY_BUY_INTENT)
            else null
        }
}