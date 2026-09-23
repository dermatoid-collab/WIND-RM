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
import com.windrm.app.ui.forecast.ForecastScreen
import com.windrm.app.ui.forecast.ForecastViewModel
import com.windrm.app.ui.home.HomeScreen
import com.windrm.app.ui.home.HomeViewModel
import com.windrm.app.ui.routedetail.RouteDetailScreen
import com.windrm.app.ui.routedetail.RouteDetailViewModel
import com.windrm.app.ui.routes.RoutesListScreen
import com.windrm.app.ui.routes.RoutesListViewModel

@Composable
fun WindRmNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            val appContext = LocalContext.current.applicationContext
            val viewModel = viewModel<HomeViewModel>(
                factory = viewModelFactory {
                    initializer { HomeViewModel(appContext, container.weatherRepository, container.routeRepository) }
                },
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenRecent = { navController.navigate(Destination.RoutesList.path(0)) },
                onOpenStrava = { navController.navigate(Destination.RoutesList.path(1)) },
                onRouteImported = { route -> navController.navigate(Destination.RouteDetail.path(route.id)) },
            )
        }

        composable(
            route = Destination.RoutesList.route,
            arguments = listOf(navArgument(Destination.RoutesList.ARG_INITIAL_TAB) { type = NavType.IntType }),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt(Destination.RoutesList.ARG_INITIAL_TAB) ?: 0
            val viewModel = viewModel<RoutesListViewModel>(
                factory = viewModelFactory {
                    initializer { RoutesListViewModel(container.routeRepository, container.stravaRepository, container.stravaAuthManager) }
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
                    initializer { RouteDetailViewModel(container.routeRepository, routeId) }
                },
            )
            RouteDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onForecast = { startEpochS, speedKmh ->
                    navController.navigate(Destination.Forecast.path(routeId, startEpochS, speedKmh))
                },
            )
        }

        composable(
            route = Destination.Forecast.route,
            arguments = listOf(
                navArgument(Destination.Forecast.ARG_ROUTE_ID) { type = NavType.LongType },
                navArgument(Destination.Forecast.ARG_START_EPOCH) { type = NavType.LongType },
                navArgument(Destination.Forecast.ARG_SPEED) { type = NavType.FloatType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments ?: return@composable
            val routeId = args.getLong(Destination.Forecast.ARG_ROUTE_ID)
            val startEpoch = args.getLong(Destination.Forecast.ARG_START_EPOCH)
            val speed = args.getFloat(Destination.Forecast.ARG_SPEED).toDouble()
            val viewModel = viewModel<ForecastViewModel>(
                factory = viewModelFactory {
                    initializer {
                        ForecastViewModel(container.routeRepository, container.weatherRepository, routeId, startEpoch, speed)
                    }
                },
            )
            ForecastScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
