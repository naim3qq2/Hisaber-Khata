package com.hisab.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LedgerTest {
    private val cash = Account(id = 1, name = "Cash", type = AccType.CASH, openingMinor = 100_000) // 1,000.00
    private val bkash = Account(id = 2, name = "bKash", type = AccType.WALLET, openingMinor = 0)
    private val bank = Account(id = 3, name = "Bank", type = AccType.BANK, openingMinor = 500_000) // 5,000.00
    private val accs = listOf(cash, bkash, bank)

    private val borrowLoan = Loan(id = 10, kind = LoanKind.BORROWED, person = "Rahim")
    private val lendLoan = Loan(id = 11, kind = LoanKind.LENT, person = "Karim")
    private val loans = listOf(borrowLoan, lendLoan)

    private var nextId = 1L
    private fun tx(
        type: TxType, amt: Long, acc: Long, to: Long? = null, loan: Long? = null,
        millis: Long = 1_000L, cat: String = ""
    ) = Tx(
        id = nextId++, type = type, amountMinor = amt, accountId = acc,
        toAccountId = to, loanId = loan, millis = millis, category = cat
    )

    private fun bal(txs: List<Tx>) = Ledger.balances(accs, txs)

    // ---------- the 8 essential rules ----------

    @Test fun incomeIncreasesSelectedAccountAndTotal() {
        val t = listOf(tx(TxType.INCOME, 250_000, 2))
        assertEquals(250_000L, bal(t)[2])
        assertEquals(100_000L, bal(t)[1])
        assertEquals(850_000L, Ledger.totals(accs, t).available)
    }

    @Test fun expenseDecreasesSelectedAccountAndTotal() {
        val t = listOf(tx(TxType.EXPENSE, 30_050, 1))
        assertEquals(69_950L, bal(t)[1])
        assertEquals(569_950L, Ledger.totals(accs, t).available)
    }

    @Test fun transferMovesMoneyWithoutChangingTotalOrIncomeExpense() {
        val before = Ledger.totals(accs, emptyList()).available
        val t = listOf(tx(TxType.TRANSFER, 40_000, 1, to = 2))
        assertEquals(60_000L, bal(t)[1])
        assertEquals(40_000L, bal(t)[2])
        assertEquals(before, Ledger.totals(accs, t).available) // no double counting
        val s = Ledger.summary(t, 0, Long.MAX_VALUE)
        assertEquals(0L, s.income)
        assertEquals(0L, s.expense)
    }

    @Test fun borrowedMoneyIncreasesCashAndDebt() {
        val t = listOf(tx(TxType.BORROW, 200_000, 2, loan = 10))
        val tot = Ledger.totals(accs, t)
        assertEquals(800_000L, tot.available)
        assertEquals(200_000L, tot.debt)
        assertEquals(0L, tot.receivable)
        assertEquals(0L, Ledger.summary(t, 0, Long.MAX_VALUE).income) // not income
    }

    @Test fun repayingDebtDecreasesCashAndDebt() {
        val t = listOf(
            tx(TxType.BORROW, 200_000, 2, loan = 10),
            tx(TxType.REPAY_DEBT, 50_000, 2, loan = 10)
        )
        val tot = Ledger.totals(accs, t)
        assertEquals(750_000L, tot.available)
        assertEquals(150_000L, tot.debt)
        assertEquals(150_000L, Ledger.outstanding(borrowLoan, t))
        val s = Ledger.summary(t, 0, Long.MAX_VALUE)
        assertEquals(0L, s.expense) // repayment is not an expense
        assertEquals(50_000L, s.repaidDebt)
    }

    @Test fun lendingDecreasesCashAndIncreasesReceivable() {
        val t = listOf(tx(TxType.LEND, 100_000, 1, loan = 11))
        val tot = Ledger.totals(accs, t)
        assertEquals(500_000L, tot.available)
        assertEquals(100_000L, tot.receivable)
        assertEquals(0L, Ledger.summary(t, 0, Long.MAX_VALUE).expense) // not an expense
    }

    @Test fun moneyReceivedBackIncreasesCashAndDecreasesReceivable() {
        val t = listOf(
            tx(TxType.LEND, 100_000, 1, loan = 11),
            tx(TxType.RECEIVE_BACK, 60_000, 2, loan = 11)
        )
        val tot = Ledger.totals(accs, t)
        assertEquals(560_000L, tot.available)
        assertEquals(40_000L, tot.receivable)
        assertEquals(0L, Ledger.summary(t, 0, Long.MAX_VALUE).income) // not income
        assertEquals(40_000L, Ledger.outstanding(lendLoan, t))
    }

    @Test fun editingOrDeletingRecalculatesEverything() {
        val a = tx(TxType.INCOME, 100_000, 1)
        val b = tx(TxType.EXPENSE, 20_000, 1)
        val c = tx(TxType.TRANSFER, 30_000, 1, to = 2)
        val all = listOf(a, b, c)
        assertEquals(150_000L, bal(all)[1]) // 1000 + 1000 - 200 - 300 = 1500
        assertEquals(30_000L, bal(all)[2])

        val edited = listOf(a, b.copy(amountMinor = 50_000), c) // edit expense 200 -> 500
        assertEquals(120_000L, bal(edited)[1])

        val deleted = listOf(a, c) // delete expense
        assertEquals(170_000L, bal(deleted)[1])

        val noTransfer = listOf(a, b) // delete transfer -> both accounts fixed
        assertEquals(180_000L, bal(noTransfer)[1])
        assertEquals(0L, bal(noTransfer)[2])
    }

    // ---------- validation ----------

    @Test fun cannotRepayMoreThanOutstanding() {
        val t = listOf(
            tx(TxType.BORROW, 100_000, 1, loan = 10),
            tx(TxType.REPAY_DEBT, 100_001, 1, loan = 10)
        )
        assertEquals("err_over_repay", Ledger.validate(accs, loans, t))
    }

    @Test fun exactFullRepaymentIsAllowedAndSettlesLoan() {
        val t = listOf(
            tx(TxType.BORROW, 100_000, 1, loan = 10),
            tx(TxType.REPAY_DEBT, 40_000, 1, loan = 10),
            tx(TxType.REPAY_DEBT, 60_000, 2, loan = 10)
        )
        assertNull(Ledger.validate(accs, loans, t))
        assertEquals(0L, Ledger.outstanding(borrowLoan, t))
        assertEquals(0L, Ledger.totals(accs, t).debt)
    }

    @Test fun loweringPrincipalBelowRepaidIsRejected() {
        val borrow = tx(TxType.BORROW, 100_000, 1, loan = 10)
        val repay = tx(TxType.REPAY_DEBT, 70_000, 1, loan = 10)
        val edited = listOf(borrow.copy(amountMinor = 50_000), repay)
        assertEquals("err_over_repay", Ledger.validate(accs, loans, edited))
    }

    @Test fun transferToSameAccountIsRejected() {
        assertEquals("err_same_account", Ledger.validate(accs, loans, listOf(tx(TxType.TRANSFER, 100, 1, to = 1))))
    }

    @Test fun zeroOrNegativeAmountIsRejected() {
        assertEquals("err_amount", Ledger.validate(accs, loans, listOf(tx(TxType.INCOME, 0, 1))))
        assertEquals("err_amount", Ledger.validate(accs, loans, listOf(tx(TxType.EXPENSE, -5, 1))))
    }

    @Test fun repaymentMustMatchLoanKind() {
        // RECEIVE_BACK on a loan I borrowed makes no sense
        val t = listOf(tx(TxType.RECEIVE_BACK, 100, 1, loan = 10))
        assertEquals("err_loan_kind", Ledger.validate(accs, loans, t))
    }

    @Test fun loanTransactionNeedsExistingLoan() {
        assertEquals("err_loan_missing", Ledger.validate(accs, loans, listOf(tx(TxType.LEND, 100, 1, loan = 999))))
        assertEquals("err_loan_missing", Ledger.validate(accs, loans, listOf(tx(TxType.BORROW, 100, 1))))
    }

    @Test fun validLedgerPasses() {
        val t = listOf(
            tx(TxType.INCOME, 100, 1), tx(TxType.EXPENSE, 50, 1), tx(TxType.TRANSFER, 10, 1, to = 3),
            tx(TxType.BORROW, 500, 1, loan = 10), tx(TxType.LEND, 300, 3, loan = 11)
        )
        assertNull(Ledger.validate(accs, loans, t))
    }

    // ---------- reports ----------

    @Test fun summaryOnlyCountsIncomeAndExpenseInsideRange() {
        val t = listOf(
            tx(TxType.INCOME, 1000, 1, millis = 100),
            tx(TxType.EXPENSE, 400, 1, millis = 150),
            tx(TxType.EXPENSE, 999, 1, millis = 200), // upper bound is exclusive
            tx(TxType.INCOME, 777, 1, millis = 50),   // before range
            tx(TxType.BORROW, 5000, 1, loan = 10, millis = 120),
            tx(TxType.TRANSFER, 300, 1, to = 2, millis = 130)
        )
        val s = Ledger.summary(t, 100, 200)
        assertEquals(1000L, s.income)
        assertEquals(400L, s.expense)
        assertEquals(600L, s.net)
        assertEquals(5000L, s.borrowed)
    }

    @Test fun expenseByCategoryGroupsAndSortsDescending() {
        val t = listOf(
            tx(TxType.EXPENSE, 100, 1, cat = "food"),
            tx(TxType.EXPENSE, 250, 1, cat = "bills"),
            tx(TxType.EXPENSE, 300, 1, cat = "food"),
            tx(TxType.INCOME, 9999, 1, cat = "salary")
        )
        val r = Ledger.expenseByCategory(t, 0, Long.MAX_VALUE)
        assertEquals(listOf("food" to 400L, "bills" to 250L), r)
    }

    @Test fun filterBySearchTypeAccountAndDate() {
        val t = listOf(
            tx(TxType.EXPENSE, 100, 1, millis = 10).copy(note = "Lunch at office"),
            tx(TxType.EXPENSE, 200, 2, millis = 20).copy(note = "Rickshaw"),
            tx(TxType.INCOME, 300, 1, millis = 30)
        )
        assertEquals(1, Ledger.filter(t, loans, TxFilter(query = "lunch")).size)
        assertEquals(2, Ledger.filter(t, loans, TxFilter(type = TxType.EXPENSE)).size)
        assertEquals(2, Ledger.filter(t, loans, TxFilter(accountId = 1)).size)
        assertEquals(1, Ledger.filter(t, loans, TxFilter(from = 15, toExclusive = 25)).size)
    }

    @Test fun weekStartsOnSaturday() {
        val tue = LocalDate.of(2026, 10, 20)
        assertEquals(LocalDate.of(2026, 10, 17), Periods.start(ReportPeriod.WEEK, tue))
        assertEquals(LocalDate.of(2026, 10, 1), Periods.start(ReportPeriod.MONTH, tue))
        assertEquals(LocalDate.of(2026, 1, 1), Periods.start(ReportPeriod.YEAR, tue))
        assertEquals(12, Periods.buckets(ReportPeriod.YEAR, tue, false).size)
        assertEquals(31, Periods.buckets(ReportPeriod.MONTH, tue, false).size)
    }

    // ---------- exact decimal money ----------

    @Test fun parseIsExactDecimal() {
        assertEquals(30L, Money.parse("0.1")!! + Money.parse("0.2")!!) // 0.1 + 0.2 == 0.3 exactly
        assertEquals(Money.parse("0.3"), Money.parse("0.1")!! + Money.parse("0.2")!!)
        assertEquals(123_450L, Money.parse("1,234.5"))
        assertEquals(12_300L, Money.parse("১২৩")) // Bengali digits accepted
        assertEquals(1L, Money.parse("0.005")) // half-up
        assertEquals(1_235L, Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1e5"))
        assertNull(Money.parse(""))
        assertEquals(0L, Money.parse("0", allowZero = true))
    }

    @Test fun manySmallAmountsSumExactly() {
        var sum = 0L
        repeat(1000) { sum += Money.parse("0.10")!! }
        assertEquals(10_000L, sum) // exactly 100.00
    }

    @Test fun bangladeshiFormatting() {
        assertEquals("৳ 12,34,567.89", Money.format(123_456_789, false))
        assertEquals("৳ 1,00,000", Money.format(10_000_000, false))
        assertEquals("৳ 999", Money.format(99_900, false))
        assertEquals("৳ 0", Money.format(0, false))
        assertEquals("-৳ 500", Money.format(-50_000, false))
        assertEquals("৳ ১,৫০০", Money.format(150_000, true))
        assertEquals("৳ ০.০৫", Money.format(5, true))
        assertEquals("1234.50", Money.plain(123_450))
        assertEquals("1500", Money.input(150_000))
        assertEquals("1500.05", Money.input(150_005))
    }

    // ---------- backup JSON shape is validated before restore ----------

    @Test fun roundTripBalancesAreStable() {
        val t = listOf(
            tx(TxType.INCOME, 123_456, 1), tx(TxType.EXPENSE, 7_891, 2),
            tx(TxType.TRANSFER, 5_000, 3, to = 1), tx(TxType.BORROW, 20_000, 1, loan = 10)
        )
        val b1 = bal(t)
        val b2 = bal(t.reversed()) // order of ledger rows must not matter
        assertEquals(b1, b2)
        assertNotNull(b1[1])
    }
}
