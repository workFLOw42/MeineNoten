package de.workflow42.meinenoten

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.workflow42.meinenoten.ui.MainApp
import de.workflow42.meinenoten.ui.theme.MeineNotenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeineNotenTheme {
                MainApp()
            }
        }
    }
}
