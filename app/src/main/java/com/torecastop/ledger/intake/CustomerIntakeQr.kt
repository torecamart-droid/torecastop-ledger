package com.torecastop.ledger.intake

import java.net.URLEncoder
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

/**
 * Customer self-serve contact capture: staff shows a QR linking to a small
 * static form (docs/intake.html, hosted on GitHub Pages) that the customer
 * fills in on their own phone; the page renders their answers back as a
 * second QR, with no server involved anywhere. Staff scans that back in.
 *
 * The nonce (not a Room trade id) correlates the two QRs — it's generated
 * fresh per attempt and lives only in Compose state, so this works before a
 * trade is ever saved. Replaces the old, never-configured
 * SellerIntakeForm/SellerIntakeQrDialog (v1.3), which needed a persisted
 * trade id and could only round-trip through a spreadsheet a human matched
 * up by eye.
 */
object CustomerIntakeQr {

    private const val BASE_URL = "https://torecamart-droid.github.io/torecastop-ledger/intake.html"
    private const val FORMAT_TAG = "TSLI2"

    // Defensive caps so a pathological item count/length can't overflow the
    // outgoing QR encoder's capacity (v1.4 — items summary added to the URL).
    private const val MAX_SUMMARY_ITEMS = 30
    private const val MAX_SUMMARY_LABEL_CHARS = 40

    private val random = SecureRandom()

    /** 40 bits — enough to avoid accidental cross-attribution between two customers' scans. */
    fun generateNonce(): String {
        val bytes = ByteArray(5)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * [items] is a snapshot of the trade's current draft lines (both OUT and
     * IN), shown to the customer before they generate their response code —
     * so they can see what they're confirming, not just blindly fill in
     * contact details.
     */
    fun urlFor(nonce: String, items: List<TradeItemSummary> = emptyList()): String {
        val base = "$BASE_URL?n=$nonce"
        if (items.isEmpty()) return base
        val array = JSONArray()
        items.take(MAX_SUMMARY_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("d", item.direction)
                    .put("l", item.label.take(MAX_SUMMARY_LABEL_CHARS))
                    .put("q", item.quantity)
                    .put("v", item.saleCost)
            )
        }
        return "$base&i=${URLEncoder.encode(array.toString(), "UTF-8")}"
    }

    fun parseScan(raw: String, expectedNonce: String): CustomerIntakeScan {
        // 6 parts — List destructuring only defines component1..component5,
        // so this is indexed access, not a val (_, ...) = parts destructure.
        val parts = raw.trim().split("|", limit = 6)
        if (parts.size != 6 || parts[0] != FORMAT_TAG) return CustomerIntakeScan.NotRecognized
        if (parts[1] != expectedNonce) return CustomerIntakeScan.NonceMismatch
        return CustomerIntakeScan.Success(
            name = parts[2].takeIf { it.isNotBlank() },
            phone = parts[3].takeIf { it.isNotBlank() },
            email = parts[4].takeIf { it.isNotBlank() },
            address = parts[5].takeIf { it.isNotBlank() }
        )
    }

    /**
     * Purpose-built projection of a draft trade line for the outgoing QR's
     * items summary — kept independent of ui.session so this package stays
     * dependency-free.
     */
    data class TradeItemSummary(
        val direction: String,
        val label: String,
        val quantity: Int,
        val saleCost: Double
    )
}

sealed interface CustomerIntakeScan {
    data class Success(
        val name: String?,
        val phone: String?,
        val email: String?,
        val address: String?
    ) : CustomerIntakeScan
    data object NotRecognized : CustomerIntakeScan
    data object NonceMismatch : CustomerIntakeScan
}
