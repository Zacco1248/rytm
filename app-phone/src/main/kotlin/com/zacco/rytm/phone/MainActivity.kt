package com.zacco.rytm.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zacco.rytm.phone.app.RytmRoot
import com.zacco.rytm.phone.ui.theme.RytmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RytmTheme {
                RytmRoot()
            }
        }
    }
}
