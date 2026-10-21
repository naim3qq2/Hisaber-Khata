package com.hisab.tracker

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

fun zone(): ZoneId = ZoneId.systemDefault()

/** All money is stored as Long "poisha" (1 BDT = 100 poisha). No floating point anywhere in balances. */
object Money {
    private val inputRegex = Regex("(\\d{1,12})?(\\.\\d{1,9})?")

    fun parse(input: String, allowZero: Boolean = false): Long? {
        val s = Fmt.asciiDigits(input).replace(",", "").trim()
        if (s.isEmpty() || !inputRegex.matches(s)) return null
        return try {
            val bd = BigDecimal(s).setScale(2, RoundingMode.HALF_UP)
            if (bd.signum() < 0 || (bd.signum() == 0 && !allowZero)) null
            else bd.movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /** "1234.50" (always 2 decimals, ASCII) – used for CSV. */
    fun plain(minor: Long): String {
        val a = abs(minor)
        return (if (minor < 0) "-" else "") + (a / 100) + "." + (a % 100).toString().padStart(2, '0')
    }

    /** Text for an edit field: "1500" or "1500.50". */
    fun input(minor: Long): String =
        if (minor % 100 == 0L) (minor / 100).toString() else plain(minor)

    /** Bangladesh style: ৳ 12,34,567.89 (lakh/crore grouping), Bengali digits when bn = true. */
    fun format(minor: Long, bn: Boolean, symbol: Boolean = true): String {
        val a = abs(minor)
        val whole = group((a / 100).toString())
        val frac = a % 100
        var s = if (frac == 0L) whole else whole + "." + frac.toString().padStart(2, '0')
        if (symbol) s = "৳ $s"
        if (minor < 0) s = "-$s"
        return Fmt.digits(s, bn)
    }

    fun group(d: String): String {
        if (d.length <= 3) return d
        val last3 = d.takeLast(3)
        var rest = d.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }
}

object Fmt {
    private val enMonths = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val bnMonths = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val bnMonthsShort = listOf("জানু", "ফেব", "মার্চ", "এপ্রি", "মে", "জুন", "জুলাই", "আগ", "সেপ্টে", "অক্টো", "নভে", "ডিসে")
    private val enDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val bnDays = listOf("রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি")
    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val csvFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    fun asciiDigits(s: String): String = buildString {
        for (c in s) append(if (c in '০'..'৯') '0' + (c - '০') else c)
    }

    fun digits(s: String, bn: Boolean): String {
        if (!bn) return s
        return buildString { for (c in s) append(if (c in '0'..'9') '০' + (c - '0') else c) }
    }

    fun zoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone())
    fun date(millis: Long, bn: Boolean): String = digits(dateFmt.format(zoned(millis)), bn)
    fun time(millis: Long, bn: Boolean): String {
        var s = timeFmt.format(zoned(millis))
        if (bn) s = s.replace("AM", "পূর্বাহ্ণ").replace("PM", "অপরাহ্ণ")
        return digits(s, bn)
    }
    fun dateTime(millis: Long, bn: Boolean): String = date(millis, bn) + ", " + time(millis, bn)
    fun csvDate(millis: Long): String = csvFmt.format(zoned(millis))

    fun today(): LocalDate = LocalDate.now(zone())
    fun toLocalDate(millis: Long): LocalDate = zoned(millis).toLocalDate()
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun hour(millis: Long): Int = zoned(millis).hour
    fun minute(millis: Long): Int = zoned(millis).minute

    fun withDate(millis: Long, d: LocalDate): Long =
        LocalDateTime.of(d, zoned(millis).toLocalTime()).atZone(zone()).toInstant().toEpochMilli()

    fun withTime(millis: Long, h: Int, m: Int): Long =
        LocalDateTime.of(toLocalDate(millis), LocalTime.of(h, m)).atZone(zone()).toInstant().toEpochMilli()

    fun weekdayShort(d: LocalDate, bn: Boolean): String {
        val i = d.dayOfWeek.value % 7 // Sunday = 0
        return if (bn) bnDays[i] else enDays[i]
    }

    fun monthName(m: Int, bn: Boolean, short: Boolean = false): String = when {
        bn && short -> bnMonthsShort[m - 1]
        bn -> bnMonths[m - 1]
        short -> enMonths[m - 1].take(3)
        else -> enMonths[m - 1]
    }
}

enum class ReportPeriod { DAY, WEEK, MONTH, YEAR }

data class Bucket(val label: String, val from: Long, val to: Long)

object Periods {
    /** Weeks start on Saturday (Bangladesh convention). */
    fun start(p: ReportPeriod, a: LocalDate): LocalDate = when (p) {
        ReportPeriod.DAY -> a
        ReportPeriod.WEEK -> a.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        ReportPeriod.MONTH -> a.withDayOfMonth(1)
        ReportPeriod.YEAR -> a.withDayOfYear(1)
    }

    fun endExclusive(p: ReportPeriod, a: LocalDate): LocalDate {
        val s = start(p, a)
        return when (p) {
            ReportPeriod.DAY -> s.plusDays(1)
            ReportPeriod.WEEK -> s.plusDays(7)
            ReportPeriod.MONTH -> s.plusMonths(1)
            ReportPeriod.YEAR -> s.plusYears(1)
        }
    }

    fun shift(p: ReportPeriod, a: LocalDate, n: Int): LocalDate = when (p) {
        ReportPeriod.DAY -> a.plusDays(n.toLong())
        ReportPeriod.WEEK -> a.plusWeeks(n.toLong())
        ReportPeriod.MONTH -> a.plusMonths(n.toLong())
        ReportPeriod.YEAR -> a.plusYears(n.toLong())
    }

    /** [from, to) in epoch millis. */
    fun range(p: ReportPeriod, a: LocalDate): Pair<Long, Long> =
        Fmt.startOfDay(start(p, a)) to Fmt.startOfDay(endExclusive(p, a))

    fun label(p: ReportPeriod, a: LocalDate, bn: Boolean): String {
        val s = start(p, a)
        return when (p) {
            ReportPeriod.DAY -> Fmt.digits(Fmt.date(Fmt.startOfDay(s), false), bn)
            ReportPeriod.WEEK -> Fmt.date(Fmt.startOfDay(s), bn) + " – " + Fmt.date(Fmt.startOfDay(s.plusDays(6)), bn)
            ReportPeriod.MONTH -> Fmt.monthName(s.monthValue, bn) + " " + Fmt.digits(s.year.toString(), bn)
            ReportPeriod.YEAR -> Fmt.digits(s.year.toString(), bn)
        }
    }

    fun buckets(p: ReportPeriod, a: LocalDate, bn: Boolean): List<Bucket> {
        val s = start(p, a)
        return when (p) {
            ReportPeriod.DAY -> (0 until 6).map { i ->
                val f = s.atStartOfDay(zone()).plusHours(i * 4L)
                Bucket(Fmt.digits("${i * 4}-${i * 4 + 4}", bn), f.toInstant().toEpochMilli(), f.plusHours(4L).toInstant().toEpochMilli())
            }
            ReportPeriod.WEEK -> (0 until 7).map { i ->
                val d = s.plusDays(i.toLong())
                Bucket(Fmt.weekdayShort(d, bn), Fmt.startOfDay(d), Fmt.startOfDay(d.plusDays(1)))
            }
            ReportPeriod.MONTH -> (0 until s.lengthOfMonth()).map { i ->
                val d = s.plusDays(i.toLong())
                Bucket(Fmt.digits(d.dayOfMonth.toString(), bn), Fmt.startOfDay(d), Fmt.startOfDay(d.plusDays(1)))
            }
            ReportPeriod.YEAR -> (1..12).map { m ->
                val d = LocalDate.of(s.year, m, 1)
                Bucket(Fmt.monthName(m, bn, true), Fmt.startOfDay(d), Fmt.startOfDay(d.plusMonths(1)))
            }
        }
    }
}

object Cats {
    val expense = listOf("food", "transport", "shopping", "bills", "education", "medical", "entertainment", "other")
    val income = listOf("salary", "business", "gift", "refund", "other")
}

data class Totals(
    val available: Long,
    val cash: Long,
    val wallet: Long,
    val bank: Long,
    val other: Long,
    val debt: Long,
    val receivable: Long
) {
    /** What I really have after paying my debts and collecting what others owe me. */
    val netPosition: Long get() = available - debt + receivable
}

data class PeriodSummary(
    val income: Long,
    val expense: Long,
    val borrowed: Long,
    val repaidDebt: Long,
    val lent: Long,
    val receivedBack: Long
) {
    val net: Long get() = income - expense
}

data class TxFilter(
    val query: String = "",
    val from: Long? = null,
    val toExclusive: Long? = null,
    val accountId: Long? = null,
    val type: TxType? = null
)

object Ledger {
    private fun MutableMap<Long, Long>.addTo(id: Long, v: Long) {
        val cur = this[id]
        if (cur != null) this[id] = cur + v
    }

    /** Balance of every account = opening balance + effect of every ledger entry. Derived, never stored. */
    fun balances(accounts: List<Account>, txs: List<Tx>): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        for (a in accounts) m[a.id] = a.openingMinor
        for (t in txs) {
            when (t.type) {
                TxType.INCOME, TxType.BORROW, TxType.RECEIVE_BACK -> m.addTo(t.accountId, t.amountMinor)
                TxType.EXPENSE, TxType.REPAY_DEBT, TxType.LEND -> m.addTo(t.accountId, -t.amountMinor)
                TxType.TRANSFER -> {
                    m.addTo(t.accountId, -t.amountMinor)
                    val to = t.toAccountId
                    if (to != null) m.addTo(to, t.amountMinor)
                }
            }
        }
        return m
    }

    private fun sumOfType(txs: List<Tx>, vararg types: TxType): Long {
        var s = 0L
        for (t in txs) if (t.type in types) s += t.amountMinor
        return s
    }

    fun debt(txs: List<Tx>): Long = sumOfType(txs, TxType.BORROW) - sumOfType(txs, TxType.REPAY_DEBT)
    fun receivable(txs: List<Tx>): Long = sumOfType(txs, TxType.LEND) - sumOfType(txs, TxType.RECEIVE_BACK)

    fun totals(accounts: List<Account>, txs: List<Tx>): Totals {
        val b = balances(accounts, txs)
        var cash = 0L
        var wallet = 0L
        var bank = 0L
        var other = 0L
        for (a in accounts) {
            val v = b[a.id] ?: 0L
            when (a.type) {
                AccType.CASH -> cash += v
                AccType.WALLET -> wallet += v
                AccType.BANK -> bank += v
                AccType.OTHER -> other += v
            }
        }
        return Totals(cash + wallet + bank + other, cash, wallet, bank, other, debt(txs), receivable(txs))
    }

    fun principal(l: Loan, txs: List<Tx>): Long {
        var s = 0L
        for (t in txs) if (t.loanId == l.id && (t.type == TxType.BORROW || t.type == TxType.LEND)) s += t.amountMinor
        return s
    }

    fun repaid(l: Loan, txs: List<Tx>): Long {
        var s = 0L
        for (t in txs) if (t.loanId == l.id && (t.type == TxType.REPAY_DEBT || t.type == TxType.RECEIVE_BACK)) s += t.amountMinor
        return s
    }

    fun outstanding(l: Loan, txs: List<Tx>): Long = principal(l, txs) - repaid(l, txs)

    /** Income/expense for [from, to). Transfers and all loan movements are deliberately excluded from income/expense. */
    fun summary(txs: List<Tx>, from: Long, to: Long): PeriodSummary {
        var inc = 0L
        var exp = 0L
        var bor = 0L
        var rep = 0L
        var len = 0L
        var rec = 0L
        for (t in txs) {
            if (t.millis < from || t.millis >= to) continue
            when (t.type) {
                TxType.INCOME -> inc += t.amountMinor
                TxType.EXPENSE -> exp += t.amountMinor
                TxType.BORROW -> bor += t.amountMinor
                TxType.REPAY_DEBT -> rep += t.amountMinor
                TxType.LEND -> len += t.amountMinor
                TxType.RECEIVE_BACK -> rec += t.amountMinor
                TxType.TRANSFER -> {}
            }
        }
        return PeriodSummary(inc, exp, bor, rep, len, rec)
    }

    fun expenseByCategory(txs: List<Tx>, from: Long, to: Long): List<Pair<String, Long>> =
        txs.filter { it.type == TxType.EXPENSE && it.millis >= from && it.millis < to }
            .groupBy { it.category.ifBlank { "other" } }
            .map { (k, v) -> k to v.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }

    fun lastDays(n: Int, bn: Boolean): List<Bucket> {
        val today = Fmt.today()
        return (n - 1 downTo 0).map { i ->
            val d = today.minusDays(i.toLong())
            Bucket(Fmt.weekdayShort(d, bn), Fmt.startOfDay(d), Fmt.startOfDay(d.plusDays(1)))
        }
    }

    /** Income list and expense list, one value per bucket. */
    fun series(txs: List<Tx>, buckets: List<Bucket>): Pair<List<Long>, List<Long>> {
        val s = buckets.map { summary(txs, it.from, it.to) }
        return s.map { it.income } to s.map { it.expense }
    }

    fun filter(txs: List<Tx>, loans: List<Loan>, f: TxFilter): List<Tx> {
        val q = f.query.trim().lowercase()
        val persons = loans.associate { it.id to it.person }
        return txs.filter { t ->
            val person = t.loanId?.let { persons[it] } ?: ""
            (f.type == null || t.type == f.type) &&
                (f.accountId == null || t.accountId == f.accountId || t.toAccountId == f.accountId) &&
                (f.from == null || t.millis >= f.from) &&
                (f.toExclusive == null || t.millis < f.toExclusive) &&
                (q.isEmpty() ||
                    t.note.lowercase().contains(q) ||
                    t.source.lowercase().contains(q) ||
                    t.category.lowercase().contains(q) ||
                    person.lowercase().contains(q))
        }
    }

    /** Returns an error key (see Strings.kt) or null if the whole ledger is consistent. */
    fun validate(accounts: List<Account>, loans: List<Loan>, txs: List<Tx>): String? {
        val accIds = accounts.map { it.id }.toSet()
        val loanById = loans.associateBy { it.id }
        for (t in txs) {
            if (t.amountMinor <= 0L) return "err_amount"
            if (t.accountId !in accIds) return "err_account"
            if (t.type == TxType.TRANSFER) {
                val to = t.toAccountId ?: return "err_account"
                if (to !in accIds) return "err_account"
                if (to == t.accountId) return "err_same_account"
            }
            val isLoanTx = t.type == TxType.BORROW || t.type == TxType.REPAY_DEBT ||
                t.type == TxType.LEND || t.type == TxType.RECEIVE_BACK
            if (isLoanTx) {
                val loan = t.loanId?.let { loanById[it] } ?: return "err_loan_missing"
                val kindOk = when (t.type) {
                    TxType.BORROW, TxType.REPAY_DEBT -> loan.kind == LoanKind.BORROWED
                    else -> loan.kind == LoanKind.LENT
                }
                if (!kindOk) return "err_loan_kind"
            }
        }
        for (l in loans) if (outstanding(l, txs) < 0L) return "err_over_repay"
        return null
    }
}
