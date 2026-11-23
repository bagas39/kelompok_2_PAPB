package com.example.cloble.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SupabaseAuthSessionValidationUnitTest {

    @Test
    fun validateSession_returnsTrue_whenServerConfirmsSameUser() = runBlocking {
        var appliedToken: String? = null
        var cleared = false

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-valid" },
            setClientToken = { appliedToken = it },
            clearLocalSession = { cleared = true },
            callRpc = { fn, _ ->
                assertEquals("rpc_validate_session", fn)
                JSONObject().put("code", "VALID").put("userId", 7L)
            }
        )

        assertTrue(valid)
        assertEquals("tok-valid", appliedToken)
        assertFalse(cleared)
    }

    @Test
    fun validateSession_clearsSessionAndReturnsFalse_whenServerReturnsInvalidSession() = runBlocking {
        var cleared = false

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-revoked" },
            setClientToken = {},
            clearLocalSession = { cleared = true },
            callRpc = { _, _ -> JSONObject().put("code", "INVALID_SESSION") }
        )

        assertFalse(valid)
        assertTrue(cleared)
    }

    @Test
    fun validateSession_clearsSessionAndReturnsFalse_whenServerReturnsMismatchedUserId() = runBlocking {
        var cleared = false

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-other" },
            setClientToken = {},
            clearLocalSession = { cleared = true },
            callRpc = { _, _ -> JSONObject().put("code", "VALID").put("userId", 99L) }
        )

        assertFalse(valid)
        assertTrue(cleared)
    }

    @Test
    fun validateSession_clearsSessionAndReturnsFalse_whenLocalTokenMissing() = runBlocking {
        var cleared = false
        var rpcCalled = false
        var appliedToken: String? = "initial"

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "" },
            setClientToken = { appliedToken = it },
            clearLocalSession = {
                cleared = true
                appliedToken = null
            },
            callRpc = { _, _ ->
                rpcCalled = true
                JSONObject()
            }
        )

        assertFalse(valid)
        assertTrue(cleared)
        assertFalse(rpcCalled)
        assertNull(appliedToken)
    }

    @Test
    fun validateSession_fallsBackToChallengeRpcOn404_andClearsInvalidSession() = runBlocking {
        var cleared = false
        val calledFunctions = mutableListOf<String>()

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-revoked" },
            setClientToken = {},
            clearLocalSession = { cleared = true },
            callRpc = { fn, _ ->
                calledFunctions.add(fn)
                if (fn == "rpc_validate_session") {
                    throw IOException("Supabase HTTP 404: {\"code\":\"PGRST202\"}")
                }
                JSONObject().put("code", "UNAUTHORIZED")
            }
        )

        assertFalse(valid)
        assertTrue(cleared)
        assertEquals(listOf("rpc_validate_session", "rpc_get_current_password_challenge"), calledFunctions)
    }

    @Test
    fun validateSession_fallsBackToChallengeRpcOn404_andPreservesValidSession() = runBlocking {
        var cleared = false

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-ok" },
            setClientToken = {},
            clearLocalSession = { cleared = true },
            callRpc = { fn, _ ->
                if (fn == "rpc_validate_session") {
                    throw IOException("Supabase HTTP 404: {\"code\":\"PGRST202\"}")
                }
                JSONObject().put("code", "SUCCESS").put("userId", 7L)
            }
        )

        assertTrue(valid)
        assertFalse(cleared)
    }

    @Test
    fun validateSession_preservesLocalSessionOnOfflineNetworkError() = runBlocking {
        var cleared = false

        val valid = validateSessionWithDeps(
            getLocalUserId = { 7L },
            getLocalToken = { "tok-offline" },
            setClientToken = {},
            clearLocalSession = { cleared = true },
            callRpc = { _, _ -> throw IOException("Unable to resolve host") }
        )

        assertTrue(valid)
        assertFalse(cleared)
    }

    @Test
    fun validateSession_rethrowsCancellationException() = runBlocking {
        var rethrown = false
        var cleared = false

        try {
            validateSessionWithDeps(
                getLocalUserId = { 7L },
                getLocalToken = { "tok-valid" },
                setClientToken = {},
                clearLocalSession = { cleared = true },
                callRpc = { _, _ -> throw CancellationException("cancelled") }
            )
        } catch (_: CancellationException) {
            rethrown = true
        }

        assertTrue(rethrown)
        assertFalse(cleared)
    }

    @Test
    fun logoutWithDeps_loadsStoredTokenIntoClientBeforeServerRpcs() = runBlocking {
        var clientToken: String? = null
        var cleared = false
        val recordedCalls = mutableListOf<Pair<String, String?>>()

        logoutWithDeps(
            getClientToken = { clientToken },
            getStoredToken = { "stored-session-token" },
            setClientToken = { clientToken = it },
            getFcmToken = { "fcm-token-123" },
            callRpc = { fn, _ ->
                recordedCalls.add(fn to clientToken)
                JSONObject().put("code", "SUCCESS")
            },
            clearLocalSession = {
                cleared = true
                clientToken = null
            }
        )

        assertEquals(
            listOf(
                "rpc_unregister_fcm_token" to "stored-session-token",
                "rpc_logout_session" to "stored-session-token"
            ),
            recordedCalls
        )
        assertTrue(cleared)
        assertNull(clientToken)
    }
}
