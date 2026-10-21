@file:OptIn(ExperimentalMaterial3Api::class)

package com.hisab.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val NEW_CAT = "__new__"

@Composable
private fun FormHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// =====================================================================================
// Income / Expense / Transfer / Borrow / Lend / Repayment form
// =====================================================================================

@Composable
fun TxFormScreen(vm: AppViewModel, data: AppData, req: FormReq.TxF) {
    val ctx = LocalContext.current
    val bn = LocalBn.current
    val type = req.type
    val edit = req.edit
    val isLoanStart = type == TxType.BORROW || type == TxType.LEND
    val isLoanPay = type == TxType.REPAY_DEBT || type == TxType.RECEIVE_BACK
    val hasCategory = type == TxType.INCOME || type == TxType.EXPENSE
    val bal = remember(data) { Ledger.balances(data.accounts, data.txs) }
    val existingLoan = data.loans.firstOrNull { it.id == (edit?.loanId ?: req.loanId) }

    var amount by remember { mutableStateOf(if (edit != null) Money.input(edit.amountMinor) else "") }
    var accId by remember { mutableStateOf<Long?>(edit?.accountId ?: data.accounts.firstOrNull()?.id) }
    var toId by remember { mutableStateOf<Long?>(edit?.toAccountId ?: data.accounts.getOrNull(1)?.id) }
    val defaultCat = (if (type == TxType.INCOME) Cats.income else Cats.expense).first()
    var cat by remember {
        mutableStateOf(edit?.category?.takeIf { it.isNotBlank() } ?: defaultCat)
    }
    var source by remember { mutableStateOf(edit?.source ?: "") }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var millis by remember { mutableStateOf(edit?.millis ?: System.currentTimeMillis()) }
    var person by remember { mutableStateOf(existingLoan?.person ?: "") }
    var due by remember { mutableStateOf<Long?>(existingLoan?.dueMillis) }
    var loanId by remember { mutableStateOf<Long?>(edit?.loanId ?: req.loanId) }
    var showNewCat by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun accLabel(id: Long): String {
        val a = data.accounts.firstOrNull { it.id == id } ?: return "?"
        return accEmoji(a.type) + " " + a.name + " · " + Money.format(bal[id] ?: 0L, bn)
    }

    fun save() {
        val amt = Money.parse(amount)
        if (amt == null) { vm.toast("err_amount"); return }
        val a = accId
        if (a == null) { vm.toast("err_account"); return }
        val base = Tx(
            id = edit?.id ?: 0L, type = type, amountMinor = amt, accountId = a,
            millis = millis, note = note.trim()
        )
        when {
            type == TxType.INCOME -> vm.saveTx(base.copy(category = cat, source = source.trim())) { vm.closeForm() }
            type == TxType.EXPENSE -> vm.saveTx(base.copy(category = cat)) { vm.closeForm() }
            type == TxType.TRANSFER -> vm.saveTx(base.copy(toAccountId = toId)) { vm.closeForm() }
            isLoanStart -> {
                if (person.isBlank()) { vm.toast("err_person"); return }
                val loan = Loan(
                    id = existingLoan?.id ?: 0L,
                    kind = if (type == TxType.BORROW) LoanKind.BORROWED else LoanKind.LENT,
                    person = person.trim(),
                    dueMillis = due
                )
                vm.saveLoan(loan, base) { vm.closeForm() }
            }
            else -> {
                val l = loanId
                if (l == null) { vm.toast("err_loan"); return }
                vm.saveTx(base.copy(loanId = l)) { vm.closeForm() }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormHeader(
            if (edit == null) tr("form_" + type.name) else tr("edit") + " · " + tr("tx_" + type.name)
        ) { vm.closeForm() }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("amount") + " (৳)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        if (type == TxType.TRANSFER) {
            SectionTitle(tr("from_account"))
            ChipRow(data.accounts.map { it.id }, { it == accId }, { accLabel(it) }) { accId = it }
            SectionTitle(tr("to_account"))
            ChipRow(data.accounts.map { it.id }, { it == toId }, { accLabel(it) }) { toId = it }
        } else {
            val incoming = type == TxType.INCOME || type == TxType.BORROW || type == TxType.RECEIVE_BACK
            SectionTitle(tr(if (incoming) "receive_into" else "pay_from"))
            ChipRow(data.accounts.map { it.id }, { it == accId }, { accLabel(it) }) { accId = it }
        }

        if (hasCategory) {
            SectionTitle(tr("category"))
            val customs = data.categories.filter { it.forIncome == (type == TxType.INCOME) }.map { it.name }
            val keys = (if (type == TxType.INCOME) Cats.income else Cats.expense) + customs + NEW_CAT
            ChipRow(
                items = keys,
                selected = { it == cat },
                label = { if (it == NEW_CAT) "＋ " + Str.get("new_category", bn) else catLabel(it, bn) },
                onClick = { if (it == NEW_CAT) showNewCat = true else cat = it }
            )
        }

        if (type == TxType.INCOME) {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr("source")) }
            )
        }

        if (isLoanStart) {
            OutlinedTextField(
                value = person,
                onValueChange = { person = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr(if (type == TxType.BORROW) "person_borrowed" else "person_lent")) }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pickDate(ctx, Fmt.toLocalDate(due ?: System.currentTimeMillis())) { due = Fmt.startOfDay(it) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📅 " + tr("due_date") + ": " + (due?.let { Fmt.date(it, bn) } ?: tr("no_due")), maxLines = 1) }
                if (due != null) TextButton(onClick = { due = null }) { Text("✕") }
            }
        }

        if (isLoanPay) {
            SectionTitle(tr("select_loan"))
            val wanted = if (type == TxType.REPAY_DEBT) LoanKind.BORROWED else LoanKind.LENT
            val open = data.loans.filter {
                it.kind == wanted && (Ledger.outstanding(it, data.txs) > 0L || it.id == loanId)
            }
            if (open.isEmpty()) {
                Text(tr("no_open_loans"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ChipRow(
                    items = open.map { it.id },
                    selected = { it == loanId },
                    label = { id ->
                        val l = data.loans.first { it.id == id }
                        l.person + " · " + Money.format(Ledger.outstanding(l, data.txs), bn)
                    },
                    onClick = { loanId = it }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { pickDate(ctx, Fmt.toLocalDate(millis)) { millis = Fmt.withDate(millis, it) } },
                modifier = Modifier.weight(1f)
            ) { Text("📅 " + Fmt.date(millis, bn), maxLines = 1) }
            OutlinedButton(
                onClick = { pickTime(ctx, Fmt.hour(millis), Fmt.minute(millis)) { h, m -> millis = Fmt.withTime(millis, h, m) } },
                modifier = Modifier.weight(1f)
            ) { Text("🕒 " + Fmt.time(millis, bn), maxLines = 1) }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("note")) }
        )

        Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text(tr("save")) }
        if (edit != null) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("delete"), color = Red)
            }
        }
        Spacer(Modifier.size(24.dp))
    }

    if (showNewCat) {
        AlertDialog(
            onDismissRequest = { showNewCat = false },
            title = { Text(tr("new_category")) },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    singleLine = true,
                    label = { Text(tr("name")) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = newCatName.trim()
                    if (n.isNotEmpty()) {
                        vm.addCategory(n, type == TxType.INCOME)
                        cat = n
                    }
                    newCatName = ""
                    showNewCat = false
                }) { Text(tr("add")) }
            },
            dismissButton = { TextButton(onClick = { showNewCat = false }) { Text(tr("cancel")) } }
        )
    }

    if (confirmDelete && edit != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("confirm_delete")) },
            text = { Text(tr("delete_tx_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteTx(edit.id) { vm.closeForm() }
                }) { Text(tr("delete"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("cancel")) } }
        )
    }
}

// =====================================================================================
// Account form
// =====================================================================================

@Composable
fun AccountFormScreen(vm: AppViewModel, req: FormReq.AccountF) {
    val bn = LocalBn.current
    val edit = req.edit
    var name by remember { mutableStateOf(edit?.name ?: "") }
    var type by remember { mutableStateOf(edit?.type ?: AccType.CASH) }
    var opening by remember { mutableStateOf(if (edit != null) Money.input(edit.openingMinor) else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormHeader(tr(if (edit == null) "add_account" else "edit_account")) { vm.closeForm() }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("name")) }
        )
        SectionTitle(tr("acc_type"))
        ChipRow(
            items = AccType.values().toList(),
            selected = { it == type },
            label = { accEmoji(it) + " " + Str.get("type_" + it.name, bn) },
            onClick = { type = it }
        )
        OutlinedTextField(
            value = opening,
            onValueChange = { opening = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr("opening_balance") + " (৳)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(
            onClick = {
                val minor = if (opening.isBlank()) 0L else Money.parse(opening, allowZero = true)
                if (minor == null) {
                    vm.toast("err_amount")
                } else {
                    vm.saveAccount(Account(edit?.id ?: 0L, name.trim(), type, minor)) { vm.closeForm() }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(tr("save")) }
        if (edit != null) {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("delete"), color = Red)
            }
        }
    }

    if (confirmDelete && edit != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("confirm_delete")) },
            text = { Text(tr("delete_account_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAccount(edit.id) { vm.closeForm() }
                }) { Text(tr("delete"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("cancel")) } }
        )
    }
}

// =====================================================================================
// Loan detail
// =====================================================================================

@Composable
fun LoanDetailScreen(vm: AppViewModel, data: AppData, req: FormReq.LoanDetail) {
    val loan = data.loans.firstOrNull { it.id == req.id }
    if (loan == null) {
        LaunchedEffect(req.id) { vm.form = null }
    } else {
        LoanDetailBody(vm, data, loan)
    }
}

@Composable
private fun LoanDetailBody(vm: AppViewModel, data: AppData, loan: Loan) {
    val bn = LocalBn.current
    var confirmDelete by remember { mutableStateOf(false) }
    val p = Ledger.principal(loan, data.txs)
    val r = Ledger.repaid(loan, data.txs)
    val out = p - r
    val borrowed = loan.kind == LoanKind.BORROWED
    val startTx = data.txs.firstOrNull {
        it.loanId == loan.id && (it.type == TxType.BORROW || it.type == TxType.LEND)
    }
    val payType = if (borrowed) TxType.REPAY_DEBT else TxType.RECEIVE_BACK
    val history = data.txs.filter { it.loanId == loan.id }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormHeader(loan.person) { vm.closeForm() }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr(if (borrowed) "borrowed_tab" else "lent_tab"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(tr("outstanding") + ": " + fm(out), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (out == 0L) Green else if (borrowed) Red else Green)
                Text(tr("principal") + ": " + fm(p))
                Text(tr("repaid") + ": " + fm(r))
                ProgressBar(if (p > 0L) r.toFloat() / p.toFloat() else 0f)
                val d = loan.dueMillis
                if (d != null) Text("📅 " + tr("due_date") + ": " + Fmt.date(d, bn))
            }
        }
        if (out > 0L) {
            Button(
                onClick = { vm.form = FormReq.TxF(payType, null, loan.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(tr(if (borrowed) "repay_debt" else "receive_back")) }
        }
        if (startTx != null) {
            FilledTonalButton(
                onClick = { vm.form = FormReq.TxF(startTx.type, startTx, loan.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("✏️ " + tr("edit_loan")) }
        }
        SectionTitle(tr("repayment_history"))
        history.forEach { t ->
            TxRow(t, data, onClick = { vm.form = FormReq.TxF(t.type, t, loan.id) })
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("delete_loan"), color = Red)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("confirm_delete")) },
            text = { Text(tr("delete_loan_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteLoan(loan.id) { vm.form = null }
                }) { Text(tr("delete"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("cancel")) } }
        )
    }
}
