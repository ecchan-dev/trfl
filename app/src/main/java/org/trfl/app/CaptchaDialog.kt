package org.trfl.app

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun CaptchaControl(target: PostingDraftKey, transport: CaptchaTransport = remember { HttpCaptchaTransport() }) {
    var open by remember(target) { mutableStateOf(false) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("Load CAPTCHA") }
        return
    }
    var challenge by remember(target) { mutableStateOf<CaptchaChallenge?>(null) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var answer by remember(target) { mutableStateOf<CaptchaAnswer?>(null) }
    var choices by remember(target) { mutableStateOf<List<Int>>(emptyList()) }
    var now by remember(target) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(target) {
        val started = SystemClock.elapsedRealtime()
        val result = withContext(Dispatchers.IO) { runCatching { CaptchaParser.parse(transport.load(target), target, started) } }
        result.onSuccess { challenge = it; choices = List(it.tasks.size) { -1 } }.onFailure { error = "CAPTCHA unavailable or unsupported. No post was sent." }
    }
    LaunchedEffect(challenge) {
        while (challenge != null) {
            now = SystemClock.elapsedRealtime()
            if (challenge?.expired(now) == true) { challenge = null; answer = null; error = "CAPTCHA expired. Close and load another."; break }
            delay(500)
        }
    }
    AlertDialog(onDismissRequest = { open = false }, title = { Text("CAPTCHA") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Experimental and live-unverified. Posting remains disabled.")
            error?.let { Text(it) }
            if (challenge == null && error == null) Text("Loading…")
            challenge?.let { current ->
                current.tasks.forEachIndexed { taskIndex, task ->
                    task.title?.let { Text(it) }
                    task.titleImage?.let { CaptchaImage(it, "Task ${taskIndex + 1} prompt") }
                    task.images.forEachIndexed { index, bytes ->
                        Row(Modifier.fillMaxWidth().clickable { choices = choices.toMutableList().also { it[taskIndex] = index }; answer = null }.semantics { contentDescription = "Task ${taskIndex + 1}, choice ${index + 1}" }) {
                            RadioButton(selected = choices[taskIndex] == index, onClick = null)
                            CaptchaImage(bytes, "Choice ${index + 1}")
                        }
                    }
                }
                if (answer != null) Text("CAPTCHA choices ready in memory. Posting is disabled.")
            }
        }
    }, confirmButton = {
        TextButton(enabled = challenge != null && choices.isNotEmpty() && choices.all { it >= 0 } && answer == null, onClick = {
            answer = challenge?.answer(target, choices, SystemClock.elapsedRealtime())
            if (answer == null) { challenge = null; error = "CAPTCHA expired. Close and load another." }
        }) { Text("Confirm choices") }
    }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}

@Composable
private fun CaptchaImage(bytes: ByteArray, description: String) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    bitmap?.let { Image(it, description, Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 160.dp)) }
}
