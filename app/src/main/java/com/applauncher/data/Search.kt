package com.applauncher.data

/**
 * Ranks apps against what the user typed. Higher score = better match, null = no match.
 *
 * "chrome" -> exact      "chr" -> starts with      "play" -> a word starts with (Google Play)
 * "gm"     -> initials   "rom" -> contains         "ytm"  -> letters in order (YouTube Music)
 */
fun matchScore(label: String, query: String): Int? {
    val l = label.lowercase()
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    if (l == q) return 1000
    if (l.startsWith(q)) return 900 - l.length

    val words = l.split(' ', '-', '_', '.', ':').filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 800 - l.length

    val initials = words.joinToString("") { it.take(1) }
    if (initials.startsWith(q.replace(" ", ""))) return 700 - l.length

    val index = l.indexOf(q)
    if (index >= 0) return 600 - index

    var i = 0
    for (ch in l) if (i < q.length && ch == q[i]) i++
    if (i == q.length) return 300 - l.length

    return null
}

fun searchApps(apps: List<AppEntry>, query: String): List<AppEntry> =
    apps.mapNotNull { app -> matchScore(app.label, query)?.let { app to it } }
        .sortedByDescending { it.second }
        .map { it.first }
