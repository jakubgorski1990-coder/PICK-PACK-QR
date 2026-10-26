package pl.pickpack.panel.data.baselinker

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pl.pickpack.panel.domain.BlStatus
import pl.pickpack.panel.domain.PoolOrder
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Read-only BaseLinker connector client.
 * Endpoint: https://api.baselinker.com/connector.php
 * Auth: X-BLToken header (never logged).
 */
class BaseLinkerClient(
    private val tokenProvider: () -> String?,
) {
    data class ApiException(val code: String?, override val message: String) : Exception(message)

    suspend fun getOrderStatusList(): List<BlStatus> = withContext(Dispatchers.IO) {
        val root = call("getOrderStatusList", JSONObject())
        val arr = root.optJSONArray("statuses") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    BlStatus(
                        id = o.getInt("id"),
                        name = o.optString("name"),
                        color = o.optString("color").ifBlank { null },
                    )
                )
            }
        }
    }

    /**
     * Fetches up to 100 orders. Paginate with [idFrom] (next = max order_id).
     * Does **not** call getOrderPickPackHistory (not a pool counter).
     */
    suspend fun getOrders(
        statusId: Int?,
        idFrom: Long? = null,
        getUnconfirmed: Boolean = false,
    ): List<PoolOrder> = withContext(Dispatchers.IO) {
        val params = JSONObject()
        if (statusId != null) params.put("status_id", statusId)
        if (idFrom != null) params.put("id_from", idFrom)
        params.put("get_unconfirmed_orders", getUnconfirmed)
        val root = call("getOrders", params)
        val arr = root.optJSONArray("orders") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    PoolOrder(
                        orderId = o.getLong("order_id"),
                        statusId = o.optInt("order_status_id", statusId ?: 0),
                        deliveryMethodId = o.optInt("delivery_method_id").takeIf {
                            o.has("delivery_method_id") && !o.isNull("delivery_method_id")
                        },
                        deliveryMethodName = o.optString("delivery_method").ifBlank { null },
                        confirmed = o.optBoolean("confirmed", true),
                    )
                )
            }
        }
    }

    private fun call(method: String, parameters: JSONObject): JSONObject {
        val token = tokenProvider()
            ?: throw ApiException(null, "Brak tokenu BaseLinker")
        val body = buildString {
            append("method=").append(URLEncoder.encode(method, "UTF-8"))
            append("&parameters=").append(URLEncoder.encode(parameters.toString(), "UTF-8"))
        }
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 40_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("X-BLToken", token)
        }
        try {
            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) {
                throw ApiException(code.toString(), "HTTP $code")
            }
            val json = JSONObject(text)
            if (json.optString("status") == "ERROR") {
                throw ApiException(
                    json.optString("error_code").ifBlank { null },
                    json.optString("error_message").ifBlank { "Błąd API BaseLinker" },
                )
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val ENDPOINT = "https://api.baselinker.com/connector.php"
    }
}
