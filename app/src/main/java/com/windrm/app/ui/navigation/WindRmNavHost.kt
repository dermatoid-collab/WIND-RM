package com.windrm.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.windrm.app.di.AppContainer
import com.windrm.app.domain.PacingMode
import com.windrm.app.ui.builder.RouteBuilderScreen
import com.windrm.app.ui.builder.RouteBuilderViewModel
import com.windrm.app.ui.forecast.ForecastScreen
import com.windrm.app.ui.forecast.ForecastViewModel
import com.windrm.app.ui.home.HomeScreen
import com.windrm.app.ui.home.HomeViewModel
import com.windrm.app.ui.routedetail.RouteDetailScreen
import com.windrm.app.ui.routedetail.RouteDetailViewModel
import com.windrm.app.ui.routes.RoutesListScreen
import com.windrm.app.ui.routes.RoutesListViewModel
import com.windrm.app.ui.routes.TAB_FAVORITES
import com.windrm.app.ui.routes.TAB_FILES
import com.windrm.app.ui.routes.TAB_RECENT
import com.windrm.app.ui.routes.TAB_STRAVA
import com.windrm.app.ui.settings.SettingsScreen
import com.windrm.app.ui.settings.SettingsViewModel

@Composable
fun WindRmNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            val appContext = LocalContext.current.applicationContext
            val viewModel = viewModel<HomeViewModel>(
                factory = viewModelFactory {
                    initializer { HomeViewModel(appContext, container.weatherRepository, container.routeRepository, container.settingsRepository) }
                },
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenRecent = { navController.navigate(Destination.RoutesList.path(TAB_RECENT)) },
                onOpenFavorites = { navController.navigate(Destination.RoutesList.path(TAB_FAVORITES)) },
                onOpenStrava = { navController.navigate(Destination.RoutesList.path(TAB_STRAVA)) },
                onOpenFiles = { navController.navigate(Destination.RoutesList.path(TAB_FILES)) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onCreateRoute = { navController.navigate(Destination.RouteBuilder.route) },
                onRouteImported = { route -> navController.navigate(Destination.RouteDetail.path(route.id)) },
            )
        }

        composable(Destination.RouteBuilder.route) {
            val appContext = LocalContext.current.applicationContext
            val viewModel = viewModel<RouteBuilderViewModel>(
                factory = viewModelFactory {
                    initializer {
                        RouteBuilderViewModel(appContext, container.routeRepository, container.settingsRepository, container.routingRepository)
                    }
                },
            )
            RouteBuilderScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // The saved route replaces the builder in the back stack, so Back from its screen goes Home.
                onSaved = { route ->
                    navController.navigate(Destination.RouteDetail.path(route.id)) {
                        popUpTo(Destination.RouteBuilder.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Destination.Settings.route) {
            val appContext = LocalContext.current.applicationContext
            val viewModel = viewModel<SettingsViewModel>(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(appContext, container.settingsRepository, container.stravaAuthManager) }
                },
            )
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(
            route = Destination.RoutesList.route,
            arguments = listOf(navArgument(Destination.RoutesList.ARG_INITIAL_TAB) { type = NavType.IntType }),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt(Destination.RoutesList.ARG_INITIAL_TAB) ?: 0
            val viewModel = viewModel<RoutesListViewModel>(
                factory = viewModelFactory {
                    initializer { RoutesListViewModel(container.routeRepository, container.stravaRepository, container.stravaAuthManager, container.settingsRepository) }
                },
            )
            RoutesListScreen(
                viewModel = viewModel,
                initialTab = initialTab,
                onBack = { navController.popBackStack() },
                onRouteSelected = { route -> navController.navigate(Destination.RouteDetail.path(route.id)) },
            )
        }

        composable(
            route = Destination.RouteDetail.route,
            arguments = listOf(navArgument(Destination.RouteDetail.ARG_ROUTE_ID) { type = NavType.LongType }),
        ) { backStackEntry ->
            val routeId = backStackEntry.arguments?.getLong(Destination.RouteDetail.ARG_ROUTE_ID) ?: return@composable
            val viewModel = viewModel<RouteDetailViewModel>(
                factory = viewModelFactory {
                    initializer { RouteDetailViewModel(container.routeRepository, container.settingsRepository, routeId) }
                },
            )
            RouteDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onForecast = { startEpochS, speedKmh, pacing, crop ->
                    navController.navigate(Destination.Forecast.path(routeId, startEpochS, speedKmh, pacing, crop.start, crop.endInclusive))
                },
            )
        }

        composable(
            route = Destination.Forecast.route,
            arguments = listOf(
                navArgument(Destination.Forecast.ARG_ROUTE_ID) { type = NavType.LongType },
                navArgument(Destination.Forecast.ARG_START_EPOCH) { type = NavType.LongType },
                navArgument(Destination.Forecast.ARG_SPEED) { type = NavType.FloatType },
                navArgument(Destination.Forecast.ARG_PACING) { type = NavType.StringType },
                navArgument(Destination.Forecast.ARG_CROP_START) { type = NavType.FloatType },
                navArgument(Destination.Forecast.ARG_CROP_END) { type = NavType.FloatType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments ?: return@composable
            val routeId = args.getLong(Destination.Forecast.ARG_ROUTE_ID)
            val startEpoch = args.getLong(Destination.Forecast.ARG_START_EPOCH)
            val speed = args.getFloat(Destination.Forecast.ARG_SPEED).toDouble()
            val pacing = args.getString(Destination.Forecast.ARG_PACING)
                ?.let { runCatching { PacingMode.valueOf(it) }.getOrNull() } ?: PacingMode.CONSTANT
            val crop = args.getFloat(Destination.Forecast.ARG_CROP_START).toDouble()..args.getFloat(Destination.Forecast.ARG_CROP_END).toDouble()
            val viewModel = viewModel<ForecastViewModel>(
                factory = viewModelFactory {
                    initializer {
                        ForecastViewModel(container.routeRepository, container.weatherRepository, container.settingsRepository, routeId, startEpoch, speed, pacing, crop)
                    }
                },
            )
            ForecastScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
