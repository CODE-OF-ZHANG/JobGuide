package com.zhiyin.jobguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhiyin.jobguide.ui.JobGuideApp
import com.zhiyin.jobguide.ui.JobGuideViewModel
import com.zhiyin.jobguide.ui.theme.JobGuideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JobGuideRoot()
        }
    }
}

@Composable
private fun JobGuideRoot() {
    val viewModel: JobGuideViewModel = viewModel()
    JobGuideTheme {
        JobGuideApp(viewModel)
    }
}
