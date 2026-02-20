package app.rkz.airgapskd.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat.startActivity
import app.rkz.airgapskd.example.ui.theme.AirgapSDKTheme
import app.rkz.airgapsdk.QRPlayerActivity
import app.rkz.airgapsdk.QRScannerActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirgapSDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        context = this,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier, context: Context) {
    Text(
        text = "Hello $name!",
        modifier = modifier.clickable(onClick = {
//            val mlKem1024PubKey = ByteArray(1568).apply {
//                java.security.SecureRandom().nextBytes(this)
//            }
//            val intent = Intent(context, QRPlayerActivity::class.java)
//            intent.putExtra("data", mlKem1024PubKey)
//            context.startActivity(intent)
            val intent = Intent(context, QRScannerActivity::class.java)
            context.startActivity(intent)
        })
    )
}
