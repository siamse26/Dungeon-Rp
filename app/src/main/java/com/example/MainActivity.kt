package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.DatabaseProvider
import com.example.ui.DungeonGameApp
import com.example.ui.GameViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Constructor Injection / Singletons conforming to guidelines
    val repository = DatabaseProvider.getRepository(applicationContext)
    val viewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GameViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown GameViewModel class")
        }
    }
    val viewModel = ViewModelProvider(this, viewModelFactory)[GameViewModel::class.java]

    setContent {
      MyApplicationTheme {
        DungeonGameApp(viewModel = viewModel)
      }
    }
  }
}
