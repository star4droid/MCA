package com.star4droid.mc.animation.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.star4droid.mc.animation.ui.editor.EditorScreen
import com.star4droid.mc.animation.ui.project.ProjectListScreen

private val StudioDarkColorScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF15803D),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color.White,
    background = Color(0xFF0B101B),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun MinecraftAnimationApp(
    onToggleOrientation: () -> Unit = {}
) {
    MaterialTheme(colorScheme = StudioDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "projects") {
                composable("projects") {
                    ProjectListScreen(
                        onOpenProject = { projectId ->
                            navController.navigate("editor/$projectId")
                        },
                        onToggleOrientation = onToggleOrientation
                    )
                }

                composable(
                    route = "editor/{projectId}",
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                    EditorScreen(
                        projectId = projectId,
                        onBack = {
                            navController.popBackStack()
                        },
                        onToggleOrientation = onToggleOrientation
                    )
                }
            }
        }
    }
}
