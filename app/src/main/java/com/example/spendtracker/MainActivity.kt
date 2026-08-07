package com.example.spendtracker

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.spendtracker.service.CommBankNotificationListener
import com.example.spendtracker.ui.SpendTrackerApp
import com.example.spendtracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var accessEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshAccess()
        setContent {
            ExpenseTrackerTheme {
                Surface(Modifier.fillMaxSize()) {
                    SpendTrackerApp(
                        notificationAccessEnabled = accessEnabled,
                        openNotificationSettings = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                }
            }
        }
    }

    override fun onResume() { super.onResume(); refreshAccess() }

    private fun refreshAccess() {
        val expected = ComponentName(this, CommBankNotificationListener::class.java)
        accessEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) &&
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == expected } == true
    }
}
