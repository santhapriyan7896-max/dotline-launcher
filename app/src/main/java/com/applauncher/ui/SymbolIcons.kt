@file:Suppress("DEPRECATION") // a few icons also exist in a mirrored version; the plain ones are fine here

package com.applauncher.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** White symbols you can give any app, plus automatic picks for preinstalled apps without a good icon. */
object SymbolIcons {

    /** Name -> symbol. The names are saved in settings, so don't rename them. */
    val all: List<Pair<String, ImageVector>> = listOf(
        "call" to Icons.Rounded.Call,
        "chat" to Icons.Rounded.Chat,
        "forum" to Icons.Rounded.Forum,
        "mail" to Icons.Rounded.Mail,
        "person" to Icons.Rounded.Person,
        "group" to Icons.Rounded.Group,
        "camera" to Icons.Rounded.PhotoCamera,
        "video" to Icons.Rounded.Videocam,
        "photo" to Icons.Rounded.Photo,
        "gallery" to Icons.Rounded.Collections,
        "music" to Icons.Rounded.MusicNote,
        "headphones" to Icons.Rounded.Headphones,
        "movie" to Icons.Rounded.Movie,
        "tv" to Icons.Rounded.Tv,
        "radio" to Icons.Rounded.Radio,
        "podcast" to Icons.Rounded.Podcasts,
        "mic" to Icons.Rounded.Mic,
        "clock" to Icons.Rounded.Schedule,
        "alarm" to Icons.Rounded.Alarm,
        "calendar" to Icons.Rounded.CalendarMonth,
        "calculator" to Icons.Rounded.Calculate,
        "notes" to Icons.Rounded.StickyNote2,
        "book" to Icons.Rounded.MenuBook,
        "news" to Icons.Rounded.Newspaper,
        "school" to Icons.Rounded.School,
        "work" to Icons.Rounded.Work,
        "folder" to Icons.Rounded.Folder,
        "download" to Icons.Rounded.Download,
        "cloud" to Icons.Rounded.Cloud,
        "scanner" to Icons.Rounded.DocumentScanner,
        "qr" to Icons.Rounded.QrCodeScanner,
        "browser" to Icons.Rounded.Public,
        "language" to Icons.Rounded.Language,
        "translate" to Icons.Rounded.Translate,
        "search" to Icons.Rounded.Search,
        "map" to Icons.Rounded.Map,
        "compass" to Icons.Rounded.Explore,
        "flight" to Icons.Rounded.Flight,
        "taxi" to Icons.Rounded.LocalTaxi,
        "food" to Icons.Rounded.Restaurant,
        "shop" to Icons.Rounded.ShoppingBag,
        "store" to Icons.Rounded.Storefront,
        "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "pay" to Icons.Rounded.Payments,
        "rupee" to Icons.Rounded.CurrencyRupee,
        "game" to Icons.Rounded.SportsEsports,
        "health" to Icons.Rounded.Favorite,
        "fitness" to Icons.Rounded.FitnessCenter,
        "weather" to Icons.Rounded.WbSunny,
        "torch" to Icons.Rounded.FlashlightOn,
        "settings" to Icons.Rounded.Settings,
        "phone" to Icons.Rounded.PhoneAndroid,
        "sim" to Icons.Rounded.SimCard,
        "wifi" to Icons.Rounded.Wifi,
        "bluetooth" to Icons.Rounded.Bluetooth,
        "security" to Icons.Rounded.Shield,
        "lock" to Icons.Rounded.Lock,
        "key" to Icons.Rounded.Key,
        "theme" to Icons.Rounded.Palette,
        "code" to Icons.Rounded.Code,
        "terminal" to Icons.Rounded.Terminal,
        "home" to Icons.Rounded.Home,
        "star" to Icons.Rounded.Star,
        "bolt" to Icons.Rounded.Bolt,
    )

    private val byName = all.toMap()

    fun named(name: String): ImageVector? = byName[name]

    /** Words in a preinstalled app's name -> symbol. Checked whole-word, in this order. */
    private val keywords = listOf(
        "camera" to "camera", "calculator" to "calculator", "clock" to "clock", "alarm" to "alarm",
        "calendar" to "calendar", "settings" to "settings", "files" to "folder", "file" to "folder",
        "notes" to "notes", "music" to "music", "video" to "video", "videos" to "movie", "player" to "movie",
        "email" to "mail", "mail" to "mail", "browser" to "browser", "maps" to "map", "weather" to "weather",
        "compass" to "compass", "contacts" to "person", "store" to "store", "games" to "game",
        "health" to "health", "fitness" to "fitness", "wallet" to "wallet", "recorder" to "mic",
        "radio" to "radio", "gallery" to "gallery", "albums" to "gallery", "photos" to "photo",
        "themes" to "theme", "theme" to "theme", "scanner" to "scanner", "phone" to "call",
        "messages" to "chat", "torch" to "torch", "flashlight" to "torch", "translate" to "translate",
        "security" to "security", "cloud" to "cloud", "downloads" to "download", "sim" to "sim",
        "feedback" to "chat", "manager" to "settings",
    )

    /** An automatic symbol for a preinstalled app, by the words in its name. */
    fun forLabel(label: String): String? {
        val words = label.lowercase().split(' ', '-', '_', '.').filter { it.isNotEmpty() }.toSet()
        return keywords.firstOrNull { it.first in words }?.second
    }
}
