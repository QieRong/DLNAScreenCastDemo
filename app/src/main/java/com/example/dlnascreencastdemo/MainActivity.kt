package com.example.dlnascreencastdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dlnascreencastdemo.feature.home.HomeScreen
import com.example.dlnascreencastdemo.feature.home.HomeUiState
import com.example.dlnascreencastdemo.ui.theme.DLNAScreenCastDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DLNAScreenCastDemoTheme {
                HomeScreen(state = HomeUiState())
            }
        }
    }
}
