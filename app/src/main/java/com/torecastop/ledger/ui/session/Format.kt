package com.torecastop.ledger.ui.session

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
