package com.example

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.star4droid.mc.animation.ui.MinecraftAnimationApp

class MainActivity : ComponentActivity() {

  fun updateStatusBarVisibility() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
      controller.hide(WindowInsetsCompat.Type.statusBars())
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_FULLSCREEN
          or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      )
    } else {
      controller.show(WindowInsetsCompat.Type.statusBars())
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  fun toggleOrientation() {
    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    requestedOrientation = if (isLandscape) {
      ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    window.decorView.postDelayed({ updateStatusBarVisibility() }, 100)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    updateStatusBarVisibility()

    window.decorView.setOnApplyWindowInsetsListener { view, insets ->
      updateStatusBarVisibility()
      view.onApplyWindowInsets(insets)
    }

    @Suppress("DEPRECATION")
    window.decorView.setOnSystemUiVisibilityChangeListener {
      updateStatusBarVisibility()
    }

    setContent {
      MinecraftAnimationApp(onToggleOrientation = { toggleOrientation() })
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      updateStatusBarVisibility()
    }
  }

  override fun onResume() {
    super.onResume()
    updateStatusBarVisibility()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    updateStatusBarVisibility()
  }
}
