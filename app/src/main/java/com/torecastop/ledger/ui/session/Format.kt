package com.torecastop.ledger.ui.session

import androidx.compose.ui.text.TextStyle
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// AUD explicitly (decision — prices are Australian dollars); renders as "$".
private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU"))
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

fun formatCurrency(value: Double): String = currencyFormat.format(value)

/** "+$80.00" / "-$20.00" / "$0.00" — for value-added and trade-cash figures. */
fun formatSignedCurrency(value: Double): String =
    if (value > 0.0049) "+" + currencyFormat.format(value) else currencyFormat.format(value)

/** Times and dates render in the device's local time zone (Adelaide on site). */
fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))

fun formatDate(epochMillis: Long): String = dateFormat.format(Date(epochMillis))

/** Money input: digits with up to two decimals, allowing partial typing ("1."). */
internal val MONEY_INPUT_REGEX = Regex("^\\d*\\.?\\d{0,2}$")

/**
 * Tabular (fixed-width) figures so currency reads like a till display and
 * digits line up cleanly — applied to the headline totals. (v1.3)
 */
fun TextStyle.tabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * A sale total (or either side of a trade) at or above this asks for a quick
 * confirmation before saving — a guard against a fat-fingered price or quantity
 * on the big-ticket cards. Normal small sales save straight through. (v1.3)
 */
const val HIGH_VALUE_CONFIRM_THRESHOLD = 200.0
