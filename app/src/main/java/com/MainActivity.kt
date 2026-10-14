package com.kustom.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

data class Message(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeminiStyleTheme {
                GeminiChatScreen()
            }
        }
    }
}

@Composable
fun GeminiStyleTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        background = Color(0xFF131314),
        surface = Color(0xFF1E1F22),
        surfaceVariant = Color(0xFF282A2D),
        onBackground = Color(0xFFE3E2E6),
        primary = Color(0xFFA8C7FA)
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiChatScreen() {
    var apiUrl by remember { mutableStateOf("https://your-cloudflare-url.trycloudflare.com/generate") }
    var inputQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<Message>() }
    var isLoading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Llama 3 • Gemini Style", fontSize = 18.sp, color = Color(0xFFE3E2E6)) },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Config", tint = Color(0xFFC4C6D0))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF131314))
            )
        },
        containerColor = Color(0xFF131314)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Halo, ada yang bisa saya bantu hari ini?",
                                color = Color(0xFF8E918F),
                                fontSize = 20.sp
                            )
                        }
                    }
                }
                items(messages) { msg ->
                    ChatBubble(message = msg)
                }
                if (isLoading) {
                    item {
                        Text(
                            text = "✨ Memikirkan jawaban...",
                            color = Color(0xFFA8C7FA),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Gemini Bottom Input Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF1E1F22)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Tanyakan sesuatu...", color = Color(0xFF8E918F)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color(0xFFE3E2E6)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (inputQuery.isNotBlank() && !isLoading) {
                                val userText = inputQuery
                                messages.add(Message(userText, isUser = true))
                                inputQuery = ""
                                isLoading = true

                                sendPromptToKaggle(apiUrl, userText) { result ->
                                    isLoading = false
                                    messages.add(Message(result, isUser = false))
                                }
                            }
                        },
                        modifier = Modifier
                            .background(if (inputQuery.isNotBlank()) Color(0xFFA8C7FA) else Color(0xFF282A2D), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputQuery.isNotBlank()) Color(0xFF00315B) else Color(0xFF8E918F)
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Pengaturan API Kaggle") },
            text = {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    label = { Text("URL Cloudflare") }
                )
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Simpan")
                }
            }
        )
    }
}

@Composable
fun ChatBubble(message: Message) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = if (message.isUser) Color(0xFF2A2B2E) else Color(0xFF1E1F22),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            )
        ) {
            Text(
                text = message.text,
                color = Color(0xFFE3E2E6),
                fontSize = 15.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

fun sendPromptToKaggle(url: String, prompt: String, onResult: (String) -> Unit) {
    val client = OkHttpClient()
    val json = JSONObject().apply {
        put("prompt", prompt)
        put("max_tokens", 256)
    }
    val body = json.toString().toRequestBody("application/json".toMediaType())
    val targetUrl = if (url.endsWith("/generate")) url else "$url/generate"

    val request = Request.Builder().url(targetUrl).post(body).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onResult("⚠️ Gagal terhubung ke server Kaggle: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()
            if (response.isSuccessful && responseBody != null) {
                try {
                    val reply = JSONObject(responseBody).getString("response")
                    onResult(reply)
                } catch (e: Exception) {
                    onResult(responseBody)
                }
            } else {
                onResult("Error ${response.code}: $responseBody")
            }
        }
    })
}