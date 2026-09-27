package io.github.shreyasskdev.tiledeck.data

import com.google.gson.annotations.SerializedName

/** Contents of the failsafe pointer file. */
data class UpdatePointer(
    @SerializedName("releases_url") val releasesUrl: String,
    @SerializedName("apk_asset_name") val apkAssetName: String? = null,
)

/** GitHub releases/latest response (only the fields we need). */
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String?,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList(),
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long,
)

/** What the checker hands back to the UI. */
sealed class UpdateStatus {
    data object UpToDate : UpdateStatus()
    data class Available(
        val version: String,
        val releaseNotes: String?,
        val downloadUrl: String,
    ) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}