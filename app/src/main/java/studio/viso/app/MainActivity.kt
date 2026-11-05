package com.creatorstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.creatorstudio.app.ui.CreatorStudioApp
import com.creatorstudio.app.ui.theme.CreatorStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreatorStudioTheme { CreatorStudioApp() }
        }
    }
}
