package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FixNowViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33) to prevent settings blockade
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissionState = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        // Initialize Supabase HTTP client and authorization handlers
        com.example.data.SupabaseClient.initialize(this)

        // Secure ViewModel initiation using standard Provider passing Application instance
        val viewModel = ViewModelProvider(this)[FixNowViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val currentMode by viewModel.currentMode.collectAsState()
                val notifications by viewModel.notifications.collectAsState()

                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Dynamic routing based on switch mode
                    when (currentMode) {
                        "Onboarding" -> {
                            RoleSelectionScreen(
                                onRoleSelected = { viewModel.switchMode(it) }
                            )
                        }
                        "Customer" -> {
                            CustomerSpace(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "Technician" -> {
                            TechnicianSpace(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "Admin" -> {
                            AdminSpace(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Overlay Slide-Down FCM & WhatsApp Notifications Simulator
                    PushNotificationTray(
                        notifications = notifications,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
