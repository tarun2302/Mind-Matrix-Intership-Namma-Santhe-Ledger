package com.example.nammasantheledger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.example.nammasantheledger.data.Transaction
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*

class AddCustomerBottomSheet(private val viewModel: LedgerViewModel) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_add_customer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nameInputLayout = view.findViewById<TextInputLayout>(R.id.nameInputLayout)
        val customerNameInput = view.findViewById<TextInputEditText>(R.id.customerNameInput)
        
        val phoneInputLayout = view.findViewById<TextInputLayout>(R.id.phoneInputLayout)
        val customerPhoneInput = view.findViewById<TextInputEditText>(R.id.customerPhoneInput)
        
        val welcomeMsgToggle = view.findViewById<SwitchMaterial>(R.id.welcomeMsgToggle)
        val saveCustomerBtn = view.findViewById<Button>(R.id.saveCustomerBtn)

        fun validateFields() {
            val name = customerNameInput.text.toString().trim()
            val phone = customerPhoneInput.text.toString().trim()
            var isValid = true

            if (name.isEmpty()) {
                nameInputLayout.error = "Name is required"
                isValid = false
            } else {
                nameInputLayout.error = null
            }

            if (phone.length != 10) {
                phoneInputLayout.error = "Phone must be exactly 10 digits"
                isValid = false
            } else {
                phoneInputLayout.error = null
            }

            saveCustomerBtn.isEnabled = isValid
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateFields()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        customerNameInput.addTextChangedListener(textWatcher)
        customerPhoneInput.addTextChangedListener(textWatcher)

        saveCustomerBtn.setOnClickListener {
            val name = customerNameInput.text.toString().trim()
            val phone = "+91" + customerPhoneInput.text.toString().trim()

            // Save customer (creating a 0 amount SETUP transaction)
            val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            val transaction = Transaction(
                customerName = name,
                phoneNumber = phone,
                amount = 0.0,
                timestamp = timestamp,
                type = "SETUP"
            )
            viewModel.addTransaction(transaction)

            if (welcomeMsgToggle.isChecked) {
                val msg = "Welcome $name to Namma Santhe Ledger! We're glad to have you."
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(msg)}")
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                }
            }

            dismiss()
        }
    }
}
