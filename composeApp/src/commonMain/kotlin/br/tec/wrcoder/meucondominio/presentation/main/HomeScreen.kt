package br.tec.wrcoder.meucondominio.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(vm: HomeViewModel = koinViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTopBar(
                title = user?.let { "Olá, ${it.name.substringBefore(' ')}" } ?: "Início",
                actions = {
                    TextButton(onClick = vm::logout) { Text("Sair") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            user?.let {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Perfil: ${roleLabel(it.role)}", style = MaterialTheme.typography.labelLarge)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(vm.featuresFor(user)) { feature ->
                    Card(
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth().clickable { vm.go(feature.route) },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(feature.title, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMIN -> "Administrador"
    UserRole.SUPERVISOR -> "Supervisor"
    UserRole.RESIDENT -> "Morador"
}
