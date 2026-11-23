package com.example.cloble.ui.screens.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityHistoryPhotoMaskUnitTest {

    @Test
    fun shouldShowHistoryItemPhoto_returnsFalseWhenLockedEvenWithPhotoUrl() {
        assertFalse(shouldShowHistoryItemPhoto(isLocked = true, photoUri = "https://example.com/item.jpg"))
    }

    @Test
    fun shouldShowHistoryItemPhoto_returnsTrueWhenUnlockedWithNonBlankUrl() {
        assertTrue(shouldShowHistoryItemPhoto(isLocked = false, photoUri = "https://example.com/item.jpg"))
    }

    @Test
    fun shouldShowHistoryItemPhoto_returnsFalseWhenUnlockedWithNullOrBlankUrl() {
        assertFalse(shouldShowHistoryItemPhoto(isLocked = false, photoUri = null))
        assertFalse(shouldShowHistoryItemPhoto(isLocked = false, photoUri = ""))
        assertFalse(shouldShowHistoryItemPhoto(isLocked = false, photoUri = "   "))
    }
}
