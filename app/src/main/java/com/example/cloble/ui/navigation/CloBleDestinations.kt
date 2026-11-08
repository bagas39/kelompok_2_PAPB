package com.example.cloble.ui.navigation

object CloBleDestinations {
    const val PHASE_1_SHOWCASE = "phase1_design_showcase"

    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PREFERENCES = "preferences?isOnboarding={isOnboarding}"

    const val DISCOVER = "discover"
    const val FILTER = "filter"
    const val ITEM_DETAIL = "item_detail/{itemId}?replyToInterestId={replyToInterestId}"

    const val NOTIFICATIONS = "notifications"
    const val OTHER_USER_PROFILE = "other_profile/{userId}?replyToInterestId={replyToInterestId}"
    const val MATCHED = "matched/{matchId}"

    const val CHAT_LIST = "chat_list"
    const val CONVERSATION = "conversation/{matchId}"

    const val MY_PROFILE = "my_profile"
    const val ACTIVITY_HISTORY = "activity_history"
    const val WARDROBE = "wardrobe"
    const val ADD_CLOTHING = "add_clothing"
    const val EDIT_CLOTHING = "edit_clothing/{itemId}"
    const val RATE_USER = "rate_user/{matchId}"

    fun preferencesRoute(isOnboarding: Boolean = false): String =
        "preferences?isOnboarding=$isOnboarding"

    fun itemDetailRoute(itemId: Long, replyToInterestId: Long = -1L): String =
        "item_detail/$itemId?replyToInterestId=$replyToInterestId"

    fun otherUserProfileRoute(userId: Long, replyToInterestId: Long = -1L): String =
        "other_profile/$userId?replyToInterestId=$replyToInterestId"

    fun matchedRoute(matchId: Long): String =
        "matched/$matchId"

    fun conversationRoute(matchId: Long): String =
        "conversation/$matchId"

    fun editClothingRoute(itemId: Long): String =
        "edit_clothing/$itemId"

    fun rateUserRoute(matchId: Long): String =
        "rate_user/$matchId"
}
