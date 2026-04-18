package br.tec.wrcoder.meucondominio.presentation.features.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.ListingStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.ImagePickerField
import br.tec.wrcoder.meucondominio.presentation.common.MemoryImage
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
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
                ExtendedFloatingActionButton(
                    onClick = vm::showCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo anúncio") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.listings.isEmpty()) {
                EmptyState(
                    title = "Sem anúncios",
                    description = "Ninguém publicou nada ainda.",
                    icon = Icons.Filled.Storefront,
                )
            } else {
                LazyColumn(
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
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Novo anúncio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        s.editor.title,
                        { v -> vm.update { copy(title = v) } },
                        label = { Text("Título") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.description,
                        { v -> vm.update { copy(description = v) } },
                        label = { Text("Descrição") },
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.price,
                        { v -> vm.update { copy(price = v) } },
                        label = { Text("Preço") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    ImagePickerField(
                        currentBytes = s.editor.imageBytes,
                        onPicked = { bytes -> vm.update { copy(imageBytes = bytes) } },
                        label = "Selecionar foto do produto",
                        fallbackIcon = Icons.Filled.Storefront,
                    )
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
    val (statusLabel, statusTone) = when (listing.status) {
        ListingStatus.ACTIVE -> "Ativo" to PillTone.Success
        ListingStatus.EXPIRED -> "Expirado" to PillTone.Warning
        ListingStatus.CLOSED -> "Encerrado" to PillTone.Neutral
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            val photoUrl = listing.imageUrls.firstOrNull()
            if (photoUrl != null) {
                MemoryImage(
                    url = photoUrl,
                    fallbackIcon = Icons.Filled.Storefront,
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
            }
            Row(Modifier.padding(16.dp)) {
                IconBadge(
                    icon = Icons.Filled.Storefront,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    size = 44.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            listing.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(text = statusLabel, tone = statusTone)
                    }
                    Text(
                        listing.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listing.price?.let {
                        Text(
                            "R$ $it",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "${listing.authorName} · Unidade ${listing.unitIdentifier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isOwner) {
                        Spacer(Modifier.size(4.dp))
                        Row {
                            if (listing.status == ListingStatus.ACTIVE) {
                                TextButton(onClick = onClose) {
                                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Encerrar")
                                }
                            }
                            if (listing.status == ListingStatus.EXPIRED || listing.status == ListingStatus.CLOSED) {
                                TextButton(onClick = onRenew) {
                                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Renovar 30 dias")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
