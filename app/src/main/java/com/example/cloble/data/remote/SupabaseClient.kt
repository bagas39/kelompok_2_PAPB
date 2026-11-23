package com.example.cloble.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SupabaseClient(
    val config: SupabaseConfig = SupabaseConfig
) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var sessionToken: String? = null

    fun setSessionToken(token: String?) {
        sessionToken = token
    }

    fun getSessionToken(): String? = sessionToken

    private fun applyHeaders(builder: Request.Builder): Request.Builder {
        builder.addHeader("apikey", config.apiKey)
        builder.addHeader("Authorization", "Bearer ${config.apiKey}")
        val token = sessionToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("x-session-token", token)
        }
        return builder
    }

    suspend fun rpc(
        functionName: String,
        params: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "${config.restUrl}/rpc/$functionName"
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(params.toString().toRequestBody(jsonMediaType))

        applyHeaders(builder)
        val responseBody = executeRequest(builder.build())
        if (responseBody.trim().startsWith("{")) {
            JSONObject(responseBody)
        } else {
            JSONObject().put("value", responseBody)
        }
    }

    suspend fun invokeEdgeFunction(
        functionName: String,
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "${config.baseUrl}/functions/v1/$functionName"
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMediaType))

        applyHeaders(builder)
        val responseBody = executeRequest(builder.build())
        if (responseBody.trim().startsWith("{")) {
            JSONObject(responseBody)
        } else {
            JSONObject().put("value", responseBody)
        }
    }

    suspend fun uploadPhoto(
        bytes: ByteArray,
        filename: String,
        mimeType: String = "image/jpeg"
    ): String = withContext(Dispatchers.IO) {
        val uploadUrl = "${config.storageUrl}/object/${config.PHOTO_BUCKET}/$filename"
        val mediaType = mimeType.toMediaType()
        val builder = Request.Builder()
            .url(uploadUrl)
            .addHeader("Content-Type", mimeType)
            .post(bytes.toRequestBody(mediaType))

        applyHeaders(builder)
        executeRequest(builder.build())
        "${config.storageUrl}/object/public/${config.PHOTO_BUCKET}/$filename"
    }

    private suspend fun executeRequest(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val bodyString = try {
                            res.body?.string().orEmpty()
                        } catch (e: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                            return
                        }
                        if (!res.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IOException("Supabase HTTP ${res.code}: $bodyString")
                                )
                            }
                            return
                        }
                        if (continuation.isActive) {
                            continuation.resume(bodyString)
                        }
                    }
                }
            })
        }
}
