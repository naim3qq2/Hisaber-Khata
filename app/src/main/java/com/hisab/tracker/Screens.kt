@file:OptIn(ExperimentalMaterial3Api::class)

package com.hisab.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate

// =====================================================================================
// Dashboard
// =====================================================================================

@Composable
fun Dashboard(vm: AppViewModel, data: AppData) {
    val bn = LocalBn.current
    val tot = remember(data) { Ledger.totals(data.accounts, data.txs) }
    val bal = remember(data) { Ledger.balances(data.accounts, data.txs) }
    val today = Fmt.today()
    val dayR = Periods.range(ReportPeriod.DAY, today)
    val monR = Periods.range(ReportPeriod.MONTH, today)
    val td = remember(data) { Ledger.summary(data.txs, dayR.first, dayR.second) }
    val mo = remember(data) { Ledger.summary(data.txs, monR.first, monR.second) }
    val days = remember(bn) { Ledger.lastDays(7, bn) }
    val ser = remember(data, days) { Ledger.series(data.txs, days) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text(tr("app_name"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(tr("total_balance"), style = MaterialTheme.typography.labelLarge)
                    Text(fm(tot.available), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Text(tr("net_position") + ": " + fm(tot.netPosition), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("💵 " + tr("cash"), fm(tot.cash), Modifier.weight(1f))
                StatCard("📱 " + tr("wallets"), fm(tot.wallet), Modifier.weight(1f))
                StatCard("🏦 " + tr("bank"), fm(tot.bank), Modifier.weight(1f))
            }
        }
        if (tot.other != 0L) {
            item { StatCard("👛 " + tr("other_acc"), fm(tot.other), Modifier.fillMaxWidth()) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(tr("debt"), fm(tot.debt), Modifier.weight(1f), Red)
                StatCard(tr("receivable"), fm(tot.receivable), Modifier.weight(1f), Green)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(tr("quick_add"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickBtn("⬇️ " + tr("income"), Modifier.weight(1f)) { vm.form = FormReq.TxF(TxType.INCOME) }
                    QuickBtn("⬆️ " + tr("expense"), Modifier.weight(1f)) { vm.form = FormReq.TxF(TxType.EXPENSE) }
                    QuickBtn("🔁 " + tr("transfer"), Modifier.weight(1f)) { vm.form = FormReq.TxF(TxType.TRANSFER) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickBtn("🤝 " + tr("borrow"), Modifier.weight(1f)) { vm.form = FormReq.TxF(TxType.BORROW) }
                    QuickBtn("💸 " + tr("lend"), Modifier.weight(1f)) { vm.form = FormReq.TxF(TxType.LEND) }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PeriodCard(tr("today"), td.income, td.expense, Modifier.weight(1f))
                PeriodCard(tr("this_month"), mo.income, mo.expense, Modifier.weight(1f))
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(tr("last7"))
                    BarChart(ser.first, ser.second, days.map { it.label })
                    Legend()
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionTitle(tr("accounts")) }
                        TextButton(onClick = { vm.form = FormReq.AccountF() }) { Text("＋ " + tr("add_account")) }
                    }
                    data.accounts.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.form = FormReq.AccountF(a) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(accEmoji(a.type) + "  " + a.name, Modifier.weight(1f))
                            val b = bal[a.id] ?: 0L
                            Text(fm(b), fontWeight = FontWeight.SemiBold, color = if (b < 0) Red else Color.Unspecified)
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionTitle(tr("recent")) }
                TextButton(onClick = { vm.tab = 1 }) { Text(tr("view_all")) }
            }
        }
        if (data.txs.isEmpty()) {
            item { EmptyText(tr("no_data")) }
        } else {
            items(data.txs.take(8)) { t ->
                TxRow(t, data, onClick = { vm.form = FormReq.TxF(t.type, t) })
            }
        }
    }
}

@Composable
private fun QuickBtn(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun PeriodCard(title: String, income: Long, expense: Long, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(4.dp))
            Text(tr("income"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fm(income), color = Green, fontWeight = FontWeight.Bold)
            Text(tr("expense"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fm(expense), color = Red, fontWeight = FontWeight.Bold)
        }
    }
}

// =====================================================================================
// History
// =====================================================================================

@Composable
fun HistoryScreen(vm: AppViewModel, data: AppData, onExport: (List<Tx>) -> Unit) {
    val ctx = LocalContext.current
    val bn = LocalBn.current
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<TxType?>(null) }
    var acc by remember { mutableStateOf<Long?>(null) }
    var fromDate by remember { mutableStateOf<LocalDate?>(null) }
    var toDate by remember { mutableStateOf<LocalDate?>(null) }
    var confirm by remember { mutableStateOf<Tx?>(null) }

    val filter = TxFilter(
        query = query,
        from = fromDate?.let { Fmt.startOfDay(it) },
        toExclusive = toDate?.let { Fmt.startOfDay(it.plusDays(1)) },
        accountId = acc,
        type = type
    )
    val list = remember(data, query, type, acc, fromDate, toDate) { Ledger.filter(data.txs, data.loans, filter) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(tr("nav_history"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { onExport(list) }) { Text("📤 " + tr("export_csv")) }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("🔍 " + tr("search")) }
            )
        }
        item {
            ChipRow(
                items = listOf<TxType?>(null) + TxType.values().toList(),
                selected = { it == type },
                label = { if (it == null) Str.get("all", bn) else Str.get("tx_" + it.name, bn) },
                onClick = { type = it }
            )
        }
        item {
            ChipRow(
                items = listOf<Long?>(null) + data.accounts.map { it.id },
                selected = { it == acc },
                label = { id -> if (id == null) Str.get("all_accounts", bn) else data.accounts.firstOrNull { it.id == id }?.name ?: "?" },
                onClick = { acc = it }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { pickDate(ctx, fromDate ?: Fmt.today()) { fromDate = it } },
                    modifier = Modifier.weight(1f)
                ) { Text(tr("from") + ": " + (fromDate?.let { Fmt.date(Fmt.startOfDay(it), bn) } ?: "—"), maxLines = 1) }
                OutlinedButton(
                    onClick = { pickDate(ctx, toDate ?: Fmt.today()) { toDate = it } },
                    modifier = Modifier.weight(1f)
                ) { Text(tr("to") + ": " + (toDate?.let { Fmt.date(Fmt.startOfDay(it), bn) } ?: "—"), maxLines = 1) }
                if (fromDate != null || toDate != null) {
                    TextButton(onClick = { fromDate = null; toDate = null }) { Text("✕") }
                }
            }
        }
        item {
            Text(
                Fmt.digits(list.size.toString(), bn) + " " + tr("transactions"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (list.isEmpty()) {
            item { EmptyText(tr("no_data")) }
        } else {
            items(list, key = { it.id }) { t ->
                TxRow(
                    t, data,
                    onClick = { vm.form = FormReq.TxF(t.type, t) },
                    onDelete = { confirm = t }
                )
            }
        }
    }

    val c = confirm
    if (c != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(tr("confirm_delete")) },
            text = { Text(tr("delete_tx_msg")) },
            confirmButton = {
                TextButton(onClick = { vm.deleteTx(c.id); confirm = null }) { Text(tr("delete"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(tr("cancel")) } }
        )
    }
}

// =====================================================================================
// Loans
// =====================================================================================

@Composable
fun LoansScreen(vm: AppViewModel, data: AppData) {
    val bn = LocalBn.current
    var kind by remember { mutableStateOf(LoanKind.BORROWED) }
    val tot = remember(data) { Ledger.totals(data.accounts, data.txs) }
    val list = data.loans
        .filter { it.kind == kind }
        .sortedByDescending { Ledger.outstanding(it, data.txs) > 0L }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text(tr("nav_loans"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(tr("debt"), fm(tot.debt), Modifier.weight(1f), Red)
                StatCard(tr("receivable"), fm(tot.receivable), Modifier.weight(1f), Green)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.form = FormReq.TxF(TxType.BORROW) }, modifier = Modifier.weight(1f)) {
                    Text("🤝 ＋ " + tr("borrow"), maxLines = 1)
                }
                FilledTonalButton(onClick = { vm.form = FormReq.TxF(TxType.LEND) }, modifier = Modifier.weight(1f)) {
                    Text("💸 ＋ " + tr("lend"), maxLines = 1)
                }
            }
        }
        item {
            ChipRow(
                items = listOf(LoanKind.BORROWED, LoanKind.LENT),
                selected = { it == kind },
                label = { Str.get(if (it == LoanKind.BORROWED) "borrowed_tab" else "lent_tab", bn) },
                onClick = { kind = it }
            )
        }
        if (list.isEmpty()) item { EmptyText(tr("no_data")) }
        items(list, key = { it.id }) { loan ->
            val p = Ledger.principal(loan, data.txs)
            val r = Ledger.repaid(loan, data.txs)
            val out = p - r
            val overdue = out > 0L && loan.dueMillis != null && loan.dueMillis < System.currentTimeMillis()
            Card(Modifier.fillMaxWidth().clickable { vm.form = FormReq.LoanDetail(loan.id) }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(loan.person, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(fm(out), fontWeight = FontWeight.Bold, color = if (out == 0L) Green else if (loan.kind == LoanKind.BORROWED) Red else Green)
                    }
                    Text(
                        tr("principal") + ": " + fm(p) + "  ·  " + tr("repaid") + ": " + fm(r),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ProgressBar(if (p > 0L) r.toFloat() / p.toFloat() else 0f)
                    if (out == 0L) {
                        Text("✅ " + tr("settled"), style = MaterialTheme.typography.labelMedium, color = Green)
                    } else if (loan.dueMillis != null) {
                        Text(
                            "📅 " + tr("due_date") + ": " + Fmt.date(loan.dueMillis, bn) + (if (overdue) "  ⚠ " + tr("overdue") else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (overdue) Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================================
// Reports
// =====================================================================================

@Composable
fun ReportsScreen(data: AppData) {
    val bn = LocalBn.current
    var period by remember { mutableStateOf(ReportPeriod.MONTH) }
    var anchor by remember { mutableStateOf(Fmt.today()) }
    val range = Periods.range(period, anchor)
    val sum = remember(data, period, anchor) { Ledger.summary(data.txs, range.first, range.second) }
    val buckets = remember(period, anchor, bn) { Periods.buckets(period, anchor, bn) }
    val ser = remember(data, buckets) { Ledger.series(data.txs, buckets) }
    val cats = remember(data, period, anchor) { Ledger.expenseByCategory(data.txs, range.first, range.second) }
    val bal = remember(data) { Ledger.balances(data.accounts, data.txs) }
    val tot = remember(data) { Ledger.totals(data.accounts, data.txs) }
    val catTotal = cats.sumOf { it.second }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("nav_reports"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        ChipRow(
            items = ReportPeriod.values().toList(),
            selected = { it == period },
            label = { Str.get("period_" + it.name, bn) },
            onClick = { period = it }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { anchor = Periods.shift(period, anchor, -1) }) { Text("◀") }
            Text(
                Periods.label(period, anchor, bn),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { anchor = Periods.shift(period, anchor, 1) }) { Text("▶") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(tr("total_income"), fm(sum.income), Modifier.weight(1f), Green)
            StatCard(tr("total_expense"), fm(sum.expense), Modifier.weight(1f), Red)
        }
        StatCard(tr("net_cashflow"), fm(sum.net), Modifier.fillMaxWidth(), if (sum.net < 0) Red else Green)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarChart(ser.first, ser.second, buckets.map { it.label })
                Legend()
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(tr("expense_by_category"))
                if (cats.isEmpty()) {
                    EmptyText(tr("no_data"))
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PieChart(cats.map { it.second }) }
                    cats.forEachIndexed { i, (key, v) ->
                        val pm = v * 1000 / catTotal // permille, integer math
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Palette[i % Palette.size]))
                            Spacer(Modifier.width(8.dp))
                            Text(catLabel(key, bn), Modifier.weight(1f))
                            Text(Fmt.digits("${pm / 10}.${pm % 10}%", bn), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(fm(v), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle(tr("account_balances"))
                data.accounts.forEach { a ->
                    Row {
                        Text(accEmoji(a.type) + "  " + a.name, Modifier.weight(1f))
                        Text(fm(bal[a.id] ?: 0L), fontWeight = FontWeight.SemiBold)
                    }
                }
                Row {
                    Text(tr("total_balance"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(fm(tot.available), fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionTitle(tr("loan_summary"))
                LoanLine(tr("borrowed_in"), sum.borrowed)
                LoanLine(tr("debt_repaid"), sum.repaidDebt)
                LoanLine(tr("lent_out"), sum.lent)
                LoanLine(tr("received_back"), sum.receivedBack)
                LoanLine(tr("debt") + " (" + tr("now") + ")", tot.debt)
                LoanLine(tr("receivable") + " (" + tr("now") + ")", tot.receivable)
                Text(
                    tr("loan_not_income"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LoanLine(label: String, value: Long) {
    Row {
        Text(label, Modifier.weight(1f))
        Text(fm(value), fontWeight = FontWeight.SemiBold)
    }
}

// =====================================================================================
// Settings
// =====================================================================================

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    data: AppData,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportAll: () -> Unit
) {
    val bn = LocalBn.current
    var showPin by remember { mutableStateOf(false) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(tr("nav_settings"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        SectionTitle(tr("language"))
        ChipRow(
            items = listOf(false, true),
            selected = { it == bn },
            label = { if (it) "বাংলা" else "English" },
            onClick = { vm.setLang(it) }
        )

        SectionTitle(tr("theme"))
        ChipRow(
            items = listOf("system", "light", "dark"),
            selected = { it == vm.theme },
            label = { Str.get("theme_$it", bn) },
            onClick = { vm.changeTheme(it) }
        )

        SectionTitle(tr("security"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("app_lock") + ": " + (if (vm.hasPin) tr("on") else tr("off")), Modifier.weight(1f))
            if (vm.hasPin) {
                OutlinedButton(onClick = { vm.removePin() }) { Text(tr("remove_pin")) }
            } else {
                Button(onClick = { pin1 = ""; pin2 = ""; showPin = true }) { Text(tr("set_pin")) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("secure_screen"), Modifier.weight(1f))
            Switch(checked = vm.secure, onCheckedChange = { vm.changeSecure(it) })
        }

        SectionTitle(tr("data"))
        Button(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text("💾 " + tr("backup")) }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("📂 " + tr("restore")) }
        OutlinedButton(onClick = onExportAll, modifier = Modifier.fillMaxWidth()) { Text("📤 " + tr("export_all")) }
        Text(tr("backup_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle(tr("custom_categories"))
        if (data.categories.isEmpty()) {
            Text(tr("no_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        data.categories.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name + "  (" + tr(if (c.forIncome) "income" else "expense") + ")",
                    Modifier.weight(1f)
                )
                TextButton(onClick = { vm.deleteCategory(c.id) }) { Text("🗑") }
            }
        }

        SectionTitle(tr("about"))
        Text(tr("about_text"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showPin) {
        val valid = pin1.length in 4..8 && pin1 == pin2
        AlertDialog(
            onDismissRequest = { showPin = false },
            title = { Text(tr("set_pin")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin1,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c in '0'..'9' }) pin1 = it },
                        label = { Text(tr("pin")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c in '0'..'9' }) pin2 = it },
                        label = { Text(tr("confirm_pin")) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    Text(tr("pin_hint"), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = { vm.setPin(pin1); showPin = false }
                ) { Text(tr("save")) }
            },
            dismissButton = { TextButton(onClick = { showPin = false }) { Text(tr("cancel")) } }
        )
    }
}
