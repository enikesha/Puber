package com.kino.puber.data.repository

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class MyShowsPairingSession(
    val url: String,
)

internal class MyShowsPairingServer(
    private val serverScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var cleanupJob: Job? = null
    @Volatile
    private var pairingState = PairingState.WAITING

    fun start(onTokenReceived: (String) -> Unit): Result<MyShowsPairingSession> {
        stop()
        return runCatching {
            pairingState = PairingState.WAITING
            val address = findLocalAddress()
            val pairingPath = "/pair/${randomUrlToken()}/"
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
            synchronized(lock) {
                serverSocket = socket
                serverJob = serverScope.launch {
                    acceptConnections(socket, pairingPath, onTokenReceived)
                }
            }
            MyShowsPairingSession(
                url = "http://${address.hostAddress}:${socket.localPort}$pairingPath",
            )
        }.onFailure {
            stop()
        }
    }

    fun stop() {
        synchronized(lock) {
            serverSocket?.runCatching { close() }
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
            cleanupJob?.cancel()
            cleanupJob = null
        }
    }

    fun reportPairingResult(isConnected: Boolean) {
        pairingState = if (isConnected) PairingState.CONNECTED else PairingState.ERROR
        if (isConnected) {
            synchronized(lock) {
                cleanupJob?.cancel()
                cleanupJob = serverScope.launch {
                    delay(SUCCESS_PAGE_LIFETIME_MS)
                    stop()
                }
            }
        }
    }

    private suspend fun acceptConnections(
        socket: ServerSocket,
        pairingPath: String,
        onTokenReceived: (String) -> Unit,
    ) {
        while (serverScope.isActive && !socket.isClosed) {
            try {
                socket.accept().use { client ->
                    handleRequest(client, pairingPath, onTokenReceived)
                }
            } catch (_: SocketException) {
                break
            } catch (error: Exception) {
                Timber.w(error, "MyShows phone pairing request failed")
            }
        }
    }

    private fun handleRequest(
        client: Socket,
        pairingPath: String,
        onTokenReceived: (String) -> Unit,
    ) {
        client.soTimeout = SOCKET_TIMEOUT_MS
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        val requestLine = reader.readLine().orEmpty()
        val contentLength = readContentLength(reader)
        val method = requestLine.substringBefore(' ')
        val target = requestLine.substringAfter(' ', "").substringBefore(' ')
        val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))

        when {
            method == "GET" && target == pairingPath ->
                respond(writer, HTTP_OK, "text/html; charset=utf-8", PAIRING_PAGE)
            method == "GET" && target == "${pairingPath}status" ->
                respond(writer, HTTP_OK, "application/json; charset=utf-8", pairingState.responseBody)
            method == "POST" && target == "${pairingPath}connect" ->
                receiveToken(reader, writer, contentLength, onTokenReceived)
            else -> respond(writer, HTTP_NOT_FOUND, "text/plain; charset=utf-8", "Not found")
        }
    }

    private fun receiveToken(
        reader: BufferedReader,
        writer: BufferedWriter,
        contentLength: Int,
        onTokenReceived: (String) -> Unit,
    ) {
        if (contentLength !in 1..MAX_REQUEST_BODY_SIZE) {
            respond(writer, HTTP_BAD_REQUEST, "text/plain; charset=utf-8", "Invalid request")
            return
        }
        val token = CharArray(contentLength).also { body ->
            var offset = 0
            while (offset < body.size) {
                val read = reader.read(body, offset, body.size - offset)
                if (read < 0) break
                offset += read
            }
        }.concatToString()
            .trim()
            .takeIf { it.isNotEmpty() }
        if (token == null) {
            respond(writer, HTTP_BAD_REQUEST, "text/plain; charset=utf-8", "Invalid token")
            return
        }
        pairingState = PairingState.CHECKING
        respond(writer, HTTP_OK, "text/plain; charset=utf-8", "Token received")
        runCatching { onTokenReceived(token) }
            .onFailure { pairingState = PairingState.ERROR }
    }

    private fun readContentLength(reader: BufferedReader): Int {
        var contentLength = 0
        var line = reader.readLine()
        while (!line.isNullOrEmpty()) {
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            line = reader.readLine()
        }
        return contentLength
    }

    private fun respond(
        writer: BufferedWriter,
        status: String,
        contentType: String,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        writer.write("HTTP/1.1 $status\r\n")
        writer.write("Content-Type: $contentType\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Cache-Control: no-store\r\n")
        writer.write("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; " +
            "script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; base-uri 'none'\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.write(body)
        writer.flush()
    }

    private fun randomUrlToken(): String {
        val bytes = ByteArray(PAIRING_SECRET_BYTES).also(SecureRandom()::nextBytes)
        return Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }

    private fun findLocalAddress(): Inet4Address {
        val candidates = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { network -> runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) }
            .flatMap { network ->
                Collections.list(network.inetAddresses)
                    .filterIsInstance<Inet4Address>()
                    .filter(Inet4Address::isSiteLocalAddress)
                    .map { address -> network.name to address }
            }
        return candidates
            .sortedBy { (name, _) ->
                if (PREFERRED_INTERFACE_PREFIXES.any(name::startsWith)) 0 else 1
            }
            .firstOrNull()
            ?.second
            ?: error("TV has no reachable local network address")
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 5_000
        const val MAX_REQUEST_BODY_SIZE = 4_096
        const val PAIRING_SECRET_BYTES = 18
        const val SUCCESS_PAGE_LIFETIME_MS = 60_000L
        const val HTTP_OK = "200 OK"
        const val HTTP_BAD_REQUEST = "400 Bad Request"
        const val HTTP_NOT_FOUND = "404 Not Found"
        val PREFERRED_INTERFACE_PREFIXES = listOf("wlan", "eth", "en")

        val PAIRING_PAGE = """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>Puber · MyShows</title>
              <style>
                :root{color-scheme:dark;font-family:system-ui,sans-serif}body{margin:0;background:#101014;color:#f7f4ff}
                main{max-width:560px;margin:auto;padding:28px 20px 48px}h1{font-size:32px;line-height:1.15;margin:0 0 12px}
                h2{font-size:19px;margin:28px 0 8px}.lead,p,li{color:#c9c4d4;line-height:1.5}.lead{font-size:18px}
                ol{padding-left:24px;margin:10px 0}li{padding:5px 0}.notice{background:#242129;border-radius:12px;padding:14px 16px;color:#f7f4ff}
                a,button{display:block;box-sizing:border-box;width:100%;border:0;border-radius:14px;padding:16px;margin:16px 0;
                  text-align:center;font-size:17px;font-weight:700;text-decoration:none;background:#7457ff;color:white}
                input{box-sizing:border-box;width:100%;border:1px solid #5e5968;border-radius:12px;padding:16px;background:#242129;
                  color:white;font-size:17px}button:disabled{opacity:.55}.status{min-height:48px;font-weight:650;color:#c9c4d4}
                .status.success{color:#9ed7a8}.status.error{color:#ffaaa2}.small{font-size:14px;color:#96909f}
              </style>
            </head>
            <body><main>
              <h1>Подключить Puber к MyShows</h1>
              <p class="lead">Создайте API-токен в MyShows и отправьте его на телевизор.</p>
              <p class="notice"><strong>Важно:</strong> создание API-токена доступно только с подпиской MyShows PRO.</p>
              <h2>1. Создайте и скопируйте токен</h2>
              <ol>
                <li>Откройте MyShows и войдите в аккаунт.</li>
                <li>Нажмите аватар справа сверху и выберите «История просмотров».</li>
                <li>В блоке «Ваш API токен» нажмите «Создать API токен», затем «Скопировать токен».</li>
              </ol>
              <a href="https://myshows.me/my" target="_blank" rel="noopener">Открыть MyShows</a>
              <h2>2. Отправьте токен на телевизор</h2>
              <p>Вернитесь в эту вкладку, вставьте скопированный токен и нажмите «Подключить».</p>
              <form id="form"><input id="token" type="password" autocomplete="off" placeholder="Токен MyShows" required>
                <button id="send" type="submit">Подключить</button></form>
              <p id="status" class="status" role="status" aria-live="polite"></p>
              <p class="small">Не закрывайте эту страницу до сообщения об успешном подключении. Телефон и телевизор должны быть в одной сети Wi-Fi.</p>
              <script>
                const form=document.getElementById('form');const field=document.getElementById('token');
                const button=document.getElementById('send');const status=document.getElementById('status');
                function showStatus(text,type=''){status.textContent=text;status.className='status '+type;}
                async function waitForResult(){
                  for(let attempt=0;attempt<60;attempt++){
                    const response=await fetch(location.pathname+'status',{cache:'no-store'});
                    if(!response.ok)throw new Error();const result=await response.json();
                    if(result.state==='connected')return 'connected';if(result.state==='error')return 'error';
                    await new Promise(resolve=>setTimeout(resolve,750));
                  }
                  return 'timeout';
                }
                document.getElementById('form').addEventListener('submit',async event=>{
                  event.preventDefault();const token=field.value.trim();
                  if(!token){showStatus('Сначала вставьте скопированный токен.','error');field.focus();return;}
                  button.disabled=true;showStatus('Проверяем токен… Не закрывайте страницу.');
                  try{
                    const response=await fetch(location.pathname+'connect',{method:'POST',headers:{'Content-Type':'text/plain'},body:token});
                    if(!response.ok)throw new Error();const result=await waitForResult();
                    if(result==='connected'){field.value='';form.hidden=true;
                      showStatus('Готово — Puber подключён к MyShows. Эту страницу можно закрыть.','success');return;}
                    if(result==='error'){showStatus('MyShows отклонил токен. Скопируйте весь токен и попробуйте ещё раз.','error');return;}
                    showStatus('Проверка заняла слишком много времени. Попробуйте подключить ещё раз.','error');
                  }catch(error){showStatus('Телевизор недоступен. Проверьте, что телефон и телевизор в одной сети Wi-Fi.','error');}
                  finally{button.disabled=false;}
                });
              </script>
            </main></body></html>
        """.trimIndent()
    }

    private enum class PairingState(val responseBody: String) {
        WAITING("{\"state\":\"waiting\"}"),
        CHECKING("{\"state\":\"checking\"}"),
        CONNECTED("{\"state\":\"connected\"}"),
        ERROR("{\"state\":\"error\"}"),
    }
}
