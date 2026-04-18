package br.tec.wrcoder.meucondominio.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

/**
 * Renders an image by URL. Local `memory://` URLs read bytes from [BinaryStore];
 * remote URLs are downloaded on first render via [MediaRepository] and cached
 * in [BinaryStore] so subsequent renders are instant.
 */
@Composable
fun MemoryImage(
    url: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    store: BinaryStore = koinInject(),
    media: MediaRepository = koinInject(),
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val bytes = url?.let { entries[it] }

    LaunchedEffect(url) {
        if (url != null && entries[url] == null && !url.startsWith("memory://")) {
            media.fetchImageBytes(url)
        }
    }

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
