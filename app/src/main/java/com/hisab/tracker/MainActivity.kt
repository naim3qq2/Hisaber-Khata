@file:OptIn(ExperimentalMaterial3Api::class)

package com.hisab.tracker

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HisabApp(vm) }
    }

    override fun onStop() {
        super.onStop()
        vm.lockIfNeeded()
    }
}

private fun writeText(ctx: Context, uri: Uri, text: String): Boolean = try {
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
} catch (e: Exception) {
    false
}

private fun readText(ctx: Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
} catch (e: Exception) {
    null
}

@Composable
fun HisabApp(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by vm.data.collectAsState()
    val dark = when (vm.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    var csvTx by remember { mutableStateOf<List<Tx>>(emptyList()) }
    var restoreText by remember { mutableStateOf<String?>(null) }

    // Launchers live here (always composed) so results survive the PIN lock screen.
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = vm.exportJson()
                val ok = withContext(Dispatchers.IO) { writeText(ctx, uri, json) }
                vm.toast(if (ok) "backup_saved" else "err_io")
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = Csv.build(csvTx, data.accounts, data.loans)
                val ok = withContext(Dispatchers.IO) { writeText(ctx, uri, text) }
                vm.toast(if (ok) "exported" else "err_io")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(ctx, uri) }
                if (text == null) vm.toast("err_io") else restoreText = text
            }
        }
    }

    val activity = ctx as? Activity
    LaunchedEffect(vm.secure) {
        val w = activity?.window
        if (w != null) {
            if (vm.secure) w.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    CompositionLocalProvider(LocalBn provides vm.bn) {
        HisabTheme(dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    vm.locked -> PinScreen(vm)
                    !data.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> MainShell(
                        vm, data,
                        exportCsv = { list ->
                            csvTx = list
                            vm.skipNextLock = true
                            csvLauncher.launch("hisab-transactions.csv")
                        },
                        backup = {
                            vm.skipNextLock = true
                            backupLauncher.launch("hisab-backup.json")
                        },
                        restore = {
                            vm.skipNextLock = true
                            restoreLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }

            val rt = restoreText
            if (rt != null) {
                AlertDialog(
                    onDismissRequest = { restoreText = null },
                    title = { Text(tr("restore")) },
                    text = { Text(tr("restore_warn")) },
                    confirmButton = {
                        TextButton(onClick = { vm.restore(rt); restoreText = null }) { Text(tr("restore")) }
                    },
                    dismissButton = { TextButton(onClick = { restoreText = null }) { Text(tr("cancel")) } }
                )
            }
        }
    }
}

@Composable
fun MainShell(
    vm: AppViewModel,
    data: AppData,
    exportCsv: (List<Tx>) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit
) {
    val req = vm.form
    BackHandler(enabled = req != null) { vm.closeForm() }

    if (req != null) {
        key(req) {
            when (req) {
                is FormReq.TxF -> TxFormScreen(vm, data, req)
                is FormReq.AccountF -> AccountFormScreen(vm, req)
                is FormReq.LoanDetail -> LoanDetailScreen(vm, data, req)
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val items = listOf(
                        "🏠" to "nav_home", "🧾" to "nav_history", "🤝" to "nav_loans",
                        "📊" to "nav_reports", "⚙️" to "nav_settings"
                    )
                    items.forEachIndexed { i, (emoji, key) ->
                        NavigationBarItem(
                            selected = vm.tab == i,
                            onClick = { vm.tab = i },
                            icon = { Text(emoji) },
                            label = { Text(tr(key), maxLines = 1) }
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (vm.tab) {
                    0 -> Dashboard(vm, data)
                    1 -> HistoryScreen(vm, data, exportCsv)
                    2 -> LoansScreen(vm, data)
                    3 -> ReportsScreen(data)
                    else -> SettingsScreen(vm, data, backup, restore) { exportCsv(data.txs) }
                }
            }
        }
    }
}

@Composable
fun PinScreen(vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 48.sp)
        Text(tr("app_name"), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c in '0'..'9' }) pin = it },
            label = { Text(tr("pin")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        Spacer(Modifier.size(12.dp))
        Button(onClick = {
            if (!vm.unlock(pin)) {
                vm.toast("wrong_pin")
                pin = ""
            }
        }) { Text(tr("unlock")) }
    }
}
