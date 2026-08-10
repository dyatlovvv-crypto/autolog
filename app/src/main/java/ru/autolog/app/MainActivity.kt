package ru.autolog.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import ru.autolog.app.notifications.ReminderCheckWorker
import ru.autolog.app.notifications.ReminderNotifications
import ru.autolog.app.ui.glass.LiquidMeshBackground
import ru.autolog.app.ui.navigation.AutologNavHost
import ru.autolog.app.ui.theme.AutologTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) ReminderCheckWorker.enqueue(this)
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* map screen uses WebView geolocation after grant */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderNotifications.ensureChannel(this)
        if (!isUnderEspressoTest()) {
            requestRuntimePermissions()
        } else {
            ReminderCheckWorker.enqueue(this)
        }
        setContent {
            AutologAppRoot()
        }
    }

    /** UI tests fail if system permission dialogs cover the app. */
    private fun isUnderEspressoTest(): Boolean {
        return try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val notif = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, notif) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(notif)
            } else {
                ReminderCheckWorker.enqueue(this)
            }
        } else {
            ReminderCheckWorker.enqueue(this)
        }

        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val needLoc = listOf(fine, coarse).any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needLoc) {
            locationPermission.launch(arrayOf(fine, coarse))
        }
    }
}

@Composable
fun AutologAppRoot() {
    AutologTheme {
        LiquidMeshBackground(modifier = Modifier.fillMaxSize()) {
            AutologNavHost()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AutologAppRootPreview() {
    AutologAppRoot()
}
