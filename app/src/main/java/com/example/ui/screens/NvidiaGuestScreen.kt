package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.NimChatRequest
import com.example.data.remote.NimMessage
import com.example.data.remote.NvidiaNimClient
import kotlinx.coroutines.launch

/**
 * Local NVIDIA-only fallback: no Firebase credentials are needed to open and use chat.
 * The API key lives in Android Keystore encrypted preferences, never in the APK.
 */
@Composable
fun NvidiaGuestScreen(onExit: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<NimMessage>() }
    val scroll = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var keyEntry by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(NvidiaNimClient.isConfigured()) }
    var model by rememberSaveable { mutableStateOf("nvidia/nemotron-3-ultra-550b-a55b") }
    var isSending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scroll.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D18))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Aura AI", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text("NVIDIA NIM • Direct Chat", color = Color(0xFF8ACCD7))
            }
            if (onExit != null) TextButton(onClick = onExit) {
                Text("Sign in", color = Color(0xFF8ACCD7))
            }
        }

        Text(
            if (configured) "NVIDIA API key saved securely on this device"
            else "Set up your own NVIDIA API key to use chat.",
            color = if (configured) Color(0xFF87DBA2) else Color(0xFFCCD4E4),
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("NVIDIA model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (!configured) {
            OutlinedTextField(
                value = keyEntry,
                onValueChange = { keyEntry = it },
                label = { Text("NVIDIA NIM API key (nvapi-...)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    try {
                        NvidiaNimClient.saveKey(context, keyEntry)
                        keyEntry = ""
                        configured = NvidiaNimClient.isConfigured()
                        status = if (configured) "Key saved. Your chat is ready."
                                 else "Enter an API key."
                    } catch (_: Exception) {
                        status = "Could not save the API key on this device."
                    }
                },
                enabled = keyEntry.isNotBlank()
            ) {
                Text("Save API key")
            }
        } else {
            TextButton(onClick = {
                NvidiaNimClient.saveKey(context, "")
                configured = false
                status = "API key removed from this device."
            }) {
                Text("Remove saved API key")
            }
        }

        if (status.isNotBlank()) {
            Text(status, color = Color(0xFFE7B96F), style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            state = scroll,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) item {
                Text(
                    "Type a message below. NVIDIA-only mode works without Google login. " +
                        "Cloud tasks and Firebase features require separate setup.",
                    color = Color(0xFFA7B5C9),
                    modifier = Modifier.padding(vertical = 18.dp)
                )
            }
            itemsIndexed(messages) { _, message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.role == "user") Color(0xFF172C47)
                                         else Color(0xFF192332)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            if (message.role == "user") "You" else "Aura AI",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF88C6F8),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(message.content, color = Color.White)
                    }
                }
            }
        }

        if (isSending) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Text("AI is responding...", color = Color(0xFFCCDAE9))
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Message Aura AI") },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = configured && !isSending && input.isNotBlank() && model.isNotBlank(),
            onClick = {
                val question = input.trim()
                input = ""
                messages.add(NimMessage("user", question))
                val history = listOf(
                    NimMessage("system", "You are Aura AI. Respond in the user's language, clearly and accurately.")
                ) + messages.takeLast(12)
                isSending = true
                status = ""
                scope.launch {
                    try {
                        val response = NvidiaNimClient.service.chatCompletion(
                            NvidiaNimClient.authHeader(),
                            NimChatRequest(model = model.trim(), messages = history)
                        )
                        val reply = response.choices?.firstOrNull()?.message?.content.orEmpty()
                        messages.add(NimMessage("assistant", reply.ifBlank {
                            "The selected NVIDIA model returned no text. Try another model ID."
                        }))
                    } catch (e: Exception) {
                        val error = e.localizedMessage.orEmpty().take(180)
                        status = "NVIDIA request failed: $error"
                    } finally {
                        isSending = false
                    }
                }
            }
        ) {
            Text("Send to NVIDIA NIM")
        }
    }
}
