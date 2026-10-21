package com.hisab.tracker

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Which full-screen form/detail is open (null = normal tabs). */
sealed class FormReq {
    data class TxF(val type: TxType, val edit: Tx? = null, val loanId: Long? = null) : FormReq()
    data class AccountF(val edit: Account? = null) : FormReq()
    data class LoanDetail(val id: Long) : FormReq()
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repo(AppDb.get(app))
    private val prefs = app.getSharedPreferences("hisab_prefs", Context.MODE_PRIVATE)

    val data: StateFlow<AppData> = combine(repo.accounts, repo.txs, repo.loans, repo.categories) { a, t, l, c ->
        AppData(a, t, l, c, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    var bn by mutableStateOf(
        prefs.getString("lang", null)?.let { it == "bn" } ?: (Locale.getDefault().language == "bn")
    )
        private set
    var theme by mutableStateOf(prefs.getString("theme", "system") ?: "system")
        private set
    var secure by mutableStateOf(prefs.getBoolean("secure", false))
        private set
    var hasPin by mutableStateOf(prefs.contains("pin_hash"))
        private set
    var locked by mutableStateOf(prefs.contains("pin_hash"))
        private set

    var tab by mutableIntStateOf(0)
    var form by mutableStateOf<FormReq?>(null)

    /** Set right before opening a system file picker so returning from it does not trigger the PIN lock. */
    var skipNextLock = false

    init {
        viewModelScope.launch {
            if (!prefs.getBoolean("seeded", false)) {
                repo.seed()
                prefs.edit().putBoolean("seeded", true).apply()
            }
        }
    }

    // ---------- helpers ----------

    fun toast(key: String) {
        Toast.makeText(app, Str.get(key, bn), Toast.LENGTH_SHORT).show()
    }

    fun closeForm() {
        val f = form
        form = if (f is FormReq.TxF && f.loanId != null) FormReq.LoanDetail(f.loanId) else null
    }

    private fun launchWrite(block: suspend () -> String?, okKey: String?, onOk: () -> Unit) {
        viewModelScope.launch {
            val err = block()
            if (err == null) {
                if (okKey != null) toast(okKey)
                onOk()
            } else {
                toast(err)
            }
        }
    }

    // ---------- ledger writes ----------

    fun saveTx(tx: Tx, onOk: () -> Unit) = launchWrite({ repo.saveTx(tx) }, "saved", onOk)
    fun saveLoan(loan: Loan, tx: Tx, onOk: () -> Unit) = launchWrite({ repo.saveLoan(loan, tx) }, "saved", onOk)
    fun deleteTx(id: Long, onOk: () -> Unit = {}) = launchWrite({ repo.deleteTx(id) }, "deleted", onOk)
    fun saveAccount(a: Account, onOk: () -> Unit) = launchWrite({ repo.saveAccount(a) }, "saved", onOk)
    fun deleteAccount(id: Long, onOk: () -> Unit) = launchWrite({ repo.deleteAccount(id) }, "deleted", onOk)

    fun deleteLoan(id: Long, onOk: () -> Unit) {
        viewModelScope.launch {
            repo.deleteLoan(id)
            toast("deleted")
            onOk()
        }
    }

    fun addCategory(name: String, forIncome: Boolean) {
        viewModelScope.launch { repo.addCategory(name, forIncome) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { repo.deleteCategory(id) }
    }

    // ---------- backup ----------

    suspend fun exportJson(): String = repo.exportJson()

    fun restore(text: String) {
        viewModelScope.launch {
            val err = repo.restoreJson(text)
            if (err == null) {
                prefs.edit().putBoolean("seeded", true).apply()
                form = null
                toast("restored")
            } else {
                toast(err)
            }
        }
    }

    // ---------- settings ----------

    fun setLang(isBn: Boolean) {
        bn = isBn
        prefs.edit().putString("lang", if (isBn) "bn" else "en").apply()
    }

    fun setTheme(t: String) {
        theme = t
        prefs.edit().putString("theme", t).apply()
    }

    fun setSecure(v: Boolean) {
        secure = v
        prefs.edit().putBoolean("secure", v).apply()
    }

    // ---------- app lock ----------

    private fun hashPin(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hashPin(pin, salt)).apply()
        hasPin = true
    }

    fun removePin() {
        prefs.edit().remove("pin_salt").remove("pin_hash").apply()
        hasPin = false
        locked = false
    }

    fun unlock(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", "") ?: ""
        val ok = hashPin(pin, salt) == prefs.getString("pin_hash", null)
        if (ok) locked = false
        return ok
    }

    fun lockIfNeeded() {
        if (skipNextLock) {
            skipNextLock = false
            return
        }
        if (hasPin) locked = true
    }
}
