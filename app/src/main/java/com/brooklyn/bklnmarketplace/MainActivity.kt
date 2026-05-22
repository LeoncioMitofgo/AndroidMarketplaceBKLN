package com.brooklyn.bklnmarketplace

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.brooklyn.bklnmarketplace.ui.navigation.AppNavigation
import com.brooklyn.bklnmarketplace.ui.theme.BklnTheme
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthDeepLink(intent)
        setContent {
            BklnTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeepLink(intent)
    }

    private fun handleAuthDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme == "bklnmarketplace" && (data.host == "auth" || data.host == "reset")) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SupabaseClient.client.auth.exchangeCodeForSession(data.toString())
                    if (data.host == "reset") {
                        AuthState.awaitingPasswordReset.update { true }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
