package com.importpark.smsreader.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ApiClient {
    private val client by lazy { OkHttpClient() }

    // Sends POST to https://apitest.com/v1/sync-sms with fields:
    // unique_id, sms_unique_id (nullable), selected_sender, sms_timestamp, content
    suspend fun sendSms(
        uniqueId: String,
        smsUniqueId: String?,
        selectedSender: String,
        timestamp: Long,
        content: String
    ) {
        val json = JSONObject().apply {
            put("unique_id", uniqueId)
            if (smsUniqueId != null) put("sms_unique_id", smsUniqueId)
            put("selected_sender", selectedSender)
            put("sms_timestamp", timestamp)
            put("content", content)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://www.import-park.com/sync-sms")
            .post(body)
            .build()
        client.newCall(req).execute().use { /* optionally read response */ }
    }
}