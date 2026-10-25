package com.returnables.app.data

import com.returnables.app.domain.Purchase
import com.returnables.app.domain.PurchaseRules
import com.returnables.app.domain.PurchaseStatus
import org.json.JSONObject
import java.time.LocalDate

object PurchaseJson {
    internal val receiptPattern = Regex("[a-f0-9]{64}\\.(jpg|png|webp|pdf)")

    fun encode(p: Purchase): JSONObject {
        validate(p)
        return JSONObject().apply {
            put("id", p.id); put("title", p.title); put("merchant", p.merchant)
            put("purchasedOn", p.purchasedOn.toString()); put("amountMinor", p.amountMinor ?: JSONObject.NULL)
            put("currency", p.currency); put("returnBy", p.returnBy?.toString() ?: JSONObject.NULL)
            put("warrantyUntil", p.warrantyUntil?.toString() ?: JSONObject.NULL); put("notes", p.notes)
            put("receiptName", p.receiptName ?: JSONObject.NULL); put("receiptMime", p.receiptMime ?: JSONObject.NULL)
            put("ocrText", p.ocrText); put("status", p.status.name)
            put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
        }
    }

    fun decode(o: JSONObject): Purchase = try {
        val p = Purchase(
            id = string(o, "id"), title = string(o, "title"), merchant = string(o, "merchant"),
            purchasedOn = LocalDate.parse(string(o, "purchasedOn")), amountMinor = optionalLong(o, "amountMinor"),
            currency = string(o, "currency"), returnBy = optionalString(o, "returnBy")?.let(LocalDate::parse),
            warrantyUntil = optionalString(o, "warrantyUntil")?.let(LocalDate::parse), notes = string(o, "notes"),
            receiptName = optionalString(o, "receiptName"), receiptMime = optionalString(o, "receiptMime"),
            ocrText = string(o, "ocrText"), status = PurchaseStatus.valueOf(string(o, "status")),
            createdAt = integer(o, "createdAt"), updatedAt = integer(o, "updatedAt"),
        )
        validate(p)
        p
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid purchase record: ${e.message}", e)
    }

    private fun validate(p: Purchase) {
        val errors = PurchaseRules.validate(p)
        require(errors.isEmpty()) { errors.joinToString(" ") }
        require(p.receiptName == null || receiptPattern.matches(p.receiptName)) { "Receipt filename is invalid." }
        require((p.receiptName == null) == (p.receiptMime == null)) { "Receipt filename and type must be provided together." }
        if (p.receiptName != null) {
            val expected = when (p.receiptName.substringAfterLast('.')) {
                "jpg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; else -> "application/pdf"
            }
            require(p.receiptMime == expected) { "Receipt MIME type does not match its filename." }
        }
    }

    internal fun string(o: JSONObject, key: String): String {
        val value = o.get(key)
        require(value is String) { "$key must be text." }
        return value
    }

    private fun optionalString(o: JSONObject, key: String): String? {
        require(o.has(key)) { "Missing $key." }
        return if (o.isNull(key)) null else string(o, key)
    }

    private fun optionalLong(o: JSONObject, key: String): Long? {
        require(o.has(key)) { "Missing $key." }
        return if (o.isNull(key)) null else integer(o, key)
    }

    internal fun integer(o: JSONObject, key: String): Long {
        val value = o.get(key)
        require(value is Long || value is Int) { "$key must be an exact integer." }
        return (value as Number).toLong()
    }
}
