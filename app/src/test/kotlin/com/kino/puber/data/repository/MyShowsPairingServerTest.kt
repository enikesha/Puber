package com.kino.puber.data.repository

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MyShowsPairingServerTest {

    @Test
    fun pairingPage_receivesTokenEnteredOnPhone() {
        val server = MyShowsPairingServer()
        val received = mutableListOf<String>()
        val latch = CountDownLatch(1)
        try {
            val session = server.start { token ->
                received += token
                latch.countDown()
            }.getOrThrow()

            val page = URL(session.url).readText()
            assertTrue(page.contains("Подключить Puber к MyShows"))
            assertTrue(page.contains("Создать API токен"))
            assertTrue(page.contains("Скопировать токен"))
            assertTrue(page.contains("MyShows PRO"))
            assertTrue(page.contains("https://myshows.me/my"))

            val connection = URL("${session.url}connect").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.outputStream.use { it.write("phone-token".toByteArray()) }

            assertEquals(200, connection.responseCode)
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("phone-token"), received)
            assertTrue(URL("${session.url}status").readText().contains("checking"))

            server.reportPairingResult(isConnected = false)
            assertTrue(URL("${session.url}status").readText().contains("error"))

            server.reportPairingResult(isConnected = true)
            assertTrue(URL("${session.url}status").readText().contains("connected"))
        } finally {
            server.stop()
        }
    }
}
