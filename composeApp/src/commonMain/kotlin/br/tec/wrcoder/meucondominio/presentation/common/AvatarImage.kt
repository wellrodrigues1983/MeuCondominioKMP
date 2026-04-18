package br.tec.wrcoder.meucondominio.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

@Composable
fun AvatarImage(
    name: String,
    avatarUrl: String?,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    store: BinaryStore = koinInject(),
    media: MediaRepository = koinInject(),
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val bytes = avatarUrl?.let { entries[it] }

    LaunchedEffect(avatarUrl) {
        if (avatarUrl != null && entries[avatarUrl] == null && !avatarUrl.startsWith("memory://")) {
            media.fetchImageBytes(avatarUrl)
        }
    }
    Surface(
        shape = CircleShape,
        color = background,
        modifier = modifier.size(size),
    ) {
        if (bytes != null) {
            AsyncImage(
                model = bytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = foreground,
                    style = textStyle,
                )
            }
        }
    }
}
