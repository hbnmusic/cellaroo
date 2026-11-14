package app.cellaroo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cellaroo.R
import app.cellaroo.core.Readiness
import app.cellaroo.core.Source
import coil3.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Palette {
    val Background = Color(0xFF120D0E)
    val Surface = Color(0xFF1D1617)
    val SurfaceHigh = Color(0xFF2A2022)
    val Gold = Color(0xFFE2BC78)
    val Burgundy = Color(0xFF5A1626)
    val Rose = Color(0xFFDB8D99)
    val Text = Color(0xFFF2E8E3)
    val TextDim = Color(0xFFBFAEAA)
    val Outline = Color(0xFF5C4B4E)

    val Emerald = Color(0xFF35C48F)
    val Teal = Color(0xFF2CB5B3)
    val Amber = Color(0xFFE9AB3D)
    val Crimson = Color(0xFFE24C61)
    val Gray = Color(0xFF837679)

    /** Outline colours for numbered bottles on a scanned photo. */
    val Boxes = listOf(
        Color(0xFFFFD166), Color(0xFF06D6A0), Color(0xFF4CC9F0), Color(0xFFF78C6B),
        Color(0xFFC77DFF), Color(0xFFFF6B9A), Color(0xFFB5E48C), Color(0xFFFFFFFF),
    )

    fun box(i: Int): Color = Boxes[(i - 1).mod(Boxes.size)]

    fun readiness(r: Readiness): Color = when (r) {
        Readiness.AT_PEAK -> Emerald
        Readiness.READY -> Teal
        Readiness.HOLD -> Amber
        Readiness.PAST_PEAK -> Crimson
        Readiness.UNKNOWN -> Gray
    }
}

private val Scheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Color(0xFF2C1E00),
    primaryContainer = Palette.Burgundy,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Palette.Rose,
    onSecondary = Color(0xFF3A0B14),
    secondaryContainer = Color(0xFF43262B),
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Palette.Emerald,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    surfaceContainerLow = Palette.Surface,
    outline = Palette.Outline,
    outlineVariant = Color(0xFF3B2F31),
    error = Color(0xFFFF8A80),
)

@Composable
fun CellarooTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val serif = FontFamily.Serif
    MaterialTheme(
        colorScheme = Scheme,
        typography = base.copy(
            headlineMedium = base.headlineMedium.copy(fontFamily = serif),
            headlineSmall = base.headlineSmall.copy(fontFamily = serif),
            titleLarge = base.titleLarge.copy(fontFamily = serif),
            titleMedium = base.titleMedium.copy(fontFamily = serif, fontSize = 17.sp),
        ),
        content = content,
    )
}

fun money(v: Double?): String = when {
    v == null -> "—"
    v >= 1000 -> "$" + String.format(Locale.US, "%,.0f", v)
    else -> "$" + String.format(Locale.US, "%.2f", v)
}

fun day(t: Long?): String = t?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it)) } ?: "—"

fun score(v: Double?): String = v?.let { String.format(Locale.US, "%.0f", it) } ?: "—"

@Composable
fun Tag(text: String, color: Color, filled: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = text,
        color = if (filled) Color(0xFF14100F) else color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.dp, color, shape))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ReadinessPill(r: Readiness, closing: Boolean = false) {
    Tag(if (closing) "DRINK SOON" else r.label, if (closing) Palette.Crimson else Palette.readiness(r), filled = true)
}

/** Small badge saying where a value came from; ESTIMATE is the one that must never be missed. */
@Composable
fun SourceTag(source: String?) {
    val label = Source.label(source)
    if (label.isEmpty()) return
    Tag(label, if (source == Source.AI_ESTIMATE) Palette.Amber else Palette.TextDim)
}

@Composable
fun WineThumb(file: File?, width: Dp, height: Dp) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier.size(width, height).clip(shape).background(Palette.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
        } else {
            Icon(painterResource(R.drawable.ic_glass), contentDescription = null, tint = Palette.Outline)
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Palette.Surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Gold, modifier = Modifier.weight(1f))
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Text(
            title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun QtyStepper(qty: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, enabled = qty > 0, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = "Remove one bottle")
        }
        Text(qty.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(26.dp), maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add one bottle")
        }
    }
}

/** Five tappable stars; tapping the current value clears the rating. */
@Composable
fun StarRating(value: Float?, onChange: (Float?) -> Unit) {
    Row {
        for (i in 1..5) {
            IconButton(onClick = { onChange(if (value == i.toFloat()) null else i.toFloat()) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Star, contentDescription = "$i stars", tint = if (value != null && i <= value) Palette.Gold else Palette.Outline)
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Gap(h: Dp = 8.dp) {
    Spacer(Modifier.height(h))
}
