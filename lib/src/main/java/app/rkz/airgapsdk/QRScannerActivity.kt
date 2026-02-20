package app.rkz.airgapsdk

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.rkz.airgapsdk.ui.theme.AirgapSDKTheme

class QRScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = QRScannerViewModel()
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    QRScannerScreen(viewModel, onDataReceived = {
                        Toast.makeText(this, "Data received: ${it.size} bytes", Toast.LENGTH_LONG).show()
                        finish()
                    }, onClose = { finish() })
                }
            }
        }
    }
}

