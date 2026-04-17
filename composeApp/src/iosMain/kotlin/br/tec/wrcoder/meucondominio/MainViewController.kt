package br.tec.wrcoder.meucondominio

import androidx.compose.ui.window.ComposeUIViewController
import br.tec.wrcoder.meucondominio.di.doInitKoin

private var koinInitialized = false

fun MainViewController() = ComposeUIViewController {
    if (!koinInitialized) {
        doInitKoin()
        koinInitialized = true
    }
    App()
}
