package com.yourname.lumen.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.lumen.core.designsystem.LumenButton
import com.yourname.lumen.core.designsystem.LumenText
import com.yourname.lumen.core.designsystem.LumenTextField
import com.yourname.lumen.core.designsystem.LumenTheme
import com.yourname.lumen.data.m3u.M3uClient
import com.yourname.lumen.domain.model.Source
import com.yourname.lumen.domain.model.SourceType
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class FormField(
    val key: String,
    val label: String,
    val masked: Boolean = false,
    val keyboard: KeyboardType = KeyboardType.Text,
    val hint: String = "",
)

private fun fieldsFor(type: SourceType): List<FormField> = when (type) {
    SourceType.XTREAM -> listOf(
        FormField("name", "Connection name", hint = "Optional"),
        FormField("url", "Server URL", keyboard = KeyboardType.Uri, hint = "http://server:8080"),
        FormField("user", "Username"),
        FormField("pass", "Password", masked = true),
    )

    SourceType.SELF_HOSTED -> listOf(
        FormField("name", "Connection name", hint = "Optional"),
        FormField("url", "Server address", keyboard = KeyboardType.Uri, hint = "server.local"),
        FormField("port", "Port", keyboard = KeyboardType.Number, hint = "Optional"),
        FormField("user", "Username"),
        FormField("pass", "Password", masked = true),
    )

    SourceType.M3U -> listOf(
        FormField("name", "Playlist name", hint = "Optional"),
        FormField("url", "Playlist link", keyboard = KeyboardType.Uri, hint = "https://…/playlist.m3u"),
    )
}

/** Values already saved for a connection, so editing starts from what is there. */
private fun prefill(type: SourceType, source: Source?): Map<String, String> {
    if (source == null) return mapOf("https" to "0")
    val values = mutableMapOf("name" to source.name)
    when (type) {
        SourceType.XTREAM -> {
            values["url"] = source.baseUrl
            values["user"] = source.username
            values["pass"] = source.password
        }

        SourceType.SELF_HOSTED -> {
            val uri = runCatching { URI(source.baseUrl) }.getOrNull()
            values["url"] = uri?.host ?: source.baseUrl
            values["port"] = uri?.port?.takeIf { it > 0 }?.toString() ?: ""
            values["https"] = if (uri?.scheme.equals("https", ignoreCase = true)) "1" else "0"
            values["user"] = source.username
            values["pass"] = source.password
        }

        SourceType.M3U -> {
            if (source.baseUrl.startsWith(M3uClient.FILE_PREFIX)) {
                values["file"] = source.baseUrl.removePrefix(M3uClient.FILE_PREFIX)
                values["fileName"] = "Imported file"
            } else {
                values["url"] = source.baseUrl
            }
        }
    }
    return values
}

/**
 * Add or edit one connection. Every field is a normal text input that uses the device's own keyboard.
 * [onSubmit] gets the entered values; with save = false it only tests the connection.
 * It returns a message for the user, or null on success.
 */
@Composable
fun ConnectionFormScreen(
    type: SourceType,
    initial: Source?,
    onClose: () -> Unit,
    onSubmit: suspend (values: Map<String, String>, save: Boolean) -> String?,
    modifier: Modifier = Modifier,
) {
    val colors = LumenTheme.colors
    val typography = LumenTheme.typography
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val fields = remember(type) { fieldsFor(type) }
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(prefill(type, initial)) } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val firstField = remember { FocusRequester() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching { copyPlaylist(context, uri) }.getOrNull() }
                if (saved == null) {
                    error = "That file couldn't be read."
                } else {
                    values["file"] = saved.first
                    values["fileName"] = saved.second
                    values["url"] = ""
                    error = null
                    notice = null
                }
            }
        }
    }

    // Back closes the system keyboard first (the system does that itself), then leaves the form.
    BackHandler(enabled = !busy) { onClose() }
    LaunchedEffect(Unit) { runCatching { firstField.requestFocus() } }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxHeight()
                .padding(top = 64.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LumenText(
                text = (if (initial == null) "Add " else "Edit ") + when (type) {
                    SourceType.XTREAM -> "Xtream Codes connection"
                    SourceType.M3U -> "M3U playlist"
                    SourceType.SELF_HOSTED -> "self-hosted server"
                },
                style = typography.title.copy(fontSize = 22.sp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fields.forEachIndexed { i, field ->
                    item(key = field.key) {
                        val last = i == fields.lastIndex && type != SourceType.SELF_HOSTED && type != SourceType.M3U
                        LumenTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = {
                                values[field.key] = it
                                // A typed link replaces an imported file.
                                if (field.key == "url" && it.isNotEmpty()) values["file"] = ""
                            },
                            placeholder = field.hint,
                            label = field.label,
                            masked = field.masked,
                            keyboardType = field.keyboard,
                            imeAction = if (last) ImeAction.Done else ImeAction.Next,
                            onImeAction = { if (!last) focusManager.moveFocus(FocusDirection.Down) },
                            focusRequester = if (i == 0) firstField else null,
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                }
                if (type == SourceType.SELF_HOSTED) {
                    item(key = "https") {
                        SettingToggleRow(
                            label = "Use HTTPS",
                            description = "Only if your server has a valid secure certificate.",
                            checked = values["https"] == "1",
                            onToggle = { values["https"] = if (values["https"] == "1") "0" else "1" },
                        )
                    }
                }
                if (type == SourceType.M3U) {
                    item(key = "file") {
                        val fileName = values["fileName"].orEmpty()
                        SettingActionRow(
                            label = if (values["file"].isNullOrEmpty()) "Import a playlist file" else "File: $fileName",
                            description = "Choose an .m3u or .m3u8 file on this device.",
                            onClick = {
                                try {
                                    picker.launch(arrayOf("*/*"))
                                } catch (e: ActivityNotFoundException) {
                                    error = "No file picker is available on this device. Use a playlist link instead."
                                }
                            },
                        )
                    }
                }
            }
            error?.let { LumenText(it, typography.label, color = Color(0xFFFF8A80)) }
            notice?.let { LumenText(it, typography.label, color = colors.accent) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LumenButton(
                    text = if (busy) "Working…" else if (initial == null) "Save" else "Save changes",
                    primary = true,
                    compact = true,
                    onClick = {
                        if (!busy) {
                            busy = true
                            error = null
                            notice = null
                            scope.launch {
                                val problem = onSubmit(values.toMap(), true)
                                busy = false
                                if (problem == null) onClose() else error = problem
                            }
                        }
                    },
                )
                LumenButton(
                    text = "Test",
                    compact = true,
                    onClick = {
                        if (!busy) {
                            busy = true
                            error = null
                            notice = null
                            scope.launch {
                                val problem = onSubmit(values.toMap(), false)
                                busy = false
                                if (problem == null) notice = "The connection works." else error = problem
                            }
                        }
                    },
                )
                LumenButton(text = "Cancel", compact = true, onClick = { if (!busy) onClose() })
            }
        }
    }
}

/** Copies a playlist the user picked into app storage. Returns the saved path and a display name. */
private fun copyPlaylist(context: Context, uri: Uri): Pair<String, String> {
    val directory = File(context.filesDir, "m3u").apply { mkdirs() }
    val target = File(directory, UUID.randomUUID().toString() + ".m3u")
    val input = context.contentResolver.openInputStream(uri) ?: error("The file could not be opened.")
    input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "Playlist file"
    return target.absolutePath to name
}
