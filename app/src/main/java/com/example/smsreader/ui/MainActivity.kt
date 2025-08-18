package com.importpark.smsreader.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Telephony
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.importpark.smsreader.R
import com.importpark.smsreader.service.SmsForegroundService
import com.importpark.smsreader.util.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var sendersInput: EditText
    private lateinit var saveSenders: Button
    private lateinit var syncButton: Button
    private lateinit var smsListView: ListView

    private val smsList = ArrayList<HashMap<String, String>>()

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val receive = granted[Manifest.permission.RECEIVE_SMS] == true
        val read = granted[Manifest.permission.READ_SMS] == true
        val postNotif = if (Build.VERSION.SDK_INT >= 33) {
            granted[Manifest.permission.POST_NOTIFICATIONS] == true
        } else true
        if (receive && read && postNotif) {
            startForegroundSync()
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sendersInput = findViewById(R.id.sendersInput)
        saveSenders = findViewById(R.id.saveSenders)
        syncButton = findViewById(R.id.syncButton)
        smsListView = findViewById(R.id.smsListView)

        val prefs = getSharedPreferences("SMS_APP", Context.MODE_PRIVATE)
        sendersInput.setText(prefs.getString("senders", ""))

        saveSenders.setOnClickListener {
            prefs.edit().putString("senders", sendersInput.text.toString()).apply()
            Toast.makeText(this, "Senders saved", Toast.LENGTH_SHORT).show()
        }

        syncButton.setOnClickListener {
            manualSyncAll()
        }

        // ask permissions then start foreground service
        val needed = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val allGranted = needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (!allGranted) requestPerms.launch(needed.toTypedArray()) else startForegroundSync()
    }

    private fun startForegroundSync() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, SmsForegroundService::class.java)
        )
    }

    private fun manualSyncAll() {
        val prefs = getSharedPreferences("SMS_APP", Context.MODE_PRIVATE)
        val senderList = prefs.getString("senders", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (senderList.isEmpty()) {
            Toast.makeText(this, "Add senders first", Toast.LENGTH_SHORT).show()
            return
        }
        smsList.clear()

        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null, Telephony.Sms.DEFAULT_SORT_ORDER
        )

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val df = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        cursor?.use {
            while (it.moveToNext()) {
                val smsId = it.getLong(0)
                val address = it.getString(1) ?: ""
                val body = it.getString(2) ?: ""
                val date = it.getLong(3)

                val matched = senderList.firstOrNull { s -> address.contains(s, ignoreCase = true) }
                if (matched != null) {
                    // send to API (include sms_unique_id and selected_sender)
                    CoroutineScope(Dispatchers.IO).launch {
                        ApiClient.sendSms(
                            uniqueId = deviceId,
                            smsUniqueId = smsId.toString(),
                            selectedSender = matched,
                            timestamp = date,
                            content = body
                        )
                    }

                    val map = hashMapOf(
                        "sender" to address,
                        "body" to body,
                        "date" to df.format(Date(date))
                    )
                    smsList.add(map)
                }
            }
        }

        val adapter = SimpleAdapter(
            this,
            smsList,
            android.R.layout.simple_list_item_2,
            arrayOf("sender", "body"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        smsListView.adapter = adapter

        Toast.makeText(this, "Sync triggered for ${smsList.size} SMS", Toast.LENGTH_SHORT).show()
    }
}