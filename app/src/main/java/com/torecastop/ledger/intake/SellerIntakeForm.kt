package com.torecastop.ledger.intake

/**
 * The seller intake link shown as a QR code on a saved trade — from the
 * planning doc: "Create QR code for a google docs form ... for any seller
 * that comes to our table ... linked to a specific sale."
 *
 * This app can't read back a Google Form response automatically (that needs
 * the Forms/Sheets API — a bigger integration than a sideloaded, offline-first
 * ledger warrants for a first pass). Instead the link carries the trade's own
 * id as a reference code; the form should ask the seller to copy that code in
 * so staff can match the response to this trade by eye afterwards.
 *
 * [FORM_URL_TEMPLATE] is a Google Form URL (or any hosted form) with a `{ref}`
 * placeholder — set once the team has built the form. Left blank, the seller
 * intake button doesn't appear; nothing here ever touches the network itself,
 * the QR is generated entirely on-device.
 */
object SellerIntakeForm {

    // TODO(team): point this at the seller intake Google Form, keeping {ref}
    // somewhere in the URL (e.g. as a prefilled entry.* query param) so it
    // shows up in the form response.
    private const val FORM_URL_TEMPLATE = ""

    val isConfigured: Boolean get() = FORM_URL_TEMPLATE.isNotBlank()

    /** The seller-facing URL for one trade, or null until a URL is configured. */
    fun urlFor(tradeId: Long): String? {
        if (!isConfigured) return null
        return FORM_URL_TEMPLATE.replace("{ref}", tradeId.toString())
    }
}
