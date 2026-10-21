@file:OptIn(ExperimentalMaterial3Api::class)

package com.hisab.tracker

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

val LocalBn = staticCompositionLocalOf { false }

@Composable
fun tr(key: String): String = Str.get(key, LocalBn.current)

@Composable
fun fm(minor: Long): String = Money.format(minor, LocalBn.current)

val Green = Color(0xFF16A34A)
val Red = Color(0xFFDC2626)
val Blue = Color(0xFF2563EB)
val Palette = listOf(
    Color(0xFF0F766E), Color(0xFFF59E0B), Color(0xFF3B82F6), Color(0xFFEF4444),
    Color(0xFF8B5CF6), Color(0xFF10B981), Color(0xFFEC4899), Color(0xFF6B7280)
)

@Composable
fun HisabTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF00201C), secondaryContainer = Color(0xFF134E4A))
    } else {
        lightColorScheme(primary = Color(0xFF0F766E), onPrimary = Color.White, secondaryContainer = Color(0xFFCCEBE6))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun accEmoji(t: AccType): String = when (t) {
    AccType.CASH -> "💵"
    AccType.WALLET -> "📱"
    AccType.BANK -> "🏦"
    AccType.OTHER -> "👛"
}

fun txEmoji(t: TxType): String = when (t) {
    TxType.INCOME -> "⬇️"
    TxType.EXPENSE -> "⬆️"
    TxType.TRANSFER -> "🔁"
    TxType.BORROW -> "🤝"
    TxType.REPAY_DEBT -> "↩️"
    TxType.LEND -> "💸"
    TxType.RECEIVE_BACK -> "✅"
}

fun txColor(t: TxType): Color = when (t) {
    TxType.INCOME, TxType.BORROW, TxType.RECEIVE_BACK -> Green
    TxType.EXPENSE, TxType.REPAY_DEBT, TxType.LEND -> Red
    TxType.TRANSFER -> Blue
}

fun txSign(t: TxType): String = when (t) {
    TxType.INCOME, TxType.BORROW, TxType.RECEIVE_BACK -> "+"
    TxType.EXPENSE, TxType.REPAY_DEBT, TxType.LEND -> "−"
    TxType.TRANSFER -> ""
}

fun catLabel(key: String, bn: Boolean): String = Str.find("cat_$key", bn) ?: key

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

@Composable
fun TxRow(tx: Tx, data: AppData, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val bn = LocalBn.current
    val acc = data.accounts.firstOrNull { it.id == tx.accountId }?.name ?: "?"
    val to = data.accounts.firstOrNull { it.id == tx.toAccountId }?.name ?: "?"
    val person = data.loans.firstOrNull { it.id == tx.loanId }?.person ?: ""
    val title = when (tx.type) {
        TxType.INCOME, TxType.EXPENSE -> catLabel(tx.category, bn)
        TxType.TRANSFER -> "$acc → $to"
        else -> Str.get("tx_" + tx.type.name, bn) + " · " + person
    }
    val sub = Fmt.dateTime(tx.millis, bn) + " · " + acc + (if (tx.note.isNotBlank()) " · " + tx.note else "")
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(txColor(tx.type).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(txEmoji(tx.type)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(txSign(tx.type) + fm(tx.amountMinor), color = txColor(tx.type), fontWeight = FontWeight.Bold)
        if (onDelete != null) TextButton(onClick = onDelete) { Text("🗑") }
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            FilterChip(
                selected = selected(item),
                onClick = { onClick(item) },
                label = { Text(label(item)) }
            )
        }
    }
}

/** Two bars (income green / expense red) per bucket. Floats are used only for drawing, never for money. */
@Composable
fun BarChart(income: List<Long>, expense: List<Long>, labels: List<String>) {
    val max = maxOf(1L, income.maxOrNull() ?: 0L, expense.maxOrNull() ?: 0L)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = income.size
        if (n > 0) {
            val slot = size.width / n
            val bw = slot * 0.34f
            for (i in 0 until n) {
                val x0 = i * slot + slot * 0.12f
                val hi = size.height * (income[i].toFloat() / max.toFloat())
                val he = size.height * (expense[i].toFloat() / max.toFloat())
                drawRect(Green, topLeft = Offset(x0, size.height - hi), size = Size(bw, hi))
                drawRect(Red, topLeft = Offset(x0 + bw + 1f, size.height - he), size = Size(bw, he))
            }
        }
    }
    if (labels.size <= 12) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("■ ", color = Green)
        Text(tr("income"), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(16.dp))
        Text("■ ", color = Red)
        Text(tr("expense"), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PieChart(values: List<Long>, modifier: Modifier = Modifier) {
    val total = maxOf(1L, values.sum())
    Canvas(modifier.size(160.dp)) {
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v.toFloat() / total.toFloat()
            drawArc(Palette[i % Palette.size], start, sweep, true, size = size)
            start += sweep
        }
    }
}

fun pickDate(ctx: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        ctx,
        { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth
    ).show()
}

fun pickTime(ctx: Context, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    android.app.TimePickerDialog(ctx, { _, h, m -> onPick(h, m) }, hour, minute, false).show()
}
