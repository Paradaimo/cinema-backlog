package com.cb.backlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(private val repo: SupabaseRepo) : ViewModel() {

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun createList(name: String, displayName: String, onDone: () -> Unit) {
        if (name.isBlank() || displayName.isBlank()) {
            error.value = "Заполните оба поля"
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            runCatching { repo.createList(name.trim(), displayName.trim()) }
                .fold(
                    onSuccess = { busy.value = false; onDone() },
                    onFailure = { e -> busy.value = false; error.value = e.message },
                )
        }
    }

    fun joinList(code: String, displayName: String, onDone: () -> Unit) {
        if (code.isBlank() || displayName.isBlank()) {
            error.value = "Заполните оба поля"
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            runCatching { repo.joinList(code.trim().uppercase(), displayName.trim()) }
                .fold(
                    onSuccess = { busy.value = false; onDone() },
                    onFailure = { e ->
                        busy.value = false
                        error.value = "Не удалось присоединиться. Проверьте код."
                    },
                )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(container: AppContainer, mainViewModel: MainViewModel) {
    val viewModel: SetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        SetupViewModel(container.supabaseRepo)
    }
    var mode by rememberSaveable { mutableStateOf("create") }
    var listName by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Общий список") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (mode == "create") "Создайте список" else "Присоединитесь к списку",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (mode == "create") {
                OutlinedTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    label = { Text("Название списка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Код приглашения") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Ваше имя для участников") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (busy) {
                CircularProgressIndicator()
            } else if (mode == "create") {
                Button(
                    onClick = { viewModel.createList(listName, displayName) { mainViewModel.refresh() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Создать") }
                OutlinedButton(
                    onClick = { mode = "join" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("У меня есть код приглашения") }
            } else {
                Button(
                    onClick = { viewModel.joinList(code, displayName) { mainViewModel.refresh() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Присоединиться") }
                OutlinedButton(
                    onClick = { mode = "create" },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Создать новый список") }
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Как это работает", fontWeight = FontWeight.Bold)
                    Text(
                        "Один список на всех: добавляйте фильмы, сериалы и аниме, " +
                            "отмечайте статусы и ставьте оценки. Изменения синхронизируются мгновенно.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
