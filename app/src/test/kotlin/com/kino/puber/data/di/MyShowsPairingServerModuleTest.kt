package com.kino.puber.data.di

import com.kino.puber.data.repository.MyShowsPairingServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication

internal class MyShowsPairingServerModuleTest {

    @Test
    fun repositoryModule_resolvesPairingServer_withDefaultCoroutineScope() {
        val application = koinApplication {
            modules(repositoryModule)
        }

        try {
            assertNotNull(application.koin.get<MyShowsPairingServer>())
        } finally {
            application.close()
        }
    }
}
