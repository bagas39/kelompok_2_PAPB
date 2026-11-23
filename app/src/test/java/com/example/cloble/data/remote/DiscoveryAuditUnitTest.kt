package com.example.cloble.data.remote

import com.example.cloble.di.resetDiscoverFilterOnAccountChange
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.ui.screens.discover.FilterViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryAuditUnitTest {
    @Test
    fun filter_preservesExistingColorsAndAppliesColorChanges() {
        val filters = MutableStateFlow(DiscoverFilter(selectedColors = setOf("Hitam")))
        val viewModel = FilterViewModel(filters)
        assertEquals(setOf("Hitam"), viewModel.uiState.value.selectedColors)
        viewModel.toggleColor("Biru")
        viewModel.toggleColor("Hitam")
        assertEquals(setOf("Biru"), viewModel.applyFilter()?.selectedColors)
        assertEquals(setOf("Biru"), filters.value.selectedColors)
        viewModel.reset()
        assertTrue(viewModel.uiState.value.selectedColors.isEmpty())
        assertEquals(DiscoverFilter(), filters.value)
    }

    @Test
    fun filter_resetsOnAccountChangeAndLogoutButKeepsSameAccountEdits() = runTest {
        val accounts = MutableSharedFlow<Long?>()
        val filters = MutableStateFlow(DiscoverFilter())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            resetDiscoverFilterOnAccountChange(accounts, filters)
        }
        accounts.emit(1L)
        filters.value = DiscoverFilter(selectedColors = setOf("Hitam"))
        accounts.emit(1L)
        assertEquals(setOf("Hitam"), filters.value.selectedColors)
        accounts.emit(2L)
        assertEquals(DiscoverFilter(), filters.value)
        filters.value = DiscoverFilter(selectedSizes = setOf("M"))
        accounts.emit(null)
        assertEquals(DiscoverFilter(), filters.value)
    }

    @Test
    fun realtime_preservesEventAndSkipsChatWithoutDroppingMatchOrInterestUpdates() = runTest {
        val client = SupabaseRealtimeClient(autoConnect = false)
        val topic = "realtime:user_threads_1"
        for (event in listOf("chat_sync", "thread_sync", "match_updated", "interest_received")) {
            val received = async(start = CoroutineStart.UNDISPATCHED) { client.incomingMessages.first() }
            client.handleIncomingMessage(JSONObject().put("topic", topic).put("event", "broadcast")
                .put("payload", JSONObject().put("event", event)
                    .put("payload", JSONObject().put("type", "sync"))).toString())
            val (incomingTopic, payload) = received.await()
            assertEquals(event, payload.optString("broadcastEvent"))
            assertEquals(event !in setOf("chat_sync", "thread_sync"),
                shouldRefreshDiscoveryForRealtime(topic, incomingTopic, payload))
        }
        assertFalse(shouldRefreshDiscoveryForRealtime(topic, "realtime:user_threads_2", JSONObject()))
        assertTrue(shouldRefreshDiscoveryForRealtime(topic, "realtime:match_3", JSONObject()))
    }

    @Test
    fun repository_doesNotConvertCancellationToSuccessOrFallbackValues() = runTest {
        val repository = SupabaseDiscoveryMatchRepository(SupabaseClient(), SupabaseDataInvalidator())
        var returned = false
        val job = launch {
            coroutineContext.cancel()
            repository.passCandidate(1L, 2L)
            returned = true
        }
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    @Test
    fun notification_neverUsesRecipientAsCounterpart() {
        val notification = JSONObject("""{"userId":1,"type":"MATCH_VERIFIED",
            "interests":{"senderUserId":1,"users":{"id":1,"name":"Self"}},
            "matches":{"user1Id":1,"user2Id":2,"user2":{"id":2,"name":"Partner"}}} """)
        val parsed = SupabaseMappers.parseNotification(notification)
        assertEquals(2L, parsed.counterpartUserId)
        assertEquals("Partner", parsed.counterpartName)
        notification.getJSONObject("interests").put("receiverUserId", 2L)
        assertEquals("Partner", SupabaseMappers.parseNotification(notification).counterpartName)
        notification.getJSONObject("interests").remove("receiverUserId")
        notification.remove("matches")
        assertNull(SupabaseMappers.parseNotification(notification).counterpartUserId)
        assertNull(SupabaseMappers.parseNotification(notification).counterpartName)
        notification.getJSONObject("interests").put("receiverUserId", 2L)
        assertEquals(2L, SupabaseMappers.parseNotification(notification).counterpartUserId)
    }

    @Test
    fun historyAndNotificationPhotos_fallBackToRemainingSlots() {
        val item = JSONObject("""{"mainPhotoUri":" ","detailPhotoUri":"detail.jpg","extraPhotoUri":"extra.jpg"}""")
        val history = JSONObject().put("clothing_items", item)
        assertEquals("detail.jpg", SupabaseMappers.parseOutgoingInterest(history).targetItemPhotoUri)
        assertEquals("detail.jpg", SupabaseMappers.parsePassedClothing(history).clothingPhotoUri)
        item.put("detailPhotoUri", JSONObject.NULL)
        assertEquals("extra.jpg", SupabaseMappers.parseOutgoingInterest(history).targetItemPhotoUri)
        val notification = JSONObject().put("type", "INCOMING_INTEREST")
            .put("interests", JSONObject().put("clothing_items", item))
        assertEquals("extra.jpg", SupabaseMappers.parseNotification(notification).counterpartPhotoUri)
    }

    @Test
    fun filter_toggleCustomRadiusPrefillsPresetAndRejectsZeroOrEmpty() {
        val presetVm = FilterViewModel(MutableStateFlow(DiscoverFilter()))
        presetVm.selectPresetRadius(25)
        presetVm.toggleCustomRadiusMode()
        assertTrue(presetVm.uiState.value.isCustomRadius)
        assertEquals("25", presetVm.uiState.value.customRadiusText)
        assertEquals(25, presetVm.uiState.value.selectedRadiusKm)

        val emptyCustomVm = FilterViewModel(MutableStateFlow(DiscoverFilter()))
        emptyCustomVm.toggleCustomRadiusMode()
        assertEquals("", emptyCustomVm.uiState.value.customRadiusText)
        assertNull(emptyCustomVm.applyFilter())
        assertEquals("Radius pencarian harus diisi lebih dari 0 km.", emptyCustomVm.uiState.value.errorMessage)

        emptyCustomVm.onCustomRadiusTextChanged("0")
        assertNull(emptyCustomVm.applyFilter())
        assertEquals("Radius pencarian harus diisi lebih dari 0 km.", emptyCustomVm.uiState.value.errorMessage)
    }
}
