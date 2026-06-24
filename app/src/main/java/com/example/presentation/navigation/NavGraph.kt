package com.example.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.di.AppModule
import com.example.presentation.ui.screens.editor.EditorScreen
import com.example.presentation.ui.screens.editor.EditorViewModel
import com.example.presentation.ui.screens.export.ExportScreen
import com.example.presentation.ui.screens.export.ExportViewModel
import com.example.presentation.ui.screens.library.LibraryScreen
import com.example.presentation.ui.screens.library.LibraryViewModel

object NavRoutes {
    const val LIBRARY = "library"
    const val EDITOR = "editor/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun editor(projectId: String) = "editor/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

@Composable
fun VideoEditionNavGraph(
    appModule: AppModule,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.LIBRARY,
        modifier = modifier
    ) {
        composable(NavRoutes.LIBRARY) {
            val viewModel: LibraryViewModel = viewModel(
                factory = appModule.provideViewModelFactory()
            )
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToEditor = { projectId ->
                    navController.navigate(NavRoutes.editor(projectId))
                }
            )
        }

        composable(
            route = NavRoutes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: EditorViewModel = viewModel(
                factory = appModule.provideViewModelFactory(projectId = projectId)
            )
            EditorScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToExport = { projId ->
                    navController.navigate(NavRoutes.export(projId))
                }
            )
        }

        composable(
            route = NavRoutes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val viewModel: ExportViewModel = viewModel(
                factory = appModule.provideViewModelFactory(projectId = projectId)
            )
            ExportScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToHome = {
                    navController.navigate(NavRoutes.LIBRARY) {
                        popUpTo(NavRoutes.LIBRARY) { inclusive = true }
                    }
                }
            )
        }
    }
}
