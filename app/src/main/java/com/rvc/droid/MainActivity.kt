package com.rvc.droid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rvc.droid.ui.theme.AppTheme
import com.rvc.droid.ui.MainScreen

class MainActivity : ComponentActivity() {
    companion object {
        val oauthTokenEvent = mutableStateOf<String?>(null)
    }

    private var permissionGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = grants.values.all { it }
        if (!permissionGranted) {
            Toast.makeText(this, "Microphone permission required for recording", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDropboxAuthIntent(intent)

        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionGranted = hasMic

        if (!hasMic) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    MainScreen(hasPermission = permissionGranted)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDropboxAuthIntent(intent)
    }

    private fun handleDropboxAuthIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "rvcdroid" && uri.host == "dropbox-auth") {
            var token: String? = null
            // Dropbox returns token in fragment (#access_token=...&token_type=bearer)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val params = fragment.split("&")
                for (p in params) {
                    val kv = p.split("=")
                    if (kv.size == 2 && kv[0] == "access_token") {
                        token = kv[1]
                        break
                    }
                }
            }
            if (token.isNullOrBlank()) {
                token = uri.getQueryParameter("access_token")
            }
            if (!token.isNullOrBlank()) {
                val prefs = getSharedPreferences("rvc_prefs", MODE_PRIVATE)
                prefs.edit().putString("dropbox_token", token).apply()
                oauthTokenEvent.value = token
                Toast.makeText(this, "Dropbox connesso con successo via OAuth!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
