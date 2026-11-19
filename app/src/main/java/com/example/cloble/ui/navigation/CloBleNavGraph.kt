package com.example.cloble.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cloble.CloBleApplication
import com.example.cloble.ui.components.CloBleBottomNavBar
import com.example.cloble.ui.components.CloBleBottomTab
import com.example.cloble.ui.screens.auth.LoginScreen
import com.example.cloble.ui.screens.auth.RegisterScreen
import com.example.cloble.ui.screens.chat.ChatListScreen
import com.example.cloble.ui.screens.chat.ConversationScreen
import com.example.cloble.ui.screens.discover.DiscoverScreen
import com.example.cloble.ui.screens.discover.FilterScreen
import com.example.cloble.ui.screens.discover.ItemDetailScreen
import com.example.cloble.ui.screens.history.ActivityHistoryScreen
import com.example.cloble.ui.screens.matched.MatchedScreen
import com.example.cloble.ui.screens.notifications.NotificationsScreen
import com.example.cloble.ui.screens.preferences.PreferencesScreen
import com.example.cloble.ui.screens.profile.EditProfileScreen
import com.example.cloble.ui.screens.profile.MyProfileScreen
import com.example.cloble.ui.screens.profile.OtherUserProfileScreen
import com.example.cloble.ui.screens.review.RateUserScreen
import com.example.cloble.ui.screens.showcase.DesignSystemShowcaseScreen
import com.example.cloble.ui.screens.wardrobe.AddEditClothingScreen
import com.example.cloble.ui.screens.wardrobe.WardrobeScreen
import com.example.cloble.ui.theme.CloBleColors
import com.example.cloble.ui.theme.CloBleTextStyles

sealed interface SessionGateState {
    data object Loading : SessionGateState
    data object Unauthenticated : SessionGateState
    data class Authenticated(val userId: Long) : SessionGateState
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CloBleNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String? = null,
    pendingRoute: String? = null,
    onRouteHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as? CloBleApplication
    val authRepository = app?.container?.authRepository

    val sessionGateState by produceState<SessionGateState>(
        initialValue = if (startDestination != null) SessionGateState.Unauthenticated else SessionGateState.Loading,
        key1 = authRepository
    ) {
        if (startDestination == null) {
            if (authRepository != null) {
                authRepository.observeLoggedInUserId().collect { userId ->
                    value = if (userId != null) {
                        SessionGateState.Authenticated(userId)
                    } else {
                        SessionGateState.Unauthenticated
                    }
                }
            } else {
                value = SessionGateState.Unauthenticated
            }
        }
    }

    var effectiveStartDestination by rememberSaveable(startDestination) {
        mutableStateOf(startDestination)
    }

    LaunchedEffect(sessionGateState, startDestination) {
        if (startDestination != null) {
            effectiveStartDestination = startDestination
        } else if (effectiveStartDestination == null && sessionGateState !is SessionGateState.Loading) {
            effectiveStartDestination = if (sessionGateState is SessionGateState.Authenticated) {
                CloBleDestinations.DISCOVER
            } else {
                CloBleDestinations.LOGIN
            }
        }
    }

    LaunchedEffect(pendingRoute, sessionGateState) {
        if (pendingRoute != null && sessionGateState is SessionGateState.Authenticated) {
            navController.navigate(pendingRoute) {
                launchSingleTop = true
            }
            onRouteHandled()
        }
    }

    if (effectiveStartDestination == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
                .testTag("session_gate_loading"),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = CloBleColors.Primary)
        }
    } else {
        NavHost(
            navController = navController,
            startDestination = effectiveStartDestination!!,
            modifier = modifier.semantics {
                testTagsAsResourceId = true
            }
        ) {

            composable(route = CloBleDestinations.PHASE_1_SHOWCASE) {
                DesignSystemShowcaseScreen()
            }

            composable(route = CloBleDestinations.LOGIN) {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate(CloBleDestinations.REGISTER)
                    },
                    onLoginSuccess = {
                        navController.navigate(CloBleDestinations.DISCOVER) {
                            popUpTo(CloBleDestinations.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(route = CloBleDestinations.REGISTER) {
                RegisterScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onRegisterSuccess = {
                        navController.navigate(CloBleDestinations.preferencesRoute(isOnboarding = true)) {
                            popUpTo(CloBleDestinations.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = CloBleDestinations.PREFERENCES,
                arguments = listOf(
                    navArgument("isOnboarding") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val isOnboarding = backStackEntry.arguments?.getBoolean("isOnboarding") ?: false
                PreferencesScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSaveSuccess = {
                        if (isOnboarding) {
                            navController.navigate(CloBleDestinations.DISCOVER) {
                                popUpTo(CloBleDestinations.PREFERENCES) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(route = CloBleDestinations.MY_PROFILE) {
                MyProfileScreen(
                    onNavigateToEditProfile = {
                        navController.navigate(CloBleDestinations.EDIT_PROFILE)
                    },
                    onNavigateToPreferences = {
                        navController.navigate(CloBleDestinations.preferencesRoute(isOnboarding = false))
                    },
                    onNavigateToWardrobe = {
                        navController.navigate(CloBleDestinations.WARDROBE) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNotifications = {
                        navController.navigate(CloBleDestinations.NOTIFICATIONS)
                    },
                    onNavigateToDiscover = {
                        navController.navigate(CloBleDestinations.DISCOVER) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToChatList = {
                        navController.navigate(CloBleDestinations.CHAT_LIST) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToHistory = {
                        navController.navigate(CloBleDestinations.ACTIVITY_HISTORY)
                    },
                    onLogoutSuccess = {
                        navController.navigate(CloBleDestinations.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                )
            }

            composable(route = CloBleDestinations.EDIT_PROFILE) {
                EditProfileScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }

        composable(route = CloBleDestinations.WARDROBE) {
            WardrobeScreen(
                onNavigateToAddClothing = {
                    navController.navigate(CloBleDestinations.ADD_CLOTHING)
                },
                onNavigateToEditClothing = { itemId ->
                    navController.navigate(CloBleDestinations.editClothingRoute(itemId))
                },
                onNavigateToNotifications = {
                    navController.navigate(CloBleDestinations.NOTIFICATIONS)
                },
                onNavigateToDiscover = {
                    navController.navigate(CloBleDestinations.DISCOVER) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToChatList = {
                    navController.navigate(CloBleDestinations.CHAT_LIST) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(CloBleDestinations.MY_PROFILE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(route = CloBleDestinations.ADD_CLOTHING) {
            AddEditClothingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = CloBleDestinations.EDIT_CLOTHING,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.LongType
                }
            )
        ) {
            AddEditClothingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSaveSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable(route = CloBleDestinations.DISCOVER) {
            DiscoverScreen(
                onNavigateToFilter = {
                    navController.navigate(CloBleDestinations.FILTER)
                },
                onNavigateToNotifications = {
                    navController.navigate(CloBleDestinations.NOTIFICATIONS)
                },
                onNavigateToItemDetail = { itemId ->
                    navController.navigate(CloBleDestinations.itemDetailRoute(itemId))
                },
                onNavigateToWardrobe = {
                    navController.navigate(CloBleDestinations.WARDROBE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToChatList = {
                    navController.navigate(CloBleDestinations.CHAT_LIST) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(CloBleDestinations.MY_PROFILE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(CloBleDestinations.ACTIVITY_HISTORY)
                }
            )
        }

        composable(route = CloBleDestinations.ACTIVITY_HISTORY) {
            ActivityHistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToItemDetail = { itemId ->
                    navController.navigate(CloBleDestinations.itemDetailRoute(itemId))
                },
                onNavigateToMatchDetail = { matchId ->
                    navController.navigate(CloBleDestinations.matchedRoute(matchId))
                }
            )
        }

        composable(route = CloBleDestinations.FILTER) {
            FilterScreen(
                onNavigateBack = { navController.popBackStack() },
                onApplyFilter = { }
            )
        }

        composable(
            route = CloBleDestinations.ITEM_DETAIL,
            arguments = listOf(
                navArgument("itemId") { type = NavType.LongType },
                navArgument("replyToInterestId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            ItemDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToOtherUserProfile = { ownerId ->
                    navController.navigate(CloBleDestinations.otherUserProfileRoute(ownerId))
                },
                onNavigateToWardrobe = {
                    navController.navigate(CloBleDestinations.WARDROBE)
                }
            )
        }

        composable(route = CloBleDestinations.NOTIFICATIONS) {
            NotificationsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToReplyInterest = { senderUserId, interestId ->
                    navController.navigate(CloBleDestinations.otherUserProfileRoute(senderUserId, interestId))
                },
                onNavigateToConversation = { matchId ->
                    navController.navigate(CloBleDestinations.conversationRoute(matchId))
                },
                onNavigateToRateUser = { matchId, otherUserId ->
                    navController.navigate(CloBleDestinations.rateUserRoute(matchId))
                }
            )
        }

        composable(
            route = CloBleDestinations.OTHER_USER_PROFILE,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType },
                navArgument("replyToInterestId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) {
            OtherUserProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMatched = { matchId ->
                    navController.navigate(CloBleDestinations.matchedRoute(matchId)) {
                        popUpTo(CloBleDestinations.NOTIFICATIONS) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = CloBleDestinations.MATCHED,
            arguments = listOf(
                navArgument("matchId") { type = NavType.LongType }
            )
        ) {
            MatchedScreen(
                onNavigateToConversation = { matchId ->
                    navController.navigate(CloBleDestinations.conversationRoute(matchId))
                },
                onNavigateToDiscover = {
                    navController.navigate(CloBleDestinations.DISCOVER) {
                        popUpTo(CloBleDestinations.DISCOVER) { inclusive = true }
                    }
                }
            )
        }

        composable(route = CloBleDestinations.CHAT_LIST) {
            ChatListScreen(
                onNavigateToConversation = { matchId ->
                    navController.navigate(CloBleDestinations.conversationRoute(matchId))
                },
                onNavigateToNotifications = {
                    navController.navigate(CloBleDestinations.NOTIFICATIONS)
                },
                onNavigateToDiscover = {
                    navController.navigate(CloBleDestinations.DISCOVER) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToWardrobe = {
                    navController.navigate(CloBleDestinations.WARDROBE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(CloBleDestinations.MY_PROFILE) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(
            route = CloBleDestinations.CONVERSATION,
            arguments = listOf(
                navArgument("matchId") { type = NavType.LongType }
            )
        ) {
            ConversationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOtherUserProfile = { partnerId ->
                    navController.navigate(CloBleDestinations.otherUserProfileRoute(partnerId))
                },
                onNavigateToRateUser = { matchId ->
                    navController.navigate(CloBleDestinations.rateUserRoute(matchId))
                }
            )
        }

        composable(
            route = CloBleDestinations.RATE_USER,
            arguments = listOf(
                navArgument("matchId") { type = NavType.LongType }
            )
        ) {
            RateUserScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOtherUserProfile = { partnerId ->
                    navController.navigate(CloBleDestinations.otherUserProfileRoute(partnerId))
                }
            )
        }
    }
}
}
