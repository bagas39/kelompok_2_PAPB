package com.example.cloble.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseRealtimeClientUnitTest {

    private fun parseFrames(frames: List<String>): List<JSONObject> = frames.map { JSONObject(it) }

    @Test
    fun subscribeAndUnsubscribe_usesReferenceCountingBeforeLeavingTopic() {
        val sentFrames = mutableListOf<String>()
        val client = SupabaseRealtimeClient(
            autoConnect = false,
            frameSenderOverride = { frame ->
                sentFrames.add(frame)
                true
            }
        )
        client.setConnectedForTest(true)

        val topic = "realtime:user_threads_1"

        client.subscribeTopic(topic)
        client.subscribeTopic(topic)

        assertEquals(2, client.activeTopicCount(topic))
        val joinsAfterTwoSubscribes = parseFrames(sentFrames).filter {
            it.optString("topic") == topic && it.optString("event") == "phx_join"
        }
        assertEquals(1, joinsAfterTwoSubscribes.size)

        client.unsubscribeTopic(topic)
        assertEquals(1, client.activeTopicCount(topic))
        val leavesAfterFirstUnsubscribe = parseFrames(sentFrames).filter {
            it.optString("topic") == topic && it.optString("event") == "phx_leave"
        }
        assertTrue(leavesAfterFirstUnsubscribe.isEmpty())

        client.unsubscribeTopic(topic)
        assertEquals(0, client.activeTopicCount(topic))
        val leavesAfterSecondUnsubscribe = parseFrames(sentFrames).filter {
            it.optString("topic") == topic && it.optString("event") == "phx_leave"
        }
        assertEquals(1, leavesAfterSecondUnsubscribe.size)

        client.unsubscribeTopic(topic)
        assertEquals(0, client.activeTopicCount(topic))
        val leavesAfterExtraUnsubscribe = parseFrames(sentFrames).filter {
            it.optString("topic") == topic && it.optString("event") == "phx_leave"
        }
        assertEquals(1, leavesAfterExtraUnsubscribe.size)
    }

    @Test
    fun broadcastToUnsubscribedTopic_joinsEphemerallyAndFlushesOnPhxReply() {
        val sentFrames = mutableListOf<String>()
        val client = SupabaseRealtimeClient(
            autoConnect = false,
            frameSenderOverride = { frame ->
                sentFrames.add(frame)
                true
            }
        )
        client.setConnectedForTest(true)

        val recipientTopic = "realtime:user_threads_9"
        val payload = JSONObject().put("matchId", 42L)

        client.broadcast(recipientTopic, "thread_updated", payload)

        val framesBeforeReply = parseFrames(sentFrames)
        assertEquals(1, framesBeforeReply.size)
        assertEquals("phx_join", framesBeforeReply[0].optString("event"))
        assertEquals(recipientTopic, framesBeforeReply[0].optString("topic"))
        val joinRef = framesBeforeReply[0].optString("ref")

        val replyJson = JSONObject()
            .put("topic", recipientTopic)
            .put("event", "phx_reply")
            .put("ref", joinRef)
            .put("payload", JSONObject().put("status", "ok"))
            .toString()
        client.handleIncomingMessage(replyJson)

        val framesAfterReply = parseFrames(sentFrames)
        val broadcastFrames = framesAfterReply.filter {
            it.optString("topic") == recipientTopic && it.optString("event") == "broadcast"
        }
        assertEquals(1, broadcastFrames.size)
        assertEquals(
            42L,
            broadcastFrames[0]
                .optJSONObject("payload")
                ?.optJSONObject("payload")
                ?.optLong("matchId")
        )
    }

    @Test
    fun phxReplyWithErrorOrMismatchedRef_doesNotFlushEphemeralBroadcasts() {
        val sentFrames = mutableListOf<String>()
        val client = SupabaseRealtimeClient(
            autoConnect = false,
            frameSenderOverride = { frame ->
                sentFrames.add(frame)
                true
            }
        )
        client.setConnectedForTest(true)

        val topicA = "realtime:user_threads_11"
        client.broadcast(topicA, "thread_sync", JSONObject().put("seq", 1))
        val joinRefA = parseFrames(sentFrames).first().optString("ref")

        val mismatchedReply = JSONObject()
            .put("topic", topicA)
            .put("event", "phx_reply")
            .put("ref", "wrong-$joinRefA")
            .put("payload", JSONObject().put("status", "ok"))
            .toString()
        client.handleIncomingMessage(mismatchedReply)
        assertTrue(parseFrames(sentFrames).none { it.optString("event") == "broadcast" })
        assertEquals(1, client.pendingEphemeralCountForTest(topicA))

        val errorReply = JSONObject()
            .put("topic", topicA)
            .put("event", "phx_reply")
            .put("ref", joinRefA)
            .put("payload", JSONObject().put("status", "error"))
            .toString()
        client.handleIncomingMessage(errorReply)

        assertTrue(parseFrames(sentFrames).none { it.optString("event") == "broadcast" })
        assertEquals(0, client.pendingEphemeralCountForTest(topicA))
        assertTrue(
            parseFrames(sentFrames).any {
                it.optString("topic") == topicA && it.optString("event") == "phx_leave"
            }
        )
    }

    @Test
    fun offlineBroadcastQueues_areBoundedAndClearedOnDisconnect() {
        val client = SupabaseRealtimeClient(
            autoConnect = false,
            frameSenderOverride = { true }
        )
        client.setConnectedForTest(false)

        val subscribedTopic = "realtime:match_77"
        client.subscribeTopic(subscribedTopic)
        repeat(80) { idx ->
            client.broadcast(subscribedTopic, "chat_sync", JSONObject().put("seq", idx))
        }
        assertEquals(64, client.pendingBroadcastCountForTest())

        val ephemeralTopic = "realtime:user_threads_88"
        repeat(45) { idx ->
            client.broadcast(ephemeralTopic, "thread_sync", JSONObject().put("seq", idx))
        }
        assertEquals(32, client.pendingEphemeralCountForTest(ephemeralTopic))

        client.disconnect()
        assertEquals(0, client.pendingBroadcastCountForTest())
        assertEquals(0, client.pendingEphemeralCountForTest(ephemeralTopic))
        assertFalse(client.hasPendingReconnectForTest())
    }
}
