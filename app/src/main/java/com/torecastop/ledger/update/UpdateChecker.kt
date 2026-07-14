package com.torecastop.ledger.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer build the team has published, as read from the release manifest. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String
)

/**
 * Checks for a newer sideloaded build. This app isn't on the Play Store, so
 * there's no auto-update — updates are hand-delivered (see README → "Getting
 * the APK onto phones"), which means phones can quietly fall behind.
 *
 * The check reads a small JSON manifest the team maintains somewhere public:
 *
 *     { "versionCode": 5, "versionName": "1.4", "url": "https://…/app-release.apk" }
 *
 * Host it wherever is convenient — a GitHub Release asset, a Gist, GitHub
 * Pages, Firebase Hosting. Set [MANIFEST_URL] to its address to switch the
 * feature on. Left blank, the check is a no-op and never touches the network,
 * so the app stays offline-first until someone opts in.
 *
 * Every failure (offline, blank URL, bad JSON, HTTP error) resolves to null:
 * a stale check must never get in the way of recording a sale.
 */
object UpdateChecker {

    // The release manifest, served straight from the (public) repo's main
    // branch. Bump update-manifest.json on each release — see README.
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/torecamart-droid/torecastop-ledger/main/update-manifest.json"

    private const val TIMEOUT_MS = 5_000

    /**
     * Returns the published build when it's newer than [currentVersionCode],
     * otherwise null. Safe to call on every launch.
     */
    suspend fun check(currentVersionCode: Int): UpdateInfo? {
        if (MANIFEST_URL.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    requestMethod = "GET"
                }
                val body = connection.use {
                    if (it.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                    it.inputStream.bufferedReader().use { reader -> reader.readText() }
                }
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName"),
                    url = json.getString("url")
                )
                info.takeIf { it.versionCode > currentVersionCode }
            }.getOrNull()
        }
    }

    /** HttpURLConnection isn't Closeable pre-API 19 semantics; wrap disconnect. */
    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
