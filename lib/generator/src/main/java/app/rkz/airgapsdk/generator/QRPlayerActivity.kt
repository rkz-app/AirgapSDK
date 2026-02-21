package app.rkz.airgapsdk.generator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.rkz.airgapsdk.ui.theme.AirgapSDKTheme

class QRPlayerActivity : ComponentActivity() {

    private val viewModel: QRPlayerViewModel by viewModels {
        viewModelFactory {
            initializer {
                QRPlayerViewModel(intent.getByteArrayExtra("data")!!)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getByteArrayExtra("data") == null) {
            finish()
            return
        }
        val title = intent.getStringExtra("title") ?: "QR Player"
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {
                QRPlayerScreen(viewModel, title, onClose = {
                    setResult(RESULT_CANCELED)
                    finish()
                })
            }
        }
    }
}
