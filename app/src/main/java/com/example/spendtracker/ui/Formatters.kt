package com.example.spendtracker.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private val australianLocale = Locale.forLanguageTag("en-AU")

fun formatAud(cents: Long): String = NumberFormat.getCurrencyInstance(australianLocale).apply {
    currency = Currency.getInstance("AUD")
}.format(cents / 100.0)

fun formatAustralianDate(timestamp: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", australianLocale).format(Date(timestamp)).lowercase()
