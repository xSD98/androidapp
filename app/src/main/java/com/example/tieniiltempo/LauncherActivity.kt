package com.example.tieniiltempo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tieniiltempo.ui.theme.TieniIlTempoTheme

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TieniIlTempoTheme {
                AppRoot()
            }
        }
    }
}
