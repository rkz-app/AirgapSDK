package app.rkz.airgapsdk.generator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.rkz.airgapsdk.ui.theme.AirgapSDKTheme

class QRPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.getByteArrayExtra("data")
        if (data == null) {
            finish()
            return
        }
        val title = intent.getStringExtra("title") ?: "QR Player"
        val viewModel = QRPlayerViewModel(data)
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {
                QRPlayerScreen(viewModel, title, onClose = {
                    val resultIntent = Intent()
                    setResult(RESULT_CANCELED, resultIntent)
                    finish()
                })
            }
        }
    }
}
