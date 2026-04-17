package br.tec.wrcoder.meucondominio.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JoinCondominiumScreen(vm: JoinCondominiumViewModel = koinViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Entrar em um condomínio", onBack = vm::back) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = s.code,
                onValueChange = { vm.update { copy(code = it) } },
                label = { Text("Código do condomínio") },
                trailingIcon = { TextButton(onClick = vm::validateCode) { Text("Validar") } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.condominiumName != null) {
                Text("Condomínio: ${s.condominiumName}", color = MaterialTheme.colorScheme.primary)
            }
            OutlinedTextField(
                value = s.unitIdentifier,
                onValueChange = { vm.update { copy(unitIdentifier = it) } },
                label = { Text("Unidade (ex: 101)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.name,
                onValueChange = { vm.update { copy(name = it) } },
                label = { Text("Seu nome") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.email,
                onValueChange = { vm.update { copy(email = it) } },
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.phone,
                onValueChange = { vm.update { copy(phone = it) } },
                label = { Text("Telefone (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.password,
                onValueChange = { vm.update { copy(password = it) } },
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (s.error != null) Text(s.error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::submit, enabled = !s.loading, modifier = Modifier.fillMaxWidth()) {
                if (s.loading) CircularProgressIndicator(Modifier.height(18.dp)) else Text("Entrar no condomínio")
            }
        }
    }
}
