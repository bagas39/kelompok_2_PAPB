package com.example.cloble.domain.result

import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.MatchStatus

sealed interface DbFailure {
    val message: String?

    data class DatabaseBusyOrLocked(override val message: String? = null) : DbFailure
    data class DuplicateOrConstraintViolation(override val message: String? = null) : DbFailure
    data class UnexpectedDatabaseError(override val message: String? = null) : DbFailure
}

sealed interface AuthResult {
    data class Success(val userId: Long) : AuthResult
    data object InvalidCredentials : AuthResult
    data object EmailAlreadyRegistered : AuthResult
    data class ValidationFailed(val message: String) : AuthResult
    data class DatabaseError(val failure: DbFailure) : AuthResult
}

sealed interface WardrobeMutationResult {
    data class Success(val itemId: Long) : WardrobeMutationResult
    data object ItemNotFound : WardrobeMutationResult
    data object LockedInActiveMatch : WardrobeMutationResult
    data object InvalidOwnership : WardrobeMutationResult
    data class ValidationFailed(val message: String) : WardrobeMutationResult
    data class DatabaseError(val failure: DbFailure) : WardrobeMutationResult
}

sealed interface SendInterestResult {
    data class Success(val interestId: Long) : SendInterestResult
    data object InvalidOrMissingSenderSession : SendInterestResult
    data class AlreadyInterested(val existingStatus: InterestStatus) : SendInterestResult
    data object RequiresBarterableItemInWardrobe : SendInterestResult
    data object TargetItemNotFound : SendInterestResult
    data object CannotInterestOwnItem : SendInterestResult
    data object TargetItemNotEligible : SendInterestResult
    data class DatabaseError(val failure: DbFailure) : SendInterestResult
}

sealed interface RespondInterestResult {
    data class Success(val matchId: Long) : RespondInterestResult
    data object InterestAlreadyProcessedOrInvalid : RespondInterestResult
    data class TargetItemNotFound(val interestInvalidated: Boolean = true) : RespondInterestResult
    data class TargetItemUnavailable(val interestInvalidated: Boolean = true) : RespondInterestResult
    data object ChosenItemNotFound : RespondInterestResult
    data object ChosenItemUnavailable : RespondInterestResult
    data object InvalidItemOwnership : RespondInterestResult
    data object BarterNotAllowed : RespondInterestResult
    data object ItemAlreadyInActiveMatch : RespondInterestResult
    data object PairAlreadyMatched : RespondInterestResult
    data class DatabaseError(val failure: DbFailure) : RespondInterestResult
}

sealed interface CancelMatchResult {
    data object Success : CancelMatchResult
    data object MatchNotFound : CancelMatchResult
    data object NotMatchParticipant : CancelMatchResult
    data class MatchNotActive(val currentStatus: MatchStatus) : CancelMatchResult
    data class DatabaseError(val failure: DbFailure) : CancelMatchResult
}

sealed interface CompleteMatchResult {
    data object Success : CompleteMatchResult
    data object MatchNotFound : CompleteMatchResult
    data object NotMatchParticipant : CompleteMatchResult
    data class MatchNotActive(val currentStatus: MatchStatus) : CompleteMatchResult
    data object ItemsNoLongerAvailable : CompleteMatchResult
    data class DatabaseError(val failure: DbFailure) : CompleteMatchResult
}

sealed interface SendMessageResult {
    data class Success(val messageId: Long) : SendMessageResult
    data object MatchNotFound : SendMessageResult
    data object NotMatchParticipant : SendMessageResult
    data object ChatReadOnly : SendMessageResult
    data object EmptyMessage : SendMessageResult
    data class DatabaseError(val failure: DbFailure) : SendMessageResult
}

sealed interface ReviewSubmitResult {
    data class Success(val reviewId: Long) : ReviewSubmitResult
    data object MatchNotFound : ReviewSubmitResult
    data object NotMatchParticipant : ReviewSubmitResult
    data object MatchNotCompleted : ReviewSubmitResult
    data object AlreadyReviewed : ReviewSubmitResult
    data object InvalidRating : ReviewSubmitResult
    data class DatabaseError(val failure: DbFailure) : ReviewSubmitResult
}
