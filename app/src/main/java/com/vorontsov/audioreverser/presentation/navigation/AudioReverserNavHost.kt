package com.vorontsov.audioreverser.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vorontsov.audioreverser.presentation.history.HistoryScreen
import com.vorontsov.audioreverser.presentation.home.HomeEffect
import com.vorontsov.audioreverser.presentation.home.HomeScreen
import com.vorontsov.audioreverser.presentation.home.HomeViewModel
import com.vorontsov.audioreverser.presentation.round.RoundScreen

@Composable
fun AudioReverserNavHost() {
    val navController = rememberNavController()

    val navigateToRound: (String) -> Unit = { roundId ->
        navController.navigate(Routes.round(roundId))
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is HomeEffect.NavigateToRound -> navigateToRound(effect.roundId)
                    }
                }
            }

            HomeScreen(
                state = state,
                onIntent = viewModel::onIntent,
                onOpenRound = navigateToRound,
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenRound = navigateToRound,
                onCreateRound = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.ROUND,
            arguments = listOf(navArgument(Routes.ARG_ROUND_ID) { type = NavType.StringType }),
        ) {
            RoundScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME)
                    }
                },
            )
        }
    }
}
