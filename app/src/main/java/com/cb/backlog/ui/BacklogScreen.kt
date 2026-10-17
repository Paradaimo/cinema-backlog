package com.cb.backlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.BacklogData
import com.cb.backlog.data.ListItem
import com.cb.backlog.data.MediaType
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.data.WatchStatus
import com.cb.backlog.di.AppContainer
import com.cb.backlog.ui.components.PosterThumb
import com.cb.backlog.ui.components.StatusLabel
import com.cb.backlog.ui.components.TypeBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BacklogViewModel(private val repo: SupabaseRepo) : ViewModel() {

    val data = MutableStateFlow<BacklogData?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val statusFilter = MutableStateFlow<WatchStatus?>(null)

    init {
        viewModelScope.launch {
            repo.backlogChanges().collect { reload() }
        }
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            loading.value = data.value == null
            error.value = null
            runCatching {
                val membership = repo.myMembership() ?: return@launch
                data.value = repo.fetchBacklog(membership.listId)
            }.fold(
                onSuccess = { loading.value = false },
                onFailure = { e -> loading.value = false; error.value = e.message },
            )
        }
    }

    fun setStatus(itemId: String, status: WatchStatus?) {
        viewModelScope.launch {
            repo.setStatus(itemId, status)
            reload()
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            repo.removeItem(itemId)
            reload()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BacklogScreen(
    container: AppContainer,
    onOpenSearch: () -> Unit,
    onOpenItem: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel: BacklogViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        BacklogViewModel(container.supabaseRepo)
    }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(data?.listRow?.name ?: "Бэклог")
                        data?.listRow?.let {
                            Text(
                                "Код: ${it.inviteCode}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Выйти")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { viewModel.statusFilter.value = null },
                    label = { Text("Все") },
                )
                WatchStatus.entries.forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            viewModel.statusFilter.value =
                                if (statusFilter == status) null else status
                        },
                        label = { Text(status.label) },
                    )
                }
            }

            when {
                loading -> {
                    Row(
                        Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }

                error != null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.reload() }) { Text("Повторить") }
                    }
                }

                data?.items.isNullOrEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Бэклог пуст.\nДобавьте первый фильм, сериал или аниме!")
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onOpenSearch) { Text("Найти") }
                    }
                }

                else -> {
                    val items = data!!.items
                        .filter { statusFilter == null || it.myStatus == statusFilter }
                    LazyColumn(state = rememberLazyListState()) {
                        items(items, key = { it.item.id }) { item ->
                            BacklogItemRow(
                                item = item,
                                onClick = { onOpenItem(item.item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BacklogItemRow(item: ListItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterThumb(
            url = item.item.posterUrl,
            modifier = Modifier
                .width(56.dp)
                .height(84.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(item.item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(item.item.mediaType)
                item.item.year?.let { Text("$it", style = MaterialTheme.typography.labelSmall) }
                StatusLabel(item.myStatus)
            }
            Row {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.height(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    if (item.avgRating != null) "%.1f".format(item.avgRating) else "нет оценок",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        androidx.compose.material3.TextButton(onClick = onClick) {
            Text("Подробнее")
        }
    }
}
