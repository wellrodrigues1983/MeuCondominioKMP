package br.tec.wrcoder.meucondominio

import android.app.Application
import br.tec.wrcoder.meucondominio.di.androidModule
import br.tec.wrcoder.meucondominio.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MeuCondominioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(platformModules = listOf(androidModule(this))) {
            androidLogger()
            androidContext(this@MeuCondominioApp)
        }
    }
}
