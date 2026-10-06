package com.helboy.smartcounter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.helboy.smartcounter.ui.SmartCounterScreen
import com.helboy.smartcounter.ui.theme.SmartCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCounterTheme {
                SmartCounterScreen()
            }
        }
    }
}
