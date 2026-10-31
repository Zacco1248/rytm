package com.zacco.rytm.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.compose.material3.MaterialTheme
import com.zacco.rytm.wear.hrv.HrvScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HrvScreen()
            }
        }
    }
}
