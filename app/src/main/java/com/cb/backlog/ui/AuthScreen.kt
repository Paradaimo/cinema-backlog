package com.cb.backlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repo: SupabaseRepo) : ViewModel() {

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun submit(email: String, password: String, isSignUp: Boolean, onDone: () -> Unit) {
        if (email.isBlank() || password.length < 6) {
            error.value = "Введите e-mail и пароль (минимум 6 символов)"
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            val result = runCatching {
                if (isSignUp) repo.signUp(email.trim(), password) else repo.signIn(email.trim(), password)
            }
            busy.value = false
            result.fold(
                onSuccess = { onDone() },
                onFailure = { e ->
                    error.value = if (e.message?.contains("Invalid login", true) == true)
                        "Неверный e-mail или пароль"
                    else
                        e.message ?: "Ошибка сети"
                },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(container: AppContainer, mainViewModel: MainViewModel) {
    val viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
        AuthViewModel(container.supabaseRepo)
    }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Бэклог") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isSignUp) "Регистрация" else "Вход",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.submit(email, password, isSignUp) { mainViewModel.refresh() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isSignUp) "Создать аккаунт" else "Войти")
                }
                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(if (isSignUp) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
