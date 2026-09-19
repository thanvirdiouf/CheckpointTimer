package com.example.checkpointtimer.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.checkpointtimer.ui.screens.TemplateEditorScreen
import com.example.checkpointtimer.ui.screens.TemplateListScreen
import com.example.checkpointtimer.ui.screens.TimerScreen
import com.example.checkpointtimer.viewmodel.AppViewModelProvider
import com.example.checkpointtimer.viewmodel.TimerViewModel

private const val ROUTE_TEMPLATE_LIST = "templates"
private const val ROUTE_EDITOR = "editor/{templateId}"
private const val ROUTE_TIMER = "timer"
private const val ARG_TEMPLATE_ID = "templateId"

private fun editorRoute(templateId: Long) = "editor/$templateId"

@Composable
fun CheckpointTimerNavHost(
    navController: NavHostController = rememberNavController(),
    timerViewModel: TimerViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    NavHost(navController = navController, startDestination = ROUTE_TEMPLATE_LIST) {

        composable(ROUTE_TEMPLATE_LIST) {
            TemplateListScreen(
                onStartTemplate = { templateId ->
                    timerViewModel.start(templateId)
                    navController.navigate(ROUTE_TIMER)
                },
                onOpenRunningTimer = { navController.navigate(ROUTE_TIMER) },
                onCreateTemplate = { navController.navigate(editorRoute(0L)) },
                onEditTemplate = { templateId -> navController.navigate(editorRoute(templateId)) },
            )
        }

        composable(
            route = ROUTE_EDITOR,
            arguments = listOf(navArgument(ARG_TEMPLATE_ID) { type = NavType.LongType }),
        ) { entry ->
            TemplateEditorScreen(
                templateId = entry.arguments?.getLong(ARG_TEMPLATE_ID) ?: 0L,
                onDone = { navController.popBackStack() },
            )
        }

        composable(ROUTE_TIMER) {
            TimerScreen(onExit = { navController.popBackStack(ROUTE_TEMPLATE_LIST, inclusive = false) })
        }
    }
}
