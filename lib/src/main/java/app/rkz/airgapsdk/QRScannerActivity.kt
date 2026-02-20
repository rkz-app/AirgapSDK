package app.rkz.airgapsdk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.rkz.airgapsdk.ui.theme.AirgapSDKTheme

class QRScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = QRScannerViewModel()
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {

                    QRScannerScreen(viewModel, onDataReceived = { byteArray ->
                        val resultIntent = Intent().apply {
                            putExtra("data", byteArray)  // Your data
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }, onClose = {
                        val resultIntent = Intent()
                        setResult(RESULT_CANCELED, resultIntent)
                        finish()
                    })
            }
        }
    }
}

