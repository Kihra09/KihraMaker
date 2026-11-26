package com.kihra.maker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kihra.maker.data.GameStore
import com.kihra.maker.ui.AppRoot
import com.kihra.maker.ui.KihraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = GameStore(applicationContext)
        setContent {
            KihraTheme {
                AppRoot(store)
            }
        }
    }
}
