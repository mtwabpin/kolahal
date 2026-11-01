package com.mtwabpin.kolahal.terminal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.script.ScriptMeta
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Request

/**
 * `update` says whether a newer release is out; `update install` downloads its APK and hands it to
 * Android's installer, once it's checked to be Kolahal's and signed with the release key. Not in the
 * Play build, which may update only through Google Play ([BuildConfig.SELF_UPDATE]).
 */
internal class UpdateCommands(
    context: Context,
    private val background: (String, () -> Unit) -> Unit,
) : CommandGroup(context) {
    data class Release(val version: String, val url: String, val size: Long, val digest: String?)

    /** How much of the APK has come so far. */
    data class Progress(val downloaded: Long, val total: Long) {
        val percent: Int get() = (downloaded * 100 / total).toInt()
    }

    companion object {
        private const val RELEASE_PAGE = "https://github.com/mtwabpin/kolahal/releases/latest"
        private const val API_URL = "https://api.github.com/repos/mtwabpin/kolahal/releases/latest"
        private const val DOWNLOAD_PREFIX = "https://github.com/mtwabpin/kolahal/releases/download/"
        private const val OFFICIAL_CERT = "21223ad4312a871e4a1dd16440efccac84f4660c56fa1e80467631062938e210"
        private const val MAX_APK_SIZE = 100L * 1024 * 1024
        private val VERSION = Regex("""\d+\.\d+\.\d+""")

        /** One at a time, whichever command started it. */
        private val running = AtomicBoolean(false)

        private val _progress = MutableStateFlow<Progress?>(null)
        /** The download under way, for the bar above the prompt; null when there is none. */
        val progress: StateFlow<Progress?> = _progress.asStateFlow()

        /** Whether version [a] comes after [b]. A release comes after its own -dev build, which [ScriptMeta.isNewer] counts as the same. */
        fun isNewer(a: String, b: String): Boolean = ScriptMeta.isNewer(a, b) || b == "$a-dev"

        /** The latest release in GitHub's answer: the full build's APK, which is what `update` installs. */
        fun parseRelease(body: String): Release {
            val data = Json.decode(body) as? Map<*, *> ?: throw IllegalStateException("Invalid GitHub release response.")
            val tag = data["tag_name"] as? String ?: throw IllegalStateException("Release has no version tag.")
            val version = tag.removePrefix("v")
            if (!version.matches(VERSION)) throw IllegalStateException("Invalid release version: $tag")

            val assets = data["assets"] as? List<*> ?: throw IllegalStateException("Release has no assets.")
            val asset = assets.filterIsInstance<Map<*, *>>().firstOrNull { it["name"] == "kolahal-$version.apk" }
                ?: throw IllegalStateException("Release APK not found.")
            // Normalised first: OkHttp resolves a ../ when it asks, which could lead out of Kolahal's releases.
            val url = (asset["browser_download_url"] as? String)?.let(Net::webUrl)
                ?: throw IllegalStateException("Release APK has no download URL.")
            if (!url.startsWith(DOWNLOAD_PREFIX)) throw IllegalStateException("Unexpected APK download address.")
            val size = (asset["size"] as? Number)?.toLong() ?: throw IllegalStateException("Release APK has no valid size.")
            if (size <= 0 || size > MAX_APK_SIZE) throw IllegalStateException("Release APK has an invalid size.")
            val digest = (asset["digest"] as? String)?.takeIf { it.startsWith("sha256:") }?.substringAfter("sha256:")
            return Release(version, url, size, digest)
        }

        /** An APK file with its permissions and signers: below Android 11, GET_SIGNING_CERTIFICATES gives none for a file. */
        @Suppress("DEPRECATION")
        fun apkPackageInfo(context: Context, apk: File): PackageInfo? = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_PERMISSIONS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES,
        )

        /** Not the Play build, which has the same version and key: Play's policy keeps this permission out of it for good. */
        fun isFullBuild(packageInfo: PackageInfo) = Manifest.permission.READ_CALL_LOG in packageInfo.requestedPermissions.orEmpty()

        fun installedPackage(context: Context): PackageInfo =
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

        @Suppress("DEPRECATION")
        fun signerDigests(packageInfo: PackageInfo): Set<String> {
            val signatures = packageInfo.signingInfo?.apkContentsSigners
                ?: packageInfo.signatures
                ?: throw IllegalStateException("Could not read APK signing certificates.")
            return signatures.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
        }

        /** Where the APK waits for the installer. */
        fun updatesDir(context: Context) = File(context.cacheDir, "updates")

        /** Throws unless [apk] is the release's file: its size and checksum, Kolahal's full build of that version, newer than this, signed with the release key. */
        fun verify(context: Context, apk: File, release: Release) {
            if (apk.length() != release.size) throw IllegalStateException("Downloaded APK size does not match the release.")
            if (release.digest != null && !sha256(apk).equals(release.digest, ignoreCase = true)) {
                throw IllegalStateException("APK SHA-256 checksum verification failed.")
            }

            val archive = apkPackageInfo(context, apk) ?: throw IllegalStateException("Downloaded file is not a valid APK.")
            if (archive.packageName != context.packageName) throw IllegalStateException("Downloaded APK has an unexpected package ID.")
            if (archive.versionName != release.version) throw IllegalStateException("Downloaded APK isn't version ${release.version}.")
            if (!isFullBuild(archive)) throw IllegalStateException("Downloaded APK isn't the full build.")

            val installed = installedPackage(context)
            if (archive.longVersionCode <= installed.longVersionCode) {
                throw IllegalStateException("Downloaded APK is not newer than the installed version.")
            }

            val signers = signerDigests(archive)
            if (signers != signerDigests(installed) || OFFICIAL_CERT !in signers) {
                throw IllegalStateException("Downloaded APK has an unexpected signing certificate.")
            }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.forEachBlock { buffer, count -> digest.update(buffer, 0, count) }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    /** The APK can take longer than a request's limit on a slow connection; the read timeout still stops a stalled one. */
    private val downloadClient by lazy { Net.client.newBuilder().callTimeout(10, TimeUnit.MINUTES).build() }

    private val releaseLink = LogContext.of(LogContext.URL to RELEASE_PAGE)
    private val installLine = LogContext.of(LogContext.COMMAND to "update install")

    fun run(args: List<String>) {
        if (args.size > 1 || (args.isNotEmpty() && args[0] != "install")) {
            error("Usage: update [install]")
            return
        }
        if (!running.compareAndSet(false, true)) {
            error("An update operation is already running.")
            return
        }
        val install = args.firstOrNull() == "install"
        background("update") {
            try {
                report(parseRelease(Net.get(API_URL)), install)
            } catch (e: Exception) {
                error("Update failed: ${e.message ?: e.javaClass.simpleName}", releaseLink)
            } finally {
                running.set(false)
            }
        }
    }

    private fun report(release: Release, install: Boolean) {
        val installed = BuildConfig.VERSION_NAME
        when {
            isNewer(installed, release.version) -> info("Installed version is newer than the latest release.")
            !isNewer(release.version, installed) -> info("Kolahal is already up to date (${release.version}).")
            else -> {
                info("Update available: $installed → ${release.version}", releaseLink)
                when {
                    // Android won't install a release over a build signed with another key: no point downloading it.
                    OFFICIAL_CERT !in signerDigests(installedPackage(context)) -> info(
                        "This build isn't signed with Kolahal's release key, so it can't update itself. Get the new version from the release page.",
                        releaseLink,
                    )
                    install -> install(release)
                    else -> info("Run 'update install' to download and install it.", installLine)
                }
            }
        }
    }

    private fun install(release: Release) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            info("Allow Kolahal to install unknown apps, then run 'update install' again.", installLine)
            return
        }

        // Kept until the update is installed (UpdateCleanupReceiver), so trying again doesn't download it again.
        val apk = File(updatesDir(context), "kolahal-${release.version}.apk")
        if (runCatching { verify(context, apk, release) }.isFailure) download(release, apk)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        // Android may keep an installer from opening if you left Kolahal during the download.
        info("Downloaded and verified Kolahal ${release.version}. If the installer doesn't open, run 'update install' again.", installLine)
    }

    /** Downloads the release to [apk] and verifies it. A failed or incomplete download is never kept. */
    private fun download(release: Release, apk: File) {
        // An APK from an earlier try: only now, as an installer may still be reading the last one until then.
        val directory = updatesDir(context)
        directory.deleteRecursively()
        if (!directory.mkdirs() && !directory.isDirectory) throw IllegalStateException("Could not create update cache directory.")

        val part = File.createTempFile("kolahal-${release.version}-", ".apk", directory)
        try {
            info("Downloading Kolahal ${release.version} (${release.size / (1024 * 1024)} MB)…")
            fetch(release, part)
            verify(context, part, release)
            if (!part.renameTo(apk)) throw IllegalStateException("Could not keep the downloaded APK.")
        } finally {
            part.delete()
            _progress.value = null
        }
    }

    private fun fetch(release: Release, destination: File) {
        val request = Request.Builder().url(release.url).header("User-Agent", Net.USER_AGENT).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("APK download returned HTTP ${response.code}")
            val body = response.body
            if (body.contentLength() > release.size) throw IllegalStateException("Downloaded APK is larger than expected.")

            var total = 0L
            _progress.value = Progress(total, release.size)
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        if (total > release.size) throw IllegalStateException("Downloaded APK is larger than expected.")
                        output.write(buffer, 0, count)
                        // By the whole percent: the bar shows no finer, and a fast connection would redraw it for nothing.
                        val progress = Progress(total, release.size)
                        if (progress.percent != _progress.value?.percent) _progress.value = progress
                    }
                }
            }
        }
    }
}
