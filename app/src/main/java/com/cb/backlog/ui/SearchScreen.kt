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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.CatalogRepository
import com.cb.backlog.data.MediaInfo
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.di.AppContainer
import com.cb.backlog.ui.components.PosterThumb
import com.cb.backlog.ui.components.TypeBadge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class SearchMode(val label: String) {
    VIDEO("Фильмы и сериалы"),
    ANIME("Аниме"),
}

class SearchViewModel(
    private val catalog: CatalogRepository,
    private val supabase: SupabaseRepo,
) : ViewModel() {

    val query = MutableStateFlow("")
    val mode = MutableStateFlow(SearchMode.VIDEO)
    val results = MutableStateFlow<List<MediaInfo>>(emptyList())
    val searching = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val addedTitle = MutableStateFlow<String?>(null)

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            query.collect { q ->
                searchJob?.cancel()
                if (q.isBlank()) {
                    results.value = emptyList()
                    return@collect
                }
                searchJob = launch {
                    delay(400)
                    search(q)
                }
            }
        }
    }

    fun setMode(newMode: SearchMode) {
        mode.value = newMode
        val q = query.value
        if (q.isNotBlank()) {
            viewModelScope.launch { search(q) }
        }
    }

    private suspend fun search(q: String) {
        searching.value = true
        error.value = null
        runCatching {
            results.value = when (mode.value) {
                SearchMode.VIDEO -> catalog.searchMoviesAndShows(q)
                SearchMode.ANIME -> catalog.searchAnime(q)
            }
        }.fold(
            onSuccess = { searching.value = false },
            onFailure = { e -> searching.value = false; error.value = "Ошибка поиска: ${e.message}" },
        )
    }

    fun add(info: MediaInfo) {
        viewModelScope.launch {
            error.value = null
            runCatching {
                val full = if (info.synopsis == null) {
                    catalog.loadDetails(info.source, info.externalId, info.type)
                } else {
                    info
                }
                supabase.addItem(full)
            }.fold(
                onSuccess = {
                    addedTitle.value = info.title
                    results.value = results.value - info
                },
                onFailure = { e ->
                    error.value = "Не удалось добавить (возможно, уже есть в списке): ${e.message}"
                },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        SearchViewModel(container.catalogRepository, container.supabaseRepo)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val addedTitle by viewModel.addedTitle.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.query.value = it },
                label = { Text("Название") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.query.value = query }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { viewModel.setMode(m) },
                        label = { Text(m.label) },
                    )
                }
            }
            addedTitle?.let {
                Spacer(Modifier.height(8.dp))
                Text("Добавлено: $it", color = MaterialTheme.colorScheme.primary)
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))

            if (searching) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.source + it.externalId }) { info ->
                        SearchResultRow(info = info, onAdd = { viewModel.add(info) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(info: MediaInfo, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosterThumb(
            url = info.posterUrl,
            modifier = Modifier
                .width(48.dp)
                .height(72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeBadge(info.type)
                info.year?.let { Text("$it", style = MaterialTheme.typography.labelSmall) }
            }
        }
        TextButton(onClick = onAdd) { Text("В бэклог") }
    }
}
