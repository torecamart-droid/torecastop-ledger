package com.torecastop.ledger.ui.session

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

fun formatCurrency(value: Double): String = currencyFormat.format(value)

fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))
