package dev.clipforge.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.clipforge.app.ui.screens.HomeScreen
import dev.clipforge.app.ui.screens.JobDetailScreen
import dev.clipforge.app.ui.screens.NewJobScreen
import dev.clipforge.app.ui.screens.SettingsScreen

object Routes {
    const val HOME = "home"
    const val NEW_JOB = "new_job"
    const val SETTINGS = "settings"
    const val JOB_DETAIL = "job/{jobId}"
    fun jobDetail(jobId: String) = "job/$jobId"
}

@Composable
fun ClipForgeNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewJob = { navController.navigate(Routes.NEW_JOB) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenJob = { id -> navController.navigate(Routes.jobDetail(id)) },
            )
        }
        composable(Routes.NEW_JOB) {
            NewJobScreen(
                onJobCreated = { id ->
                    navController.navigate(Routes.jobDetail(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.JOB_DETAIL,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
            JobDetailScreen(jobId = jobId, onBack = { navController.popBackStack() })
        }
    }
}
