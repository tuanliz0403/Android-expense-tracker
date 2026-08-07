package com.example.spendtracker.data.repository

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(private val preferences: SharedPreferences) {
    var commBankPackage: String
        get() = preferences.getString(KEY_PACKAGE, DEFAULT_PACKAGE) ?: DEFAULT_PACKAGE
        set(value) { preferences.edit().putString(KEY_PACKAGE, value.trim()).apply() }

    var currency: String
        get() = preferences.getString(KEY_CURRENCY, "AUD") ?: "AUD"
        set(value) { preferences.edit().putString(KEY_CURRENCY, value.trim().uppercase()).apply() }

    var accountName: String
        get() = preferences.getString(KEY_ACCOUNT_NAME, "") ?: ""
        set(value) { preferences.edit().putString(KEY_ACCOUNT_NAME, value.trim()).apply() }

    var payId: String
        get() = preferences.getString(KEY_PAY_ID, "") ?: ""
        set(value) { preferences.edit().putString(KEY_PAY_ID, value.trim()).apply() }

    companion object {
        const val DEFAULT_PACKAGE = "com.commbank.netbank"
        private const val KEY_PACKAGE = "commbank_package"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_PAY_ID = "pay_id"
    }
}
