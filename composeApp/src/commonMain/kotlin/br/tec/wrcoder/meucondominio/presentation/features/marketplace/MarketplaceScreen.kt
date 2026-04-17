package br.tec.wrcoder.meucondominio.presentation.features.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.ListingStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MarketplaceScreen(vm: MarketplaceViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Anúncios", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canCreate) {
                ExtendedFloatingActionButton(onClick = vm::showCreate) { Text("+ Novo anúncio") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.listings.isEmpty()) EmptyState("Sem anúncios", "Ninguém anunciou ainda.")
            else LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.listings, key = { it.id }) { listing ->
                    ListingCard(
                        listing = listing,
                        isOwner = s.user?.id == listing.authorUserId,
                        onClose = { vm.close(listing) },
                        onRenew = { vm.renew(listing) },
                    )
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Novo anúncio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.title, { v -> vm.update { copy(title = v) } }, label = { Text("Título") })
                    OutlinedTextField(s.editor.description, { v -> vm.update { copy(description = v) } }, label = { Text("Descrição") }, minLines = 3)
                    OutlinedTextField(s.editor.price, { v -> vm.update { copy(price = v) } }, label = { Text("Preço (opcional)") })
                    OutlinedTextField(s.editor.imageUrls, { v -> vm.update { copy(imageUrls = v) } }, label = { Text("URLs (vírgula)") })
                }
            },
            confirmButton = { TextButton(onClick = vm::save) { Text("Publicar") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancelar") } },
        )
    }

    s.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Aviso") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun ListingCard(listing: Listing, isOwner: Boolean, onClose: () -> Unit, onRenew: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(listing.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text(listing.status.name) })
            }
            Text(listing.description, style = MaterialTheme.typography.bodyMedium)
            listing.price?.let { Text("R$ $it", color = MaterialTheme.colorScheme.primary) }
            Text(
                "${listing.authorName} · Unidade ${listing.unitIdentifier}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOwner) {
                Row {
                    if (listing.status == ListingStatus.ACTIVE) {
                        TextButton(onClick = onClose) { Text("Encerrar") }
                    }
                    if (listing.status == ListingStatus.EXPIRED || listing.status == ListingStatus.CLOSED) {
                        TextButton(onClick = onRenew) { Text("Renovar 30 dias") }
                    }
                }
            }
        }
    }
}
