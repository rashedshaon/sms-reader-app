package com.importpark.smsreader

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {

    private val SMS_PERMISSION_CODE = 101
    private lateinit var senderDropdown: Spinner
    private lateinit var smsContainer: LinearLayout
    private lateinit var syncButton: Button
    private var smsList: MutableList<SmsData> = mutableListOf()
    private lateinit var uniqueId: String

    data class SmsData(val address: String, val date: Long, val body: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        senderDropdown = findViewById(R.id.senderDropdown)
        smsContainer = findViewById(R.id.smsContainer)
        syncButton = findViewById(R.id.syncButton)

        uniqueId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val senders = arrayOf("16216", "bKash")
        senderDropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, senders)

        if (checkSmsPermission()) {
            loadSmsForSelectedSender()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                SMS_PERMISSION_CODE
            )
        }

        senderDropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadSmsForSelectedSender()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        syncButton.setOnClickListener {
            smsList.forEach { sms ->
                SyncTask(uniqueId, sms).execute()
            }
            Toast.makeText(this, "Sync started...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun loadSmsForSelectedSender() {
        val sender = senderDropdown.selectedItem.toString()
        smsList.clear()
        smsContainer.removeAllViews()

        val uri = Uri.parse("content://sms/inbox")
        val cursor: Cursor? = contentResolver.query(
            uri, arrayOf("_id", "address", "date", "body"),
            "address LIKE ?", arrayOf("%$sender%"), "date DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val address = it.getString(1)
                val date = it.getLong(2)
                val body = it.getString(3)
                val sms = SmsData(address, date, body)
                smsList.add(sms)

                val tv = TextView(this)
                tv.text = "$address\n$body\n${Date(date)}"
                tv.setPadding(0, 10, 0, 10)
                smsContainer.addView(tv)
            }
        }
    }

    class SyncTask(private val uniqueId: String, private val sms: SmsData) : AsyncTask<Void, Void, Boolean>() {
        override fun doInBackground(vararg params: Void?): Boolean {
            try {
                val url = URL("https://www.import-park.com/sync-sms")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject()
                json.put("unique_id", uniqueId)
                json.put("sms_timestamp", sms.date)
                json.put("content", sms.body)

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(json.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                conn.disconnect()
                return responseCode in 200..299
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }
}
