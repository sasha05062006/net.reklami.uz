package uz.reklami.zevs

import android.content.Context

/** Persistent user allow/deny rules plus counters for DNS-only protection mode. */
object FilterStore {
    private val builtIn = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "app-measurement.com",
        "facebook.net", "scorecardresearch.com", "adform.net",
        "adsrvr.org", "taboola.com", "outbrain.com", "unityads.unity3d.com"
    )

    private fun prefs(context: Context) = context.getSharedPreferences("zevs_filters", Context.MODE_PRIVATE)

    fun getDomains(context: Context, key: String): Set<String> =
        prefs(context).getStringSet(key, emptySet())?.toSet() ?: emptySet()

    fun addDomain(context: Context, key: String, raw: String): Boolean {
        val domain = raw.trim().trimEnd('.').lowercase()
        if (domain.length !in 3..253 || !domain.contains('.') ||
            !domain.matches(Regex("^[a-z0-9.-]+$")) ||
            domain.split('.').any { it.isBlank() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }
        ) return false
        val current = getDomains(context, key).toMutableSet()
        current.add(domain)
        prefs(context).edit().putStringSet(key, current).apply()
        return true
    }

    fun removeDomain(context: Context, key: String, domain: String) {
        val current = getDomains(context, key).toMutableSet()
        current.remove(domain)
        prefs(context).edit().putStringSet(key, current).apply()
    }

    fun isBlocked(context: Context, rawDomain: String): Boolean {
        val domain = rawDomain.trimEnd('.').lowercase()
        val allowed = getDomains(context, "allow_domains")
        if (allowed.any { domain == it || domain.endsWith(".$it") }) return false
        val denied = getDomains(context, "deny_domains")
        return builtIn.any { domain == it || domain.endsWith(".$it") } ||
            denied.any { domain == it || domain.endsWith(".$it") }
    }

    fun recordQuery(context: Context, blocked: Boolean) {
        val p = prefs(context)
        val editor = p.edit().putLong("query_count", p.getLong("query_count", 0L) + 1L)
        if (blocked) editor.putLong("blocked_count", p.getLong("blocked_count", 0L) + 1L)
        editor.apply()
    }

    fun blockedCount(context: Context): Long = prefs(context).getLong("blocked_count", 0L)
    fun queryCount(context: Context): Long = prefs(context).getLong("query_count", 0L)
}
