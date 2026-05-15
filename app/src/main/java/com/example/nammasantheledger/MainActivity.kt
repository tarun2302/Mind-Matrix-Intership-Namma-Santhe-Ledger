package com.example.nammasantheledger

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.nammasantheledger.data.Transaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(context: Context, private val items: List<Transaction>) :
    ArrayAdapter<Transaction>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_transaction, parent, false)
        val item = items[position]
        
        view.findViewById<TextView>(R.id.avatarText).text =
            item.customerName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        view.findViewById<TextView>(R.id.customerNameText).text = item.customerName
        view.findViewById<TextView>(R.id.phoneNumberText).text =
            if (item.phoneNumber.isEmpty()) "No phone" else item.phoneNumber
        view.findViewById<TextView>(R.id.timestampText).text = item.timestamp
        
        val amountTv = view.findViewById<TextView>(R.id.amountText)
        val typeTv = view.findViewById<TextView>(R.id.typeText)
        val whatsappBtn = view.findViewById<ImageButton>(R.id.whatsappBtn)

        amountTv.text = "₹${String.format("%.2f", item.amount)}"
        if (item.type == "CREDIT") {
            amountTv.setTextColor(android.graphics.Color.parseColor("#E53935")) // Red
            typeTv.text = "UDARI (CREDIT)"
            typeTv.setTextColor(android.graphics.Color.parseColor("#E53935"))
        } else if (item.type == "PAYMENT") {
            amountTv.setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Green
            typeTv.text = "PAYMENT"
            typeTv.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
        } else {
            amountTv.setTextColor(android.graphics.Color.parseColor("#757575")) // Grey
            typeTv.text = "CUSTOMER SETUP"
            typeTv.setTextColor(android.graphics.Color.parseColor("#757575"))
        }

        whatsappBtn.setOnClickListener {
            val due = if (item.type == "CREDIT") item.amount else 0.0
            val msg = "Hello ${item.customerName}, gentle reminder of your pending due of ₹${String.format("%.2f", due)} at our shop. Please clear it soon."
            val intent = Intent(Intent.ACTION_VIEW)
            val phone = if (item.phoneNumber.startsWith("+")) item.phoneNumber else "+91${item.phoneNumber}"
            intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}")
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }
        
        return view
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: LedgerViewModel
    private lateinit var adapter: TransactionAdapter
    private val transactions = ArrayList<Transaction>()

    private lateinit var dailySummaryTv: TextView
    private lateinit var totalOutstandingTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addCustomerFab).setOnClickListener {
            AddCustomerBottomSheet(viewModel).show(supportFragmentManager, "AddCustomer")
        }

        viewModel = ViewModelProvider(this).get(LedgerViewModel::class.java)

        val customerNameInput = findViewById<AutoCompleteTextView>(R.id.customerName)
        val amountInput       = findViewById<EditText>(R.id.amount)
        val addUdariButton    = findViewById<Button>(R.id.addUdariButton)
        val addPaymentButton  = findViewById<Button>(R.id.addPaymentButton)
        val historyList       = findViewById<ListView>(R.id.historyList)
        val searchCustomer    = findViewById<EditText>(R.id.searchCustomer)
        
        dailySummaryTv       = findViewById(R.id.dailySummaryText)
        totalOutstandingTv   = findViewById(R.id.totalOutstanding)
        val dateText          = findViewById<TextView>(R.id.dateText)
        val clearAll          = findViewById<TextView>(R.id.clearAll)

        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val shopNameTv   = findViewById<TextView>(R.id.shopNameHeader)
        shopNameTv.text  = profilePrefs.getString("shopName", "Namma Santhe Ledger") ?: "Namma Santhe Ledger"

        dateText.text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date())

        adapter = TransactionAdapter(this, transactions)
        historyList.adapter = adapter

        viewModel.transactions.observe(this) { list ->
            transactions.clear()
            transactions.addAll(list)
            adapter.notifyDataSetChanged()
            updateSummary()

            // Update AutoCompleteTextView suggestions
            val uniqueNames = list.map { it.customerName }.distinct()
            val autoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, uniqueNames)
            customerNameInput.setAdapter(autoAdapter)
        }

        viewModel.loadTransactions()

        searchCustomer.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.loadTransactions(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun addTransaction(type: String) {
            val name   = customerNameInput.text.toString().trim()
            val amtStr = amountInput.text.toString().trim()

            if (name.isEmpty() || amtStr.isEmpty()) {
                Toast.makeText(this, "Name and amount are required", Toast.LENGTH_SHORT).show()
                return
            }

            val amount = amtStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
                return
            }

            val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            // Retrieve phone number if it exists in history
            val existingPhone = transactions.find { it.customerName.equals(name, ignoreCase = true) && it.phoneNumber.isNotEmpty() }?.phoneNumber ?: ""
            
            val transaction = Transaction(
                customerName = name, 
                phoneNumber = existingPhone, 
                amount = amount, 
                timestamp = timestamp,
                type = type
            )

            viewModel.addTransaction(transaction)
            
            customerNameInput.setText("")
            amountInput.setText("")
            Toast.makeText(this, "✅ ${if (type == "CREDIT") "Udari" else "Payment"} saved!", Toast.LENGTH_SHORT).show()
        }

        addUdariButton.setOnClickListener { addTransaction("CREDIT") }
        addPaymentButton.setOnClickListener { addTransaction("PAYMENT") }

        historyList.setOnItemClickListener { _, _, position, _ ->
            val t = transactions[position]
            val customerTransactions = transactions.filter { it.customerName.equals(t.customerName, ignoreCase = true) }
            val totalCredit = customerTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
            val totalPayment = customerTransactions.filter { it.type == "PAYMENT" }.sumOf { it.amount }
            val due = totalCredit - totalPayment

            val intent = Intent(this, CustomerProfileActivity::class.java).apply {
                putExtra("customerName", t.customerName)
                putExtra("customerPhone", t.phoneNumber)
                putExtra("customerDue", due)
            }
            startActivity(intent)
        }

        historyList.setOnItemLongClickListener { _, _, position, _ ->
            val t = transactions[position]
            AlertDialog.Builder(this)
                .setTitle("🗑️ Delete Transaction")
                .setMessage("Delete ${t.customerName}'s entry of ₹${String.format("%.2f", t.amount)}?")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deleteTransaction(t)
                    Toast.makeText(this, "Deleted!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        clearAll.setOnClickListener {
            if (transactions.isEmpty()) {
                Toast.makeText(this, "Nothing to clear", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("⚠️ Clear All")
                .setMessage("Delete all ${transactions.size} transactions?")
                .setPositiveButton("Clear All") { _, _ ->
                    viewModel.clearAll()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val profilePrefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        findViewById<TextView>(R.id.shopNameHeader).text =
            profilePrefs.getString("shopName", "Namma Santhe Ledger")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Logout") { _, _ ->
                        getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("isLoggedIn", false).apply()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateSummary() {
        val todayStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        
        val todaySales = transactions.filter { 
            it.type == "CREDIT" && it.timestamp.startsWith(todayStr) 
        }.sumOf { it.amount }
        
        val totalCredit = transactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val totalPayment = transactions.filter { it.type == "PAYMENT" }.sumOf { it.amount }
        val totalOutstanding = totalCredit - totalPayment
        
        dailySummaryTv.text = "Daily Summary: \"Today you sold for ₹${todaySales.toInt()}; Dues pending ₹${totalOutstanding.toInt()}.\""
        totalOutstandingTv.text = "₹${String.format("%.2f", totalOutstanding)}"
    }
}