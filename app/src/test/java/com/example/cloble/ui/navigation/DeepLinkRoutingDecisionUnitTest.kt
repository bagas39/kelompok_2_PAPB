package com.example.cloble.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkRoutingDecisionUnitTest {

    @Test
    fun decidePendingDeepLinkAction_waitsWhenRouteNullOrSessionNotAuthenticated() {
        assertEquals(
            PendingDeepLinkDecision.WAIT,
            decidePendingDeepLinkAction(
                pendingRoute = null,
                pendingTargetUserId = 1L,
                sessionGateState = SessionGateState.Authenticated(1L),
                isOnboardingActive = false
            )
        )
        assertEquals(
            PendingDeepLinkDecision.WAIT,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.NOTIFICATIONS,
                pendingTargetUserId = 1L,
                sessionGateState = SessionGateState.Loading,
                isOnboardingActive = false
            )
        )
        assertEquals(
            PendingDeepLinkDecision.WAIT,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.NOTIFICATIONS,
                pendingTargetUserId = 1L,
                sessionGateState = SessionGateState.Unauthenticated,
                isOnboardingActive = false
            )
        )
    }

    @Test
    fun decidePendingDeepLinkAction_navigatesWhenUserMatchesOrTargetUnset() {
        assertEquals(
            PendingDeepLinkDecision.NAVIGATE,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.conversationRoute(30L),
                pendingTargetUserId = 7L,
                sessionGateState = SessionGateState.Authenticated(7L),
                isOnboardingActive = false
            )
        )
        assertEquals(
            PendingDeepLinkDecision.NAVIGATE,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.NOTIFICATIONS,
                pendingTargetUserId = null,
                sessionGateState = SessionGateState.Authenticated(7L),
                isOnboardingActive = false
            )
        )
    }

    @Test
    fun decidePendingDeepLinkAction_discardsWhenUserMismatchedOrOnboardingActive() {
        assertEquals(
            PendingDeepLinkDecision.DISCARD,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.conversationRoute(30L),
                pendingTargetUserId = 7L,
                sessionGateState = SessionGateState.Authenticated(99L),
                isOnboardingActive = false
            )
        )
        assertEquals(
            PendingDeepLinkDecision.DISCARD,
            decidePendingDeepLinkAction(
                pendingRoute = CloBleDestinations.NOTIFICATIONS,
                pendingTargetUserId = 7L,
                sessionGateState = SessionGateState.Authenticated(7L),
                isOnboardingActive = true
            )
        )
    }
}
