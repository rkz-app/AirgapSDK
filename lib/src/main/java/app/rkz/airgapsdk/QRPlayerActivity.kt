package app.rkz.airgapsdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.rkz.airgapsdk.ui.theme.AirgapSDKTheme

class QRPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.getByteArrayExtra("data")
        if (data == null) {
            finish()
            return
        }
        val viewModel = QRPlayerViewModel(data)
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    QRPlayerScreen(viewModel, "QR Player", onClose = { finish() })
                }
            }
        }
    }
}