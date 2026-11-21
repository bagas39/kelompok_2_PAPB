package com.example.cloble.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "${config.restUrl}/$path"
        val builder = Request.Builder().url(url).get()
        applyHeaders(builder)
        executeRequest(builder.build())
    }

    suspend fun getJsonArray(path: String): JSONArray {
        val body = get(path)
        return if (body.isBlank()) JSONArray() else JSONArray(body)
    }

    suspend fun getJsonObjectOrNull(path: String): JSONObject? {
        val arr = getJsonArray(path)
        return if (arr.length() > 0) arr.getJSONObject(0) else null
    }

    suspend fun post(
        path: String,
        jsonBody: String,
        returnRepresentation: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "${config.restUrl}/$path"
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(jsonMediaType))

        applyHeaders(builder)
        if (returnRepresentation) {
            builder.addHeader("Prefer", "return=representation")
        }

        executeRequest(builder.build())
    }

    suspend fun patch(
        path: String,
        jsonBody: String
    ): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "${config.restUrl}/$path"
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .patch(jsonBody.toRequestBody(jsonMediaType))

        applyHeaders(builder)
        executeRequest(builder.build())
    }

    suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        val url = if (path.startsWith("http")) path else "${config.restUrl}/$path"
        val builder = Request.Builder()
            .url(url)
            .delete()

        applyHeaders(builder)
        executeRequest(builder.build())
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

    private fun executeRequest(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Supabase HTTP ${response.code}: $bodyString")
            }
            return bodyString
        }
    }
}
