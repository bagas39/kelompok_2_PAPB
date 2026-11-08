package com.example.cloble.data.local.dao

import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.MatchStatus

sealed interface SendInterestDaoResult {
    data class Success(val interestId: Long) : SendInterestDaoResult
    data object SenderNotFound : SendInterestDaoResult
    data class AlreadyInterested(val existingStatus: InterestStatus) : SendInterestDaoResult
    data object RequiresBarterableItemInWardrobe : SendInterestDaoResult
    data object TargetItemNotFound : SendInterestDaoResult
    data object CannotInterestOwnItem : SendInterestDaoResult
    data object TargetItemNotEligible : SendInterestDaoResult
}

sealed interface RespondInterestDaoResult {
    data class Success(val matchId: Long) : RespondInterestDaoResult
    data object InterestAlreadyProcessedOrInvalid : RespondInterestDaoResult
    data class TargetItemNotFound(val interestInvalidated: Boolean = true) : RespondInterestDaoResult
    data class TargetItemUnavailable(val interestInvalidated: Boolean = true) : RespondInterestDaoResult
    data object ChosenItemNotFound : RespondInterestDaoResult
    data object ChosenItemUnavailable : RespondInterestDaoResult
    data object InvalidItemOwnership : RespondInterestDaoResult
    data object BarterNotAllowed : RespondInterestDaoResult
    data object ItemAlreadyInActiveMatch : RespondInterestDaoResult
    data object PairAlreadyMatched : RespondInterestDaoResult
}

sealed interface CancelMatchDaoResult {
    data object Success : CancelMatchDaoResult
    data object MatchNotFound : CancelMatchDaoResult
    data object NotMatchParticipant : CancelMatchDaoResult
    data class MatchNotActive(val currentStatus: MatchStatus) : CancelMatchDaoResult
}

sealed interface CompleteMatchDaoResult {
    data object Success : CompleteMatchDaoResult
    data object MatchNotFound : CompleteMatchDaoResult
    data object NotMatchParticipant : CompleteMatchDaoResult
    data class MatchNotActive(val currentStatus: MatchStatus) : CompleteMatchDaoResult
    data object ItemsNoLongerAvailable : CompleteMatchDaoResult
}

sealed interface SendMessageDaoResult {
    data class Success(val messageId: Long) : SendMessageDaoResult
    data object MatchNotFound : SendMessageDaoResult
    data object NotMatchParticipant : SendMessageDaoResult
    data object ChatReadOnly : SendMessageDaoResult
    data object EmptyMessage : SendMessageDaoResult
}

sealed interface ReviewSubmitDaoResult {
    data class Success(val reviewId: Long) : ReviewSubmitDaoResult
    data object MatchNotFound : ReviewSubmitDaoResult
    data object NotMatchParticipant : ReviewSubmitDaoResult
    data object MatchNotCompleted : ReviewSubmitDaoResult
    data object AlreadyReviewed : ReviewSubmitDaoResult
    data object InvalidRating : ReviewSubmitDaoResult
}

sealed interface WardrobeMutationDaoResult {
    data class Success(val itemId: Long) : WardrobeMutationDaoResult
    data object ItemNotFound : WardrobeMutationDaoResult
    data object LockedInActiveMatch : WardrobeMutationDaoResult
    data object InvalidOwnership : WardrobeMutationDaoResult
}
