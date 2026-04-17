package br.tec.wrcoder.meucondominio.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.core.BinaryStore
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

/**
 * Renders an image stored in [BinaryStore] by its `memory://` URL, falling back to
 * a given icon placeholder when the URL is null or unknown.
 */
@Composable
fun MemoryImage(
    url: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    store: BinaryStore = koinInject(),
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val bytes = url?.let { entries[it] }
    if (bytes != null) {
        AsyncImage(
            model = bytes,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
