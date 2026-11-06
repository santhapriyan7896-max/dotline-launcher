package com.applauncher.messages

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.applauncher.phone.PhoneData

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
    val fromMe: Boolean,
) {
    val title: String get() = name ?: address
}

data class Sms(
    val id: Long,
    val body: String,
    val date: Long,
    val type: Int, // Telephony.Sms.MESSAGE_TYPE_INBOX / SENT / OUTBOX / FAILED ...
) {
    val fromMe: Boolean get() = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
}

/**
 * Reads SMS from the phone's message store and sends new ones.
 * Google Messages stays the default SMS app, so it still receives, saves and notifies.
 */
object SmsData {

    fun canRead(context: Context) = has(context, Manifest.permission.READ_SMS)
    fun canSend(context: Context) = has(context, Manifest.permission.SEND_SMS)

    private fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Newest conversation first. Call off the main thread. */
    fun conversations(context: Context, maxMessages: Int = 4000): List<Conversation> {
        if (!canRead(context)) return emptyList()
        val latest = LinkedHashMap<Long, Conversation>()
        val unread = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                var seen = 0
                while (c.moveToNext() && seen++ < maxMessages) {
                    val thread = c.getLong(0)
                    if (c.getInt(4) == 0) unread += thread
                    if (thread in latest) continue
                    latest[thread] = Conversation(
                        threadId = thread,
                        address = c.getString(1).orEmpty(),
                        name = null,
                        snippet = c.getString(2).orEmpty().replace('\n', ' '),
                        date = c.getLong(3),
                        unread = false,
                        fromMe = c.getInt(5) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        }
        return latest.values.map { conv ->
            conv.copy(
                name = PhoneData.nameFor(context, conv.address),
                unread = conv.threadId in unread,
            )
        }
    }

    /** Oldest first. Call off the main thread. */
    fun messages(context: Context, threadId: Long): List<Sms> {
        if (!canRead(context)) return emptyList()
        val result = ArrayList<Sms>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    result += Sms(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3))
                }
            }
        }
        return result
    }

    /** The conversation with [address], if there is one. Call off the main thread. */
    fun threadFor(context: Context, address: String): Long? {
        val wanted = PhoneData.digitsOnly(address).takeLast(10)
        if (wanted.isEmpty()) return null
        return conversations(context).firstOrNull { PhoneData.digitsOnly(it.address).endsWith(wanted) }?.threadId
    }

    /** Sends [text] to [address]. Long texts are split into parts automatically. */
    fun send(context: Context, address: String, text: String): Boolean {
        if (!canSend(context) || address.isBlank() || text.isBlank()) return false
        return runCatching {
            val manager: SmsManager = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION") SmsManager.getDefault()
            }
            val parts = manager.divideMessage(text)
            val sent = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, SmsSentReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.sendMultipartTextMessage(address, null, parts, ArrayList(List(parts.size) { sent }), null)
        }.isSuccess
    }

    // 4–8 digits on their own. Skips parts of amounts like "20.00" or "1,500" but allows "121572." at a sentence end.
    private val otp = Regex("""(?<!\d)(?<!\d[.,])(\d{4,8})(?!\d)(?![.,]\d)""")
    private val otpWords = Regex("(?i)\\b(otp|code|verification|verify|one[- ]time|passcode|pin)\\b")

    /** A one-time code in the message, if it looks like one. */
    fun findCode(body: String): String? =
        if (otpWords.containsMatchIn(body)) otp.find(body)?.groupValues?.get(1) else null
}
