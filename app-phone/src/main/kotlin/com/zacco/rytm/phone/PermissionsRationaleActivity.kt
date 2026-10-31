package com.zacco.rytm.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Ekran, który Health Connect pokazuje, gdy użytkownik pyta, po co Rytmowi dane. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text(
                    text = "Rytm to osobisty coach treningu. Czyta sen, tętno, treningi, żywienie i pomiary ciała " +
                        "z Health Connect, żeby liczyć regenerację, obciążenie i plan dnia. " +
                        "Dane zostają na telefonie i nie są nigdzie wysyłane.",
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                )
            }
        }
    }
}
