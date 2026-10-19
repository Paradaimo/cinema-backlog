package com.cb.backlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.cb.backlog.ui.components.GridPosterCard
import com.cb.backlog.ui.components.MediaTitleRow
import com.cb.backlog.ui.components.PosterThumb
import com.cb.backlog.ui.components.StatusLabel
import com.cb.backlog.ui.components.TypeBadge
import com.cb.backlog.ui.components.ViewMode
import com.cb.backlog.ui.components.ViewModePicker
import com.cb.backlog.ui.components.ViewModeStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BacklogViewModel(
    private val repo: SupabaseRepo,
    private val prefs: android.content.SharedPreferences,
) : ViewModel() {

    val data = MutableStateFlow<BacklogData?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val statusFilter = MutableStateFlow<WatchStatus?>(null)
    val lists = MutableStateFlow<List<SupabaseRepo.MyList>>(emptyList())
    val currentListId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repo.backlogChanges().collect { reload() }
        }
        refreshLists()
    }

    fun refreshLists() {
        viewModelScope.launch {
            runCatching { repo.myLists() }
                .onSuccess { result ->
                    lists.value = result
                    val saved = prefs.getString("current_list_id", null)
                    val target = result.firstOrNull { it.listId == saved }?.listId
                        ?: result.firstOrNull()?.listId
                    if (target != null && target != currentListId.value) switchList(target)
                }
                .onFailure { e -> error.value = e.message }
        }
    }

    fun switchList(listId: String) {
        currentListId.value = listId
        prefs.edit().putString("current_list_id", listId).apply()
        reload()
    }

    fun reload() {
        val listId = currentListId.value ?: return
        viewModelScope.launch {
            loading.value = data.value == null
            error.value = null
            runCatching {
                data.value = repo.fetchBacklog(listId)
            }.fold(
                onSuccess = { loading.value = false },
                onFailure = { e -> loading.value = false; error.value = e.message },
            )
        }
    }

    fun createList(name: String, displayName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            error.value = null
            runCatching { repo.createList(name.trim(), displayName.trim()) }
                .fold(
                    onSuccess = { row ->
                        refreshLists()
                        switchList(row.id)
                        onDone()
                    },
                    onFailure = { e -> error.value = e.message },
                )
        }
    }

    fun joinList(code: String, displayName: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            error.value = null
            runCatching { repo.joinList(code.trim().uppercase(), displayName.trim()) }
                .fold(
                    onSuccess = { row ->
                        refreshLists()
                        switchList(row.id)
                        onDone()
                    },
                    onFailure = { e -> error.value = "Не удалось присоединиться. Проверьте код." },
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
        BacklogViewModel(container.supabaseRepo, container.prefs)
    }
    val data by viewModel.data.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val currentListId by viewModel.currentListId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(ViewModeStore.load(context, "backlog")) }

    var listMenuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val currentList = lists.firstOrNull { it.listId == currentListId }
    val inviteCode = data?.listRow?.inviteCode

    var createDialog by remember { mutableStateOf(false) }
    var joinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { listMenuOpen = true },
                        ) {
                            Text(data?.listRow?.name ?: "Бэклог")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Сменить список")
                        }
                        inviteCode?.let { code ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Код: $code",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                IconButton(onClick = {
                                    clipboard.setText(AnnotatedString(code))
                                    android.widget.Toast.makeText(context, "Код скопирован", android.widget.Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать код", modifier = Modifier.height(16.dp))
                                }
                            }
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
            DropdownMenu(expanded = listMenuOpen, onDismissRequest = { listMenuOpen = false }) {
                lists.forEach { list ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (list.lists?.name ?: "Список") +
                                    if (list.listId == currentListId) "  ✓" else "",
                            )
                        },
                        onClick = {
                            listMenuOpen = false
                            viewModel.switchList(list.listId)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Создать список…") },
                    onClick = { listMenuOpen = false; createDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("Вступить по коду…") },
                    onClick = { listMenuOpen = false; joinDialog = true },
                )
            }

            LazyRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { viewModel.statusFilter.value = null },
                        label = { Text("Все") },
                    )
                }
                items(WatchStatus.entries.toList()) { status ->
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

            Spacer(Modifier.height(4.dp))
            var searchQuery by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по названию") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            ViewModePicker(
                current = viewMode,
                onChange = { viewMode = it; ViewModeStore.save(context, "backlog", it) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

            when {
                currentListId == null && !loading -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("У вас пока нет ни одного списка")
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { createDialog = true }) { Text("Создать список") }
                        TextButton(onClick = { joinDialog = true }) { Text("Вступить по коду") }
                    }
                }

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
                        .filter { searchQuery.isBlank() || it.item.title.contains(searchQuery.trim(), ignoreCase = true) }
                    if (items.isEmpty()) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("Ничего не найдено по запросу «${searchQuery.trim()}»")
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { searchQuery = "" }) { Text("Сбросить поиск") }
                        }
                    } else when (viewMode) {
                        ViewMode.TITLES -> LazyColumn(state = rememberLazyListState()) {
                            items(items, key = { it.item.id }) { item ->
                                MediaTitleRow(
                                    title = item.item.title,
                                    year = item.item.year,
                                    status = item.myStatus,
                                    onClick = { onOpenItem(item.item.id) },
                                )
                            }
                        }

                        ViewMode.LIST -> LazyColumn(state = rememberLazyListState()) {
                            items(items, key = { it.item.id }) { item ->
                                BacklogItemRow(
                                    item = item,
                                    onClick = { onOpenItem(item.item.id) },
                                )
                            }
                        }

                        ViewMode.GRID -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = rememberLazyGridState(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(items, key = { it.item.id }) { item ->
                                GridPosterCard(
                                    title = item.item.title,
                                    posterUrl = item.item.posterUrl,
                                    year = item.item.year,
                                    type = item.item.mediaType,
                                    status = item.myStatus,
                                    onClick = { onOpenItem(item.item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (createDialog) {
        CreateListDialog(
            onDismiss = { createDialog = false },
            onCreate = { name, displayName -> viewModel.createList(name, displayName) },
        )
    }
    if (joinDialog) {
        JoinListDialog(
            onDismiss = { joinDialog = false },
            onJoin = { code, displayName -> viewModel.joinList(code, displayName) },
        )
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

@Composable
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, displayName: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый список") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название списка") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Ваше имя в этом списке") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, displayName); onDismiss() }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun JoinListDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String, displayName: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вступить по коду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Код приглашения") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Ваше имя в этом списке") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code, displayName); onDismiss() }) { Text("Вступить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


