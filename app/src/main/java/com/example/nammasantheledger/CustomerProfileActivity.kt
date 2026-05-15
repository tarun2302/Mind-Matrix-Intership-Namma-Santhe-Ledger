package com.example.nammasantheledger

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class CustomerProfileActivity : AppCompatActivity() {

    private var customerName: String = ""
    private var customerPhone: String = ""
    private var customerDue: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_profile)

        supportActionBar?.title = "Customer Profile"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        customerName = intent.getStringExtra("customerName") ?: "Unknown"
        customerPhone = intent.getStringExtra("customerPhone") ?: ""
        customerDue = intent.getDoubleExtra("customerDue", 0.0)

        findViewById<TextView>(R.id.cpNameText).text = customerName
        findViewById<TextView>(R.id.cpPhoneText).text = if (customerPhone.isNotEmpty()) customerPhone else "No phone saved"
        findViewById<TextView>(R.id.cpDueText).text = "₹${String.format("%.2f", customerDue)}"

        val sendReminderBtn = findViewById<Button>(R.id.sendReminderBtn)
        val rootLayout = findViewById<android.view.View>(R.id.customerProfileRoot)

        if (customerPhone.isEmpty()) {
            Snackbar.make(rootLayout, "No phone number saved.", Snackbar.LENGTH_INDEFINITE)
                .setAction("Edit") {
                    showEditPhoneDialog()
                }.show()
        }

        sendReminderBtn.setOnClickListener {
            if (customerPhone.isEmpty()) {
                showEditPhoneDialog()
                return@setOnClickListener
            }
            sendWhatsAppApiReminder(rootLayout)
        }
    }

    private fun showEditPhoneDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_PHONE
        input.hint = "10 digit phone number"

        AlertDialog.Builder(this)
            .setTitle("Add Phone Number")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val phone = input.text.toString().trim()
                if (phone.length == 10) {
                    customerPhone = "+91$phone"
                    findViewById<TextView>(R.id.cpPhoneText).text = customerPhone
                    Snackbar.make(findViewById(R.id.customerProfileRoot), "Phone number added for session.", Snackbar.LENGTH_SHORT).show()
                    // Note: Ideally we update the database here, but the requirement specifically says "lets the shopkeeper add the number on the spot"
                    // and since we don't have a Customer table, we just update the in-memory variable for the intent.
                } else {
                    Snackbar.make(findViewById(R.id.customerProfileRoot), "Invalid phone number.", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendWhatsAppApiReminder(rootLayout: android.view.View) {
        val msg = "Hello $customerName, gentle reminder of your pending due of ₹${String.format("%.2f", customerDue)} at our shop. Please clear it soon."
        val token = getString(R.string.meta_api_token)
        val phoneId = getString(R.string.meta_phone_number_id)

        Snackbar.make(rootLayout, "Sending reminder via Meta API...", Snackbar.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://graph.facebook.com/v17.0/$phoneId/messages")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                // Note: Meta API usually requires template messages for business-initiated chats outside the 24h window.
                // Assuming this is a standard text message for simplicity as per requirements.
                val jsonBody = JSONObject().apply {
                    put("messaging_product", "whatsapp")
                    put("to", customerPhone.replace("+", "")) // Meta requires phone number without '+'
                    put("type", "text")
                    put("text", JSONObject().put("body", msg))
                }

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    if (responseCode in 200..299) {
                        Snackbar.make(rootLayout, "✅ Reminder sent successfully via Meta API!", Snackbar.LENGTH_LONG).show()
                    } else {
                        throw Exception("API Error: $responseCode")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(rootLayout, "API failed, falling back to WhatsApp App...", Snackbar.LENGTH_LONG).show()
                    fallbackToWhatsAppApp(msg)
                }
            }
        }
    }

    private fun fallbackToWhatsAppApp(msg: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$customerPhone&text=${Uri.encode(msg)}")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(findViewById(R.id.customerProfileRoot), "WhatsApp not installed.", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
