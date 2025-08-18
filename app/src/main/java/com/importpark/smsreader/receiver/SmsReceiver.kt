package com.importpark.smsreader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import com.importpark.smsreader.util.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val prefs = context.getSharedPreferences("SMS_APP", Context.MODE_PRIVATE)
            val senderList = prefs.getString("senders", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            if (senderList.isEmpty()) return

            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                val address = sms.displayOriginatingAddress ?: ""
                val body = sms.displayMessageBody ?: ""
                val timestamp = sms.timestampMillis

                val matched = senderList.firstOrNull { s -> address.contains(s, ignoreCase = true) }
                if (matched != null) {
                    // sms_unique_id is not available from the PDU. Send null.
                    CoroutineScope(Dispatchers.IO).launch {
                        ApiClient.sendSms(
                            uniqueId = deviceId,
                            smsUniqueId = null,
                            selectedSender = matched,
                            timestamp = timestamp,
                            content = body
                        )
                    }
                }
            }
        }
    }
}