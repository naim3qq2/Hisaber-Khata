package com.hisab.tracker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

enum class AccType { CASH, WALLET, BANK, OTHER }
enum class TxType { INCOME, EXPENSE, TRANSFER, BORROW, REPAY_DEBT, LEND, RECEIVE_BACK }
enum class LoanKind { BORROWED, LENT }

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccType,
    val openingMinor: Long = 0L
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val forIncome: Boolean
)

/** A loan only stores who and when it is due. Amounts live in the ledger (Tx) so they can never disagree. */
@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: LoanKind,
    val person: String,
    val dueMillis: Long? = null
)

/** The single ledger. Every balance, debt and report is derived from these rows. */
@Entity(tableName = "transactions")
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amountMinor: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val category: String = "",
    val source: String = "",
    val loanId: Long? = null,
    val millis: Long,
    val note: String = ""
)

data class AppData(
    val accounts: List<Account> = emptyList(),
    val txs: List<Tx> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val categories: List<Category> = emptyList(),
    val loaded: Boolean = false
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM transactions ORDER BY millis DESC, id DESC") fun txs(): Flow<List<Tx>>
    @Query("SELECT * FROM loans ORDER BY id DESC") fun loans(): Flow<List<Loan>>
    @Query("SELECT * FROM categories ORDER BY id") fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM accounts") suspend fun accountsOnce(): List<Account>
    @Query("SELECT * FROM transactions") suspend fun txsOnce(): List<Tx>
    @Query("SELECT * FROM loans") suspend fun loansOnce(): List<Loan>
    @Query("SELECT * FROM categories") suspend fun categoriesOnce(): List<Category>

    @Insert suspend fun insertAccount(a: Account): Long
    @Update suspend fun updateAccount(a: Account)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteAccount(id: Long)

    @Insert suspend fun insertTx(t: Tx): Long
    @Update suspend fun updateTx(t: Tx)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTx(id: Long)
    @Query("DELETE FROM transactions WHERE loanId = :loanId") suspend fun deleteTxByLoan(loanId: Long)

    @Insert suspend fun insertLoan(l: Loan): Long
    @Update suspend fun updateLoan(l: Loan)
    @Query("DELETE FROM loans WHERE id = :id") suspend fun deleteLoan(id: Long)

    @Insert suspend fun insertCategory(c: Category): Long
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: Long)

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM transactions") suspend fun clearTxs()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM categories") suspend fun clearCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(list: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTxs(list: List<Tx>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLoans(list: List<Loan>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(list: List<Category>)
}

@Database(
    entities = [Account::class, Category::class, Loan::class, Tx::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "hisab.db")
                .build()
                .also { inst = it }
        }
    }
}

/**
 * Every write runs inside one DB transaction: load ledger -> apply change in memory ->
 * Ledger.validate -> only then write. A rejected change never touches the database.
 */
class Repo(private val db: AppDb) {
    private val dao = db.dao()

    val accounts: Flow<List<Account>> = dao.accounts()
    val txs: Flow<List<Tx>> = dao.txs()
    val loans: Flow<List<Loan>> = dao.loans()
    val categories: Flow<List<Category>> = dao.categories()

    suspend fun seed() {
        if (dao.accountsOnce().isNotEmpty()) return
        dao.insertAccount(Account(name = "Cash", type = AccType.CASH))
        dao.insertAccount(Account(name = "bKash", type = AccType.WALLET))
        dao.insertAccount(Account(name = "Nagad", type = AccType.WALLET))
        dao.insertAccount(Account(name = "Bank Account", type = AccType.BANK))
        dao.insertAccount(Account(name = "Rocket", type = AccType.WALLET))
    }

    suspend fun saveTx(tx: Tx): String? = db.withTransaction<String?> {
        val accs = dao.accountsOnce()
        val loans = dao.loansOnce()
        val txs = dao.txsOnce().filter { it.id != tx.id } + tx
        val err = Ledger.validate(accs, loans, txs)
        if (err != null) return@withTransaction err
        if (tx.id == 0L) dao.insertTx(tx) else dao.updateTx(tx)
        null
    }

    /** Create or edit a loan together with its starting transaction (money borrowed / lent). */
    suspend fun saveLoan(loan: Loan, tx: Tx): String? = db.withTransaction<String?> {
        val accs = dao.accountsOnce()
        val loans = dao.loansOnce()
        val txs = dao.txsOnce()
        val isNew = loan.id == 0L
        val lid = if (isNew) -1L else loan.id
        val newLoans = loans.filter { it.id != loan.id } + loan.copy(id = lid)
        val newTxs = txs.filter { it.id != tx.id } + tx.copy(loanId = lid)
        val err = Ledger.validate(accs, newLoans, newTxs)
        if (err != null) return@withTransaction err
        if (isNew) {
            val newId = dao.insertLoan(loan.copy(id = 0L))
            dao.insertTx(tx.copy(id = 0L, loanId = newId))
        } else {
            dao.updateLoan(loan)
            if (tx.id == 0L) dao.insertTx(tx.copy(loanId = loan.id)) else dao.updateTx(tx.copy(loanId = loan.id))
        }
        null
    }

    suspend fun deleteTx(id: Long): String? = db.withTransaction<String?> {
        val accs = dao.accountsOnce()
        val loans = dao.loansOnce()
        val all = dao.txsOnce()
        val target = all.firstOrNull { it.id == id } ?: return@withTransaction null
        val loanId = target.loanId
        val startsLoan = (target.type == TxType.BORROW || target.type == TxType.LEND) && loanId != null
        val remainingLoans = if (startsLoan) loans.filter { it.id != loanId } else loans
        val err = Ledger.validate(accs, remainingLoans, all.filter { it.id != id })
        if (err != null) return@withTransaction err
        dao.deleteTx(id)
        if (startsLoan && loanId != null) dao.deleteLoan(loanId)
        null
    }

    suspend fun deleteLoan(id: Long) = db.withTransaction {
        dao.deleteTxByLoan(id)
        dao.deleteLoan(id)
    }

    suspend fun saveAccount(a: Account): String? {
        if (a.name.isBlank()) return "err_name"
        if (a.id == 0L) dao.insertAccount(a) else dao.updateAccount(a)
        return null
    }

    suspend fun deleteAccount(id: Long): String? = db.withTransaction<String?> {
        if (dao.txsOnce().any { it.accountId == id || it.toAccountId == id }) return@withTransaction "err_account_used"
        dao.deleteAccount(id)
        null
    }

    suspend fun addCategory(name: String, forIncome: Boolean) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (dao.categoriesOnce().any { it.name.equals(n, ignoreCase = true) && it.forIncome == forIncome }) return
        dao.insertCategory(Category(name = n, forIncome = forIncome))
    }

    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)

    // ---------- Backup / restore (plain JSON, amounts in poisha) ----------

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "hisab")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val accs = JSONArray()
        for (a in dao.accountsOnce()) {
            accs.put(JSONObject().put("id", a.id).put("name", a.name).put("type", a.type.name).put("opening", a.openingMinor))
        }
        root.put("accounts", accs)
        val cats = JSONArray()
        for (c in dao.categoriesOnce()) {
            cats.put(JSONObject().put("id", c.id).put("name", c.name).put("income", c.forIncome))
        }
        root.put("categories", cats)
        val loans = JSONArray()
        for (l in dao.loansOnce()) {
            loans.put(
                JSONObject().put("id", l.id).put("kind", l.kind.name).put("person", l.person)
                    .put("due", l.dueMillis ?: JSONObject.NULL)
            )
        }
        root.put("loans", loans)
        val txs = JSONArray()
        for (t in dao.txsOnce()) {
            txs.put(
                JSONObject().put("id", t.id).put("type", t.type.name).put("amount", t.amountMinor)
                    .put("account", t.accountId).put("to", t.toAccountId ?: JSONObject.NULL)
                    .put("category", t.category).put("source", t.source)
                    .put("loan", t.loanId ?: JSONObject.NULL)
                    .put("millis", t.millis).put("note", t.note)
            )
        }
        root.put("txs", txs)
        return root.toString(2)
    }

    private class Parsed(val accounts: List<Account>, val categories: List<Category>, val loans: List<Loan>, val txs: List<Tx>)

    private fun parse(text: String): Parsed {
        val root = JSONObject(text)
        if (root.optString("app") != "hisab") throw IllegalArgumentException("not a Hisab backup")
        fun longOrNull(o: JSONObject, k: String): Long? = if (o.isNull(k)) null else o.getLong(k)
        val accs = root.getJSONArray("accounts")
        val cats = root.getJSONArray("categories")
        val loans = root.getJSONArray("loans")
        val txs = root.getJSONArray("txs")
        return Parsed(
            (0 until accs.length()).map {
                val o = accs.getJSONObject(it)
                Account(o.getLong("id"), o.getString("name"), AccType.valueOf(o.getString("type")), o.getLong("opening"))
            },
            (0 until cats.length()).map {
                val o = cats.getJSONObject(it)
                Category(o.getLong("id"), o.getString("name"), o.getBoolean("income"))
            },
            (0 until loans.length()).map {
                val o = loans.getJSONObject(it)
                Loan(o.getLong("id"), LoanKind.valueOf(o.getString("kind")), o.getString("person"), longOrNull(o, "due"))
            },
            (0 until txs.length()).map {
                val o = txs.getJSONObject(it)
                Tx(
                    id = o.getLong("id"),
                    type = TxType.valueOf(o.getString("type")),
                    amountMinor = o.getLong("amount"),
                    accountId = o.getLong("account"),
                    toAccountId = longOrNull(o, "to"),
                    category = o.optString("category", ""),
                    source = o.optString("source", ""),
                    loanId = longOrNull(o, "loan"),
                    millis = o.getLong("millis"),
                    note = o.optString("note", "")
                )
            }
        )
    }

    /** Replaces ALL data with the backup – but only if the backup passes the same validation as normal entries. */
    suspend fun restoreJson(text: String): String? {
        val p = try {
            parse(text)
        } catch (e: Exception) {
            return "err_backup"
        }
        if (Ledger.validate(p.accounts, p.loans, p.txs) != null) return "err_backup"
        db.withTransaction {
            dao.clearTxs()
            dao.clearLoans()
            dao.clearCategories()
            dao.clearAccounts()
            dao.insertAccounts(p.accounts)
            dao.insertCategories(p.categories)
            dao.insertLoans(p.loans)
            dao.insertTxs(p.txs)
        }
        return null
    }
}

object Csv {
    private fun esc(s: String): String {
        var v = s
        if (v.isNotEmpty() && v[0] in "=+-@") v = "'$v" // stop spreadsheet formula injection
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    private fun typeName(t: TxType) = when (t) {
        TxType.INCOME -> "Income"
        TxType.EXPENSE -> "Expense"
        TxType.TRANSFER -> "Transfer"
        TxType.BORROW -> "Borrowed"
        TxType.REPAY_DEBT -> "Debt repaid"
        TxType.LEND -> "Lent"
        TxType.RECEIVE_BACK -> "Repayment received"
    }

    fun build(txs: List<Tx>, accounts: List<Account>, loans: List<Loan>): String {
        val an = accounts.associate { it.id to it.name }
        val ln = loans.associate { it.id to it.person }
        val sb = StringBuilder("\uFEFF") // BOM so Excel reads Bengali text correctly
        sb.append("Date,Type,Account,To account,Category,Source,Person,Amount (BDT),Note\n")
        for (t in txs.sortedBy { it.millis }) {
            sb.append(Fmt.csvDate(t.millis)).append(',')
                .append(typeName(t.type)).append(',')
                .append(esc(an[t.accountId] ?: "")).append(',')
                .append(esc(t.toAccountId?.let { an[it] } ?: "")).append(',')
                .append(esc(t.category)).append(',')
                .append(esc(t.source)).append(',')
                .append(esc(t.loanId?.let { ln[it] } ?: "")).append(',')
                .append(Money.plain(t.amountMinor)).append(',')
                .append(esc(t.note)).append('\n')
        }
        return sb.toString()
    }
}
