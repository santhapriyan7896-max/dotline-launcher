package com.applauncher.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.applauncher.ui.mono.Mono
import com.applauncher.ui.mono.MonoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** The Phone app: keypad, recent calls and contacts. */
class DialerActivity : ComponentActivity() {

    /** Number handed to us by another app ("tel:" links, Dial buttons). */
    private val incomingNumber = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        incomingNumber.value = numberFrom(intent)
        setContent {
            MonoTheme { DialerScreen(incomingNumber) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberFrom(intent)?.let { incomingNumber.value = it }
    }

    private fun numberFrom(intent: Intent?): String? =
        intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
}

private val PERMISSIONS = buildList {
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private enum class Tab(val label: String) { Keypad("KEYPAD"), Recents("RECENTS"), Contacts("CONTACTS") }

@Composable
private fun DialerScreen(incomingNumber: MutableStateFlow<String?>) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.Recents) }
    var number by rememberSaveable { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var isDefault by remember { mutableStateOf(isDefaultDialer(context)) }

    // A number from a link or another app: show it on the keypad.
    val handed by androidx.compose.runtime.produceState<String?>(null) {
        incomingNumber.collect { value = it }
    }
    androidx.compose.runtime.LaunchedEffect(handed) {
        handed?.let {
            number = it
            tab = Tab.Keypad
            incomingNumber.value = null
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        reload++
    }
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultDialer(context)
        reload++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = isDefaultDialer(context)
        reload++
    }

    val hasLog = PhoneData.has(context, Manifest.permission.READ_CALL_LOG)
    val hasContacts = PhoneData.has(context, Manifest.permission.READ_CONTACTS)
    val recents by produceState(emptyList<RecentCall>(), reload) {
        value = withContext(Dispatchers.IO) { PhoneData.recentCalls(context) }
    }
    val contacts by produceState(emptyList<ContactNumber>(), reload) {
        value = withContext(Dispatchers.IO) { PhoneData.contacts(context) }
    }
    val favourites by produceState(emptyList<ContactNumber>(), reload) {
        value = withContext(Dispatchers.IO) { PhoneData.favourites(context) }
    }

    fun call(target: String) {
        if (target.isBlank()) return
        if (!PhoneData.has(context, Manifest.permission.CALL_PHONE)) {
            permissions.launch(PERMISSIONS)
            return
        }
        placeCall(context, target)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mono.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        // Tabs
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Tab.entries.forEach { t ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { tab = t }.padding(vertical = 6.dp),
                ) {
                    Text(t.label, style = Mono.label.copy(fontSize = 13.sp, color = if (tab == t) Mono.White else Mono.Grey))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.size(5.dp).clip(RoundedCornerShape(50))
                            .background(if (tab == t) Mono.Red else Mono.Black)
                    )
                }
            }
        }

        if (!isDefault) {
            Text(
                "SET AS DEFAULT PHONE APP  →",
                style = Mono.label.copy(color = Mono.Red, fontSize = 11.sp),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .clickable { requestDefaultDialer(context, roleRequest::launch) },
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Keypad -> KeypadTab(
                    number = number,
                    onNumber = { number = it },
                    contacts = contacts,
                    lastDialed = recents.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.number,
                    onCall = ::call,
                )
                Tab.Recents -> if (!hasLog) {
                    AskAccess("See your recent calls") { permissions.launch(PERMISSIONS) }
                } else {
                    RecentsTab(recents, favourites, onPick = { number = it; tab = Tab.Keypad }, onCall = ::call)
                }
                Tab.Contacts -> if (!hasContacts) {
                    AskAccess("See your contacts") { permissions.launch(PERMISSIONS) }
                } else {
                    ContactsTab(contacts, onPick = { number = it; tab = Tab.Keypad }, onCall = ::call)
                }
            }
        }
    }
}

@Composable
private fun KeypadTab(
    number: String,
    onNumber: (String) -> Unit,
    contacts: List<ContactNumber>,
    lastDialed: String?,
    onCall: (String) -> Unit,
) {
    val digits = PhoneData.digitsOnly(number)
    val match = remember(digits, contacts) {
        if (digits.length < 3) null
        else contacts.firstOrNull { PhoneData.digitsOnly(it.number).contains(digits) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(48.dp))
            Text(
                number,
                color = Mono.White,
                fontSize = if (number.length > 12) 28.sp else 36.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (number.isNotEmpty()) {
                    RoundButton(
                        size = 44.dp, color = Mono.Black,
                        onClick = { onNumber(number.dropLast(1)) },
                        onLongClick = { onNumber("") },
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Delete", tint = Mono.Grey)
                    }
                }
            }
        }
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            if (match != null) {
                Text(
                    "${match.name}  ·  ${match.number}",
                    style = Mono.label.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onNumber(match.number) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Keypad(onKey = { onNumber(number + it) })
        Spacer(Modifier.height(20.dp))
        RoundButton(
            size = 76.dp,
            color = Mono.Red,
            // Empty keypad + call = bring back the last number you dialled.
            onClick = { if (number.isBlank()) lastDialed?.let(onNumber) else onCall(number) },
        ) { CallIcon(Mono.White) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecentsTab(
    recents: List<RecentCall>,
    favourites: List<ContactNumber>,
    onPick: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    val grouped = remember(recents) { PhoneData.group(recents) }
    if (grouped.isEmpty() && favourites.isEmpty()) {
        Message("No recent calls")
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (favourites.isNotEmpty()) {
            item(key = "favourites") { FavouritesRow(favourites, onCall) }
        }
        items(grouped) { (r, count) ->
            val (label, color) = when (r.type) {
                CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> "MISSED" to Mono.Red
                CallLog.Calls.OUTGOING_TYPE -> "OUT" to Mono.Grey
                else -> "IN" to Mono.Grey
            }
            val who = r.name ?: r.number.ifBlank { "Unknown" }
            PersonRow(
                title = if (count > 1) "$who ($count)" else who,
                titleColor = if (label == "MISSED") Mono.Red else Mono.White,
                subtitle = "$label  ·  ${timeAgo(r.time)}",
                subtitleColor = color,
                onClick = { onPick(r.number) },
                onCall = { onCall(r.number) },
            )
        }
    }
}

/** Starred contacts as round initials; tap to call. */
@Composable
private fun FavouritesRow(favourites: List<ContactNumber>, onCall: (String) -> Unit) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text("FAVOURITES", style = Mono.label.copy(fontSize = 11.sp), modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
            items(favourites, key = { it.contactId }) { f ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                    RoundButton(size = 60.dp, color = Mono.Circle, onClick = { onCall(f.number) }) {
                        Text(f.name.firstOrNull { it.isLetter() }?.uppercase() ?: "#", color = Mono.White, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(f.name.substringBefore(' '), color = Mono.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(1.dp).background(Mono.Dim))
    }
}

@Composable
private fun ContactsTab(contacts: List<ContactNumber>, onPick: (String) -> Unit, onCall: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts
        else contacts.filter { it.name.lowercase().contains(q) || PhoneData.digitsOnly(it.number).contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(Mono.Circle)
                .padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Mono.Grey, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Mono.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Mono.Red),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    Box {
                        if (query.isEmpty()) Text("Search contacts", color = Mono.Grey, fontSize = 16.sp)
                        field()
                    }
                },
            )
        }
        if (shown.isEmpty()) {
            Message(if (contacts.isEmpty()) "No contacts" else "No match")
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(shown) { c ->
                    PersonRow(
                        title = c.name,
                        subtitle = c.number,
                        onClick = { onPick(c.number) },
                        onCall = { onCall(c.number) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onCall: () -> Unit,
    titleColor: androidx.compose.ui.graphics.Color = Mono.White,
    subtitleColor: androidx.compose.ui.graphics.Color = Mono.Grey,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(50)).background(Mono.Circle), contentAlignment = Alignment.Center) {
            Text(title.firstOrNull { it.isLetter() }?.uppercase() ?: "#", color = Mono.White, fontSize = 17.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = Mono.label.copy(fontSize = 10.sp, letterSpacing = 1.sp, color = subtitleColor), maxLines = 1)
        }
        RoundButton(size = 44.dp, color = Mono.Circle, onClick = onCall) { CallIcon(Mono.White, size = 20.dp) }
    }
}

@Composable
private fun AskAccess(text: String, onAllow: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Mono.Grey, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "ALLOW ACCESS",
            style = Mono.label.copy(color = Mono.White),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Mono.Tile)
                .clickable(onClick = onAllow)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Mono.Grey, fontSize = 15.sp)
    }
}

private fun isDefaultDialer(context: Context): Boolean =
    context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName

private fun requestDefaultDialer(context: Context, launch: (Intent) -> Unit) {
    val intent = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    } else {
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
    }
    intent?.let { runCatching { launch(it) } }
}

@SuppressLint("MissingPermission") // checked by the caller before placing the call
private fun placeCall(context: Context, number: String) {
    val telecom = context.getSystemService(TelecomManager::class.java)
    val ok = runCatching { telecom?.placeCall(Uri.fromParts("tel", number, null), Bundle()) }.isSuccess
    if (!ok) Toast.makeText(context, "Couldn't place the call", Toast.LENGTH_SHORT).show()
}
