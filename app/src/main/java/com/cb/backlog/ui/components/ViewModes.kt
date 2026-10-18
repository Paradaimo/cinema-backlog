package com.cb.backlog.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cb.backlog.data.MediaType
import com.cb.backlog.data.WatchStatus

enum class ViewMode(val label: String) {
    TITLES("Названия"),
    LIST("Список"),
    GRID("Плитки"),
}

object ViewModeStore {
    private const val PREFS = "cb_prefs"

    fun load(context: Context, screen: String): ViewMode {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            ViewMode.valueOf(sp.getString("view_$screen", ViewMode.LIST.name)!!)
        }.getOrDefault(ViewMode.LIST)
    }

    fun save(context: Context, screen: String, mode: ViewMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("view_$screen", mode.name).apply()
    }
}

@Composable
fun ViewModePicker(current: ViewMode, onChange: (ViewMode) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        ViewMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, ViewMode.entries.size),
                label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
fun MediaTitleRow(
    title: String,
    year: Int?,
    status: WatchStatus?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                year?.let { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                status?.let { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
fun GridPosterCard(
    title: String,
    posterUrl: String?,
    year: Int?,
    type: MediaType?,
    status: WatchStatus?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        PosterThumb(
            url = posterUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            year?.let { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            status?.let { Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
