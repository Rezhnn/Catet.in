package com.catetin.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catetin.app.data.db.TransactionEntity
import com.catetin.app.ui.theme.Amber
import com.catetin.app.ui.theme.Coral
import com.catetin.app.ui.theme.Pine
import com.catetin.app.ui.theme.PineDeep
import com.catetin.app.util.CurrencyFormatter
import com.catetin.app.util.Dates
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CatetCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = color,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun HeroCard(
    title: String,
    amount: String,
    caption: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Pine, PineDeep)))
                .padding(22.dp),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        title,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    trailing?.invoke()
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    amount,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    caption,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amount = transaction.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val outgoing = transaction.direction == "EXPENSE"
    val transfer = transaction.direction == "TRANSFER"
    val color = when {
        transfer -> MaterialTheme.colorScheme.tertiary
        outgoing -> MaterialTheme.colorScheme.onSurface
        else -> Color(0xFF2E7D5B)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(transaction.category, transaction.direction)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.merchant ?: transaction.category,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                transaction.category + " · " + Dates.formatTime(transaction.occurredAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (outgoing) "−" else if (transfer) "" else "+") + CurrencyFormatter.format(amount, transaction.currency),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                maxLines = 1,
            )
            if (transaction.status == "NEEDS_REVIEW") {
                Text(
                    "Perlu ditinjau",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
        }
    }
}

@Composable
fun CategoryAvatar(category: String, direction: String = "EXPENSE", size: Int = 44) {
    val icon = categoryIcon(category, direction)
    val tint = if (direction == "INCOME") Color(0xFF2E7D5B) else Pine
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = category, tint = tint, modifier = Modifier.size((size * 0.48f).dp))
    }
}

private fun categoryIcon(category: String, direction: String): ImageVector = when {
    direction == "TRANSFER" || category == "Transfer" -> Icons.Outlined.SwapHoriz
    category.startsWith("Makanan") -> Icons.Outlined.Restaurant
    category == "Transportasi" -> Icons.Outlined.DirectionsCar
    category == "Belanja" -> Icons.Outlined.ShoppingBag
    category == "Tagihan" -> Icons.Outlined.ReceiptLong
    category == "Pulsa & Data" -> Icons.Outlined.Smartphone
    category == "Hiburan" -> Icons.Outlined.Movie
    category == "Kesehatan" -> Icons.Outlined.HealthAndSafety
    category == "Pendidikan" -> Icons.Outlined.School
    category == "Donasi" -> Icons.Outlined.VolunteerActivism
    category == "Gaji" -> Icons.Outlined.Payments
    category == "Asisten" -> Icons.Outlined.AutoAwesome
    else -> Icons.Outlined.MoreHoriz
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ReceiptLong,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun StatusPill(text: String, positive: Boolean = true) {
    val color = if (positive) Color(0xFF2E7D5B) else Coral
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun LinearProgress(value: Float, color: Color = Pine, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(8f, 8f))
        drawRoundRect(
            color,
            size = Size(size.width * value.coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

@Composable
fun RingProgress(
    fraction: Float,
    size: Int = 88,
    stroke: Int = 9,
    color: Color = Pine,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    label: String? = null,
) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            drawArc(track, 0f, 360f, false, style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round))
            drawArc(
                color,
                -90f,
                360f * fraction.coerceIn(0f, 1f),
                false,
                style = Stroke(stroke.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = Pine,
) {
    val max = values.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height((4 + (value / max) * 92).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == values.lastIndex) Amber else color.copy(alpha = 0.82f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CategoryBar(label: String, amount: String, fraction: Float, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(amount, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(
                color,
                size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
