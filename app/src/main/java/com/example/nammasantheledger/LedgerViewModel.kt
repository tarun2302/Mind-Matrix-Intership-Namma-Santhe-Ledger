package com.example.nammasantheledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.nammasantheledger.data.AppDatabase
import com.example.nammasantheledger.data.Transaction
import java.util.concurrent.Executors

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.transactionDao()
    private val executor = Executors.newSingleThreadExecutor()

    private val _transactions = MutableLiveData<List<Transaction>>()
    val transactions: LiveData<List<Transaction>> get() = _transactions

    fun loadTransactions(query: String = "") {
        executor.execute {
            val all = if (query.isEmpty()) {
                dao.getAll()
            } else {
                dao.searchByName("%$query%")
            }
            _transactions.postValue(all)
        }
    }

    fun addTransaction(transaction: Transaction) {
        executor.execute {
            dao.insert(transaction)
            loadTransactions()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        executor.execute {
            dao.delete(transaction)
            loadTransactions()
        }
    }

    fun clearAll() {
        executor.execute {
            dao.deleteAll()
            loadTransactions()
        }
    }
}
