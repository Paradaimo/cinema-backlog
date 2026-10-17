package com.cb.backlog.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.ListItem
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.data.WatchStatus
import com.cb.backlog.di.AppContainer
import com.cb.backlog.ui.components.PosterThumb
import com.cb.backlog.ui.components.StatusLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel(private val repo: SupabaseRepo) : ViewModel() {

    val item = MutableStateFlow<ListItem?>(null)
    val memberNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val error = MutableStateFlow<String?>(null)

    fun load(itemId: String) {
        viewModelScope.launch {
            runCatching {
                val membership = repo.myMembership() ?: error("Нет списка")
                val backlog = repo.fetchBacklog(membership.listId)
                memberNames.value = backlog.members.associate { it.userId to it.displayName }
                backlog.items.firstOrNull { it.item.id == itemId }
            }.fold(
                onSuccess = { item.value = it },
                onFailure = { error.value = it.message },
            )
        }
    }

    fun setStatus(itemId: String, status: WatchStatus?) {
        viewModelScope.launch {
            repo.setStatus(itemId, status)
            load(itemId)
        }
    }

    fun setRating(itemId: String, score: Int?) {
        viewModelScope.launch {
            repo.setRating(itemId, score)
            load(itemId)
        }
    }

    fun removeItem(itemId: String, onRemoved: () -> Unit) {
        viewModelScope.launch {
            repo.removeItem(itemId)
            onRemoved()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(container: AppContainer, itemId: String, onBack: () -> Unit) {
    val viewModel: DetailsViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        DetailsViewModel(container.supabaseRepo)
    }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val memberNames by viewModel.memberNames.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(itemId) { viewModel.load(itemId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.item?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    item?.let {
                        IconButton(onClick = { viewModel.removeItem(it.item.id, onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить из бэклога")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = item
        when {
            data == null && error == null -> {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(padding), horizontalArrangement = Arrangement.Center
                ) { androidx.compose.material3.CircularProgressIndicator() }
            }

            error != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp)
                ) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            data != null -> DetailsBody(data, memberNames, viewModel)
        }
    }
}

@Composable
private fun DetailsBody(
    data: ListItem,
    memberNames: Map<String, String>,
    viewModel: DetailsViewModel,
) {
    val row = data.item
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PosterThumb(
                url = row.posterUrl,
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusLabel(data.myStatus)
                row.year?.let { Text("Год: $it", style = MaterialTheme.typography.bodyMedium) }
                row.director?.takeIf { it.isNotBlank() }?.let {
                    Text("Режиссёр: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text("Добавил: ${data.addedByName}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Мой статус", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            WatchStatus.entries.forEach { status ->
                FilterChip(
                    selected = data.myStatus == status,
                    onClick = {
                        viewModel.setStatus(row.id, if (data.myStatus == status) null else status)
                    },
                    label = { Text(status.label) },
                )
            }
        }

        Text("Моя оценка", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            (1..10).forEach { score ->
                AssistChip(
                    onClick = {
                        viewModel.setRating(
                            row.id,
                            if (data.myRating == score) null else score,
                        )
                    },
                    label = { Text("$score") },
                )
            }
        }

        row.castList.takeIf { it.isNotEmpty() }?.let { cast ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("В ролях", style = MaterialTheme.typography.titleSmall)
                Text(cast.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
        }

        row.synopsis?.let {
            Text("Описание", style = MaterialTheme.typography.titleSmall)
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text("Участники", style = MaterialTheme.typography.titleSmall)
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.statuses.entries.sortedBy { it.key }.forEach { (userId, status) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val rating = data.ratings[userId]
                        Text(
                            memberNames[userId] ?: "Участник",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusLabel(status)
                            rating?.let {
                                Text(
                                    "$it/10",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
