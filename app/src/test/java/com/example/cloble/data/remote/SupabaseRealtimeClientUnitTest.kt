package com.example.cloble.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        assertTrue("Leaving ConversationScreen should not leave shared user_threads topic", leavesAfterFirstUnsubscribe.isEmpty())

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

        val replyJson = JSONObject()
            .put("topic", recipientTopic)
            .put("event", "phx_reply")
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
}
