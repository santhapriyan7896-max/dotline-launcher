package com.applauncher.system

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class NotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val smallIcon: Icon?,
    val contentIntent: PendingIntent?,
)

/** Current notifications, as seen by [MediaListenerService], plus actions on them. */
object NotificationStore {

    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Apps that should show a notification dot: they have a notification you can dismiss, and allow badges. */
    private val _badged = MutableStateFlow<Set<String>>(emptySet())
    val badged: StateFlow<Set<String>> = _badged.asStateFlow()

    private val _doNotDisturb = MutableStateFlow(false)
    val doNotDisturb: StateFlow<Boolean> = _doNotDisturb.asStateFlow()

    private var service: WeakReference<NotificationListenerService>? = null
    private val labels = HashMap<String, String>()

    internal fun attach(listener: NotificationListenerService) {
        service = WeakReference(listener)
        _connected.value = true
        refresh()
        updateDnd(listener.currentInterruptionFilter)
    }

    internal fun detach() {
        service = null
        _connected.value = false
        _badged.value = emptySet()
    }

    internal fun updateDnd(filter: Int) {
        _doNotDisturb.value = filter != NotificationListenerService.INTERRUPTION_FILTER_ALL &&
            filter != NotificationListenerService.INTERRUPTION_FILTER_UNKNOWN
    }

    internal fun refresh() {
        val listener = service?.get() ?: return
        val active = runCatching { listener.activeNotifications }.getOrNull() ?: return
        // Groups with several notifications also post a "summary"; hide it when its children are shown.
        val groupsWithChildren = active
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .map { it.groupKey }
            .toSet()
        _items.value = active
            .filterNot {
                it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 && it.groupKey in groupsWithChildren
            }
            .mapNotNull { toItem(listener, it) }
            .sortedByDescending { it.time }

        // Dots: dismissable notifications only (not music players or "USB debugging"),
        // and only for apps whose badge setting is on.
        val ranking = runCatching { listener.currentRanking }.getOrNull()
        val r = NotificationListenerService.Ranking()
        _badged.value = active
            .filter { it.isClearable }
            .filter { ranking == null || !ranking.getRanking(it.key, r) || r.canShowBadge() }
            .map { it.packageName }
            .toSet()
    }

    fun dismiss(key: String) {
        runCatching { service?.get()?.cancelNotification(key) }
    }

    fun dismissAll() {
        runCatching { service?.get()?.cancelAllNotifications() }
    }

    fun setDoNotDisturb(on: Boolean) {
        runCatching {
            service?.get()?.requestInterruptionFilter(
                if (on) NotificationListenerService.INTERRUPTION_FILTER_PRIORITY
                else NotificationListenerService.INTERRUPTION_FILTER_ALL
            )
        }
    }

    /** Opens what the notification points to, like tapping it in the system panel. */
    fun open(context: Context, item: NotificationItem) {
        val opened = item.contentIntent?.let { intent ->
            runCatching {
                val options = if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else null
                intent.send(context, 0, null, null, null, null, options)
            }.isSuccess
        } ?: false
        if (!opened) {
            context.packageManager.getLaunchIntentForPackage(item.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { runCatching { context.startActivity(it) } }
        }
        if (item.autoCancel) dismiss(item.key)
    }

    private fun toItem(context: Context, sbn: StatusBarNotification): NotificationItem? {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = label(context, sbn.packageName),
            title = title.trim(),
            text = text.trim(),
            time = sbn.postTime,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            smallIcon = n.smallIcon,
            contentIntent = n.contentIntent,
        )
    }

    @Suppress("DEPRECATION") // the newer overload needs Android 13
    private fun label(context: Context, pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }
}
