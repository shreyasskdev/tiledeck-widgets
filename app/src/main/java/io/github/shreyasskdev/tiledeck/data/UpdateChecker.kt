package io.github.shreyasskdev.tiledeck.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    // Throttle config
    private const val PREFS = "update_check_prefs"
    private const val KEY_LAST_CHECK = "last_check"
    private const val MIN_INTERVAL_MS = 60 * 60 * 1000L   // 1 hour

    /**
     * Public entry point. Throttles checks so we don't hammer the GitHub API
     * on every single launch. Pass `force = true` to bypass the throttle
     * (useful for a manual "Check for updates" button).
     */
    suspend fun check(context: Context, force: Boolean = false): UpdateStatus {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(KEY_LAST_CHECK, 0L)

        if (!force && System.currentTimeMillis() - last < MIN_INTERVAL_MS) {
            Log.d(TAG, "Skipping check — last check was ${(System.currentTimeMillis() - last) / 1000}s ago")
            return UpdateStatus.UpToDate
        }

        val result = performCheck(context)
        sp.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        return result
    }

    /**
     * Full check pipeline:
     *   pointer file → releases_url → /releases/latest → compare → pick asset
     */
    private suspend fun performCheck(context: Context): UpdateStatus = withContext(Dispatchers.IO) {
        try {
            val api = UpdateApi.create()

            // ── Step 1: fetch the failsafe pointer ──────────────────────────
            Log.d(TAG, "Fetching pointer: ${UpdateApi.pointerUrl()}")
            val pointer = api.fetchPointer(UpdateApi.pointerUrl())
            Log.d(TAG, "Pointer resolved to: ${pointer.releasesUrl}")

            // ── Step 2: fetch latest release from the resolved repo ─────────
            val releaseUrl = "${pointer.releasesUrl.trimEnd('/')}/releases/latest"
            val release = api.fetchLatestRelease(releaseUrl)

            val remoteVersion = release.tagName.removePrefix("v").trim()
            val currentVersion = currentVersionName(context)

            Log.d(TAG, "current=$currentVersion remote=$remoteVersion")

            // ── Step 3: compare ─────────────────────────────────────────────
            if (!isNewer(remoteVersion, currentVersion)) {
                return@withContext UpdateStatus.UpToDate
            }

            // ── Step 4: pick the right asset ────────────────────────────────
            val apk = pickApkAsset(release.assets, pointer.apkAssetName)
                ?: return@withContext UpdateStatus.Error(
                    "Release ${release.tagName} has no APK asset."
                )

            UpdateStatus.Available(
                version = remoteVersion,
                releaseNotes = release.body,
                downloadUrl = apk.downloadUrl,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateStatus.Error(e.message ?: "Unknown error")
        }
    }

    /** Reads the installed app's versionName. */
    private fun currentVersionName(context: Context): String {
        return runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0L),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: "0.0.0"
    }

    /**
     * Semantic-ish version comparison. Splits on ".", pads missing segments
     * with 0, compares numerically left to right.
     *
     *  "1.2.0" vs "1.2"    → true  (remote has an extra non-zero segment)
     *  "1.2"   vs "1.2.0"  → false
     *  "2.0.0" vs "1.9.9"  → true
     *  "1.2.3" vs "1.2.3"  → false
     */
    fun isNewer(remote: String, current: String): Boolean {
        // Strip any pre-release suffix like "-beta.1"
        val r = remote.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
        val c = current.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

        val len = maxOf(r.size, c.size)
        for (i in 0 until len) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    /** Picks the APK to download from the release's asset list. */
    private fun pickApkAsset(
        assets: List<GitHubAsset>,
        preferredName: String?,
    ): GitHubAsset? {
        if (assets.isEmpty()) return null

        // If the pointer file named a specific asset, honor it first.
        if (!preferredName.isNullOrBlank()) {
            assets.firstOrNull { it.name.equals(preferredName, ignoreCase = true) }
                ?.let { return it }
        }

        // Otherwise prefer an ABI-matched APK, then fall back to the first .apk.
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        val abi = preferredAbi()
        return apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            ?: apks.first()
    }

    private fun preferredAbi(): String {
        val supported = Build.SUPPORTED_ABIS
        return when {
            supported.any { it == "arm64-v8a" } -> "arm64"
            supported.any { it == "armeabi-v7a" } -> "arm"
            supported.any { it == "x86_64" } -> "x86_64"
            supported.any { it == "x86" } -> "x86"
            else -> "universal"
        }
    }
}