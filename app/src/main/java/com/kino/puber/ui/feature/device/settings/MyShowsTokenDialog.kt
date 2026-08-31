@file:Suppress("MagicNumber")

package com.kino.puber.ui.feature.device.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvDialogOverlay
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import kotlinx.coroutines.delay

private const val FOCUS_DELAY_MS = 100L
private const val QR_SIZE_PX = 480

@Composable
internal fun MyShowsTokenDialog(
    isOpen: Boolean,
    isConnected: Boolean,
    isRequestInProgress: Boolean,
    pairingUrl: String?,
    isPairingUnavailable: Boolean,
    onConnect: (String) -> Unit,
    onValidate: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return

    var token by rememberSaveable(isConnected) { mutableStateOf("") }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    val primaryActionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isPairingUnavailable) {
        if (isPairingUnavailable) showManualEntry = true
    }
    RestoreActionFocusAfterKeyboard(primaryActionFocusRequester)

    TvDialogOverlay(onDismiss = onDismiss) { dismiss ->
        Card(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PairingHeader()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PairingQrCode(
                        pairingUrl = pairingUrl,
                        isPairingUnavailable = isPairingUnavailable,
                    )
                    PairingInstructions(
                        pairingUrl = pairingUrl,
                        isPairingUnavailable = isPairingUnavailable,
                        isRequestInProgress = isRequestInProgress,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showManualEntry) {
                    TokenInput(
                        token = token,
                        isConnected = isConnected,
                        enabled = !isRequestInProgress,
                        onTokenChange = { token = it },
                        onKeyboardClosed = { primaryActionFocusRequester.requestFocusSafely() },
                    )
                }
                DialogActions(
                    isConnected = isConnected,
                    isRequestInProgress = isRequestInProgress,
                    showManualEntry = showManualEntry,
                    hasToken = token.isNotBlank(),
                    primaryActionFocusRequester = primaryActionFocusRequester,
                    onToggleManualEntry = { showManualEntry = !showManualEntry },
                    onConnect = { onConnect(token) },
                    onValidate = onValidate,
                    onDisconnect = onDisconnect,
                    onDismiss = dismiss,
                )
            }
        }
    }
}

@Composable
private fun PairingHeader() {
    Text(
        text = stringResource(R.string.myshows_dialog_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.myshows_dialog_description),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PairingInstructions(
    pairingUrl: String?,
    isPairingUnavailable: Boolean,
    isRequestInProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.myshows_pairing_phone_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.myshows_pairing_same_network),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            isPairingUnavailable -> PairingError()
            isRequestInProgress -> PairingStatus(R.string.myshows_pairing_checking, showProgress = true)
            pairingUrl == null -> PairingStatus(R.string.myshows_pairing_waiting, showProgress = true)
            else -> PairingStatus(R.string.myshows_pairing_waiting, showProgress = false)
        }
    }
}

@Composable
private fun PairingStatus(textResource: Int, showProgress: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        Text(
            text = stringResource(textResource),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PairingError() {
    Text(
        text = stringResource(R.string.myshows_pairing_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun PairingQrCode(
    pairingUrl: String?,
    isPairingUnavailable: Boolean,
) {
    Box(
        modifier = Modifier
            .size(210.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isPairingUnavailable -> Text(
                text = stringResource(R.string.myshows_pairing_qr_unavailable),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            pairingUrl == null -> CircularProgressIndicator()
            else -> {
                val qrBitmap = remember(pairingUrl) { createQrBitmap(pairingUrl) }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.myshows_pairing_qr_description),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun createQrBitmap(content: String): Bitmap {
    val matrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        QR_SIZE_PX,
        QR_SIZE_PX,
        mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        ),
    )
    val pixels = IntArray(QR_SIZE_PX * QR_SIZE_PX) { index ->
        val x = index % QR_SIZE_PX
        val y = index / QR_SIZE_PX
        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, QR_SIZE_PX, 0, 0, QR_SIZE_PX, QR_SIZE_PX)
    }
}

@Composable
private fun RestoreActionFocusAfterKeyboard(focusRequester: FocusRequester) {
    var wasKeyboardOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(FOCUS_DELAY_MS)
        focusRequester.requestFocusSafely()
    }
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardOpen) {
        if (wasKeyboardOpen && !isKeyboardOpen) focusRequester.requestFocusSafely()
        wasKeyboardOpen = isKeyboardOpen
    }
}

private fun FocusRequester.requestFocusSafely() {
    runCatching(::requestFocus)
}

@Composable
private fun TokenInput(
    token: String,
    isConnected: Boolean,
    enabled: Boolean,
    onTokenChange: (String) -> Unit,
    onKeyboardClosed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.myshows_token_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TokenInputField(token, isConnected, enabled, onTokenChange, onKeyboardClosed)
    }
}

@Composable
private fun TokenInputField(
    token: String,
    isConnected: Boolean,
    enabled: Boolean,
    onTokenChange: (String) -> Unit,
    onKeyboardClosed: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    fun closeKeyboard() {
        keyboardController?.hide()
        onKeyboardClosed()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = token,
            onValueChange = onTokenChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    val isBack = event.type == KeyEventType.KeyUp &&
                        (event.key == Key.Back || event.key == Key.Escape)
                    if (isBack) closeKeyboard()
                    isBack
                },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { closeKeyboard() }),
            decorationBox = { innerTextField ->
                if (token.isEmpty()) TokenPlaceholder(isConnected)
                innerTextField()
            },
        )
    }
}

@Composable
private fun TokenPlaceholder(isConnected: Boolean) {
    Text(
        text = stringResource(
            if (isConnected) {
                R.string.myshows_token_replace_placeholder
            } else {
                R.string.myshows_token_placeholder
            }
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DialogActions(
    isConnected: Boolean,
    isRequestInProgress: Boolean,
    showManualEntry: Boolean,
    hasToken: Boolean,
    primaryActionFocusRequester: FocusRequester,
    onToggleManualEntry: () -> Unit,
    onConnect: () -> Unit,
    onValidate: () -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isConnected) {
            TvSafeButton(
                text = stringResource(
                    if (isRequestInProgress) R.string.myshows_checking else R.string.myshows_validate
                ),
                onClick = onValidate,
                enabled = !isRequestInProgress,
                primary = true,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
            )
            TvSafeButton(
                text = stringResource(R.string.myshows_disconnect),
                onClick = onDisconnect,
                enabled = !isRequestInProgress,
            )
        } else {
            TvSafeButton(
                text = stringResource(
                    if (showManualEntry) R.string.myshows_hide_manual_entry else R.string.myshows_manual_entry
                ),
                onClick = onToggleManualEntry,
                enabled = !isRequestInProgress,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
            )
        }
        if (showManualEntry) {
            TvSafeButton(
                text = stringResource(
                    if (isRequestInProgress) R.string.myshows_checking else R.string.myshows_connect
                ),
                onClick = onConnect,
                enabled = hasToken && !isRequestInProgress,
                primary = !isConnected,
            )
        }
        TvSafeButton(
            text = stringResource(R.string.myshows_close),
            onClick = onDismiss,
            enabled = !isRequestInProgress,
        )
    }
}

@Preview(name = "MyShows — phone pairing", device = TV_1080p)
@Composable
private fun MyShowsPairingPreview() = PuberTheme {
    MyShowsTokenDialog(
        isOpen = true,
        isConnected = false,
        isRequestInProgress = false,
        pairingUrl = "http://192.168.1.42:42137/pair/example/",
        isPairingUnavailable = false,
        onConnect = {},
        onValidate = {},
        onDisconnect = {},
        onDismiss = {},
    )
}
