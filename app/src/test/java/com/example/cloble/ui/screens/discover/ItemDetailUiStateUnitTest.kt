package com.example.cloble.ui.screens.discover

import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.MatchStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemDetailUiStateUnitTest {
    @Test
    fun invalidatedOfferCanBeRetriedOnlyWhenItemIsAvailableAndUnreserved() {
        val available = ItemDetailUiState(
            existingInterestId = 10L,
            existingInterestStatus = InterestStatus.INVALIDATED,
            isLoading = false
        )
        assertTrue(available.canReofferAfterInvalidated)
        assertFalse(available.copy(isItemUnavailable = true).canReofferAfterInvalidated)
        assertFalse(available.copy(isLockedByOtherMatch = true).canReofferAfterInvalidated)
        assertFalse(available.copy(relatedMatchId = 30L, relatedMatchStatus = MatchStatus.ACTIVE).canReofferAfterInvalidated)
    }

    @Test
    fun successfulRetryStopsReofferAndWaitsForOwnersResponse() {
        val state = ItemDetailUiState(existingInterestStatus = InterestStatus.INVALIDATED)
        assertTrue(state.canReofferAfterInvalidated)
        val sent = state.copy(
            existingInterestId = 11L,
            existingInterestStatus = InterestStatus.PENDING,
            interestSentSuccess = true
        )
        assertFalse(sent.canReofferAfterInvalidated)
    }

    @Test
    fun invalidationRetryDoesNotUnlockPendingDeclinedOrMatchedOffers() {
        for (status in InterestStatus.entries.filter { it != InterestStatus.INVALIDATED }) {
            assertFalse(ItemDetailUiState(existingInterestStatus = status).canReofferAfterInvalidated)
        }
        assertFalse(ItemDetailUiState().canReofferAfterInvalidated)
    }
}
