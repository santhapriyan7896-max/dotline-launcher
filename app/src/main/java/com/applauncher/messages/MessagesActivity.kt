package com.applauncher.messages

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applauncher.phone.RoundButton
import com.applauncher.phone.timeAgo
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Messages: read your SMS conversations and reply, in the launcher's style.
 * Google Messages stays the default SMS app (it keeps receiving and notifying).
 */
class MessagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val startAddress = intent?.data?.schemeSpecificPart?.substringBefore('?')
        setContent { MonoTheme { MessagesApp(startAddress) } }
    }
}

private val PERMISSIONS = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_CONTACTS,
)

/** Which screen is showing. */
private sealed interface Screen {
    data object List : Screen
    data class Thread(val threadId: Long?, val address: String, val title: String) : Screen
    data object New : Screen
}

@Composable
private fun MessagesApp(startAddress: String?) {
    val context = LocalContext.current
    var screen by remember {
        mutableStateOf<Screen>(if (startAddress.isNullOrBlank()) Screen.List else Screen.Thread(null, startAddress, startAddress))
    }
    // Goes up whenever the phone's message store changes (new text received or sent).
    var changes by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(SmsData.canRead(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = SmsData.canRead(context)
        changes++
    }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { changes++ }
        }
        runCatching { context.contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer) }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    BackHandler(enabled = screen != Screen.List) { screen = Screen.List }

    Box(Modifier.fillMaxSize().background(Mono.Black).windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        when {
            !allowed -> AskAccess { ask.launch(PERMISSIONS) }
            else -> when (val s = screen) {
                Screen.List -> ConversationList(
                    changes = changes,
                    onOpen = { screen = Screen.Thread(it.threadId, it.address, it.title) },
                    onNew = { screen = Screen.New },
                )
                is Screen.Thread -> ThreadScreen(s, changes, onBack = { screen = Screen.List }, onAskSend = { ask.launch(PERMISSIONS) })
                Screen.New -> NewMessage(
                    onBack = { screen = Screen.List },
                    onStart = { address -> screen = Screen.Thread(null, address, address) },
                )
            }
        }
    }
}

@Composable
private fun ConversationList(changes: Int, onOpen: (Conversation) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val conversations by produceState<List<Conversation>?>(null, changes) {
        delay(150) // several changes often arrive together
        value = withContext(Dispatchers.IO) { SmsData.conversations(context) }
    }
    val shown = remember(conversations, query) {
        val q = query.trim().lowercase()
        conversations.orEmpty().filter {
            q.isEmpty() || it.title.lowercase().contains(q) || it.snippet.lowercase().contains(q)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MESSAGES", style = Mono.label.copy(color = Mono.White, fontSize = 16.sp), modifier = Modifier.weight(1f))
            RoundButton(size = 44.dp, color = Mono.Red, onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "New message", tint = Mono.White)
            }
        }
        SearchPill(query, onChange = { query = it }, hint = "Search messages")
        when {
            conversations == null -> Centered("Loading…")
            shown.isEmpty() -> Centered(if (query.isBlank()) "No messages" else "No match")
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(shown, key = { it.threadId }) { c -> ConversationRow(c) { onOpen(c) } }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Mono.Circle), contentAlignment = Alignment.Center) {
            Text(
                c.title.firstOrNull { it.isLetter() }?.uppercase() ?: "#",
                color = Mono.White, fontSize = 18.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title, color = Mono.White, fontSize = 16.sp,
                    fontWeight = if (c.unread) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(timeAgo(c.date), style = Mono.label.copy(fontSize = 9.sp, letterSpacing = 1.sp))
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (c.fromMe) "You: " else "") + c.snippet,
                    color = if (c.unread) Mono.White.copy(alpha = 0.85f) else Mono.Grey,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (c.unread) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Mono.Red))
                }
            }
        }
    }
}

@Composable
private fun ThreadScreen(screen: Screen.Thread, changes: Int, onBack: () -> Unit, onAskSend: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(screen.address) { mutableStateOf("") }
    // A brand-new conversation gets its id after the first message is saved.
    val threadId by produceState(screen.threadId, changes) {
        value = screen.threadId ?: withContext(Dispatchers.IO) { SmsData.threadFor(context, screen.address) }
    }
    val messages by produceState(emptyList<Sms>(), threadId, changes) {
        val id = threadId
        value = if (id == null) emptyList() else withContext(Dispatchers.IO) { SmsData.messages(context, id) }
    }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.scrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            RoundButton(size = 44.dp, color = Mono.Black, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Mono.White)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(screen.title, color = Mono.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (screen.title != screen.address) Text(screen.address, style = Mono.label.copy(fontSize = 10.sp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Mono.Dim))

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { m -> Bubble(m) }
        }

        // Reply bar
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(color = Mono.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Mono.Red),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Mono.Circle)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
                decorationBox = { field ->
                    Box {
                        if (draft.isEmpty()) Text("Text message", color = Mono.Grey, fontSize = 16.sp)
                        field()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            RoundButton(
                size = 48.dp,
                color = if (draft.isBlank()) Mono.Circle else Mono.Red,
                onClick = {
                    val text = draft.trim()
                    if (text.isEmpty()) return@RoundButton
                    if (!SmsData.canSend(context)) { onAskSend(); return@RoundButton }
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { SmsData.send(context, screen.address, text) }
                        if (ok) draft = "" else Toast.makeText(context, "Couldn't send", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Mono.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** One message. Yours are white on the right; theirs dark on the left. Long-press to copy. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Sms) {
    val context = LocalContext.current
    val code = remember(m.body) { if (m.fromMe) null else SmsData.findCode(m.body) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
        Text(
            m.body,
            color = if (m.fromMe) Mono.Ink else Mono.White,
            fontSize = 15.sp,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (m.fromMe) Mono.White else Mono.Tile)
                .combinedClickable(onClick = {}, onLongClick = { copy(context, m.body, "Message copied") })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (code != null) {
                Text(
                    "COPY $code",
                    style = Mono.label.copy(fontSize = 10.sp, color = Mono.Red),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { copy(context, code, "Code copied") }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (m.failed) "NOT SENT" else timeOf(m.date),
                style = Mono.label.copy(fontSize = 9.sp, letterSpacing = 1.sp, color = if (m.failed) Mono.Red else Mono.Grey),
            )
        }
    }
}

@Composable
private fun NewMessage(onBack: () -> Unit, onStart: (String) -> Unit) {
    var to by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            RoundButton(size = 44.dp, color = Mono.Black, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Mono.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("NEW MESSAGE", style = Mono.label.copy(color = Mono.White, fontSize = 14.sp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(50)).background(Mono.Circle).padding(start = 18.dp, end = 6.dp),
        ) {
            Text("TO", style = Mono.label.copy(fontSize = 11.sp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = to,
                onValueChange = { to = it },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Mono.Red),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box {
                        if (to.isEmpty()) Text("Phone number", color = Mono.Grey, fontSize = 17.sp)
                        field()
                    }
                },
            )
            RoundButton(size = 42.dp, color = if (to.isBlank()) Mono.Tile else Mono.Red, onClick = { if (to.isNotBlank()) onStart(to.trim()) }) {
                Text("→", color = Mono.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit, hint: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(50)).background(Mono.Circle).padding(horizontal = 16.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Mono.Grey, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Mono.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Mono.Red),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text(hint, color = Mono.Grey, fontSize = 16.sp)
                    field()
                }
            },
        )
    }
}

@Composable
private fun AskAccess(onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MESSAGES", style = Mono.label.copy(color = Mono.White, fontSize = 16.sp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to read and send your texts.\nGoogle Messages keeps receiving them as usual.",
            color = Mono.Grey, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "ALLOW ACCESS",
            style = Mono.label.copy(color = Mono.White),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Mono.Tile).clickable(onClick = onAllow)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Mono.Grey, fontSize = 15.sp) }
}

private fun copy(context: Context, text: String, done: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Message", text))
    Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
}

private fun timeOf(date: Long): String {
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(date))
    val pattern = if (day == today) "h:mm a" else "dd MMM, h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(date)).uppercase()
}
