package com.kachat.app.services

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.util.redactingAddressesForLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android counterpart to iOS's "Export Diagnostics Archive" (`SettingsView.exportDiagnosticsArchive`)
 * — a zip with a `diagnostics.json` snapshot (app/device info, the wallet's settings and state,
 * node pool state) plus `app.log` (this process's own recent logcat output). Only the wallet's own
 * data (iOS wallet 59593af): no private key/mnemonic material, only the active wallet's public
 * address and connection/settings metadata.
 *
 * Android has no equivalent to iOS's in-process OSLogStore, so `app.log` is captured via `logcat`
 * filtered to this process's own pid — apps can only read their own UID's log entries this way since
 * Android 4.1, so this needs no extra permission.
 */
@Singleton
class DiagnosticsExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletManager: WalletManager,
    private val settingsRepository: AppSettingsRepository,
    private val nodePoolManager: NodePoolManager,
    private val pushState: PushState,
    private val nextcloudService: NextcloudService,
    /** Which .kachat source names are read from, for the wallet section (iOS wallet 59593af). */
    private val kachatRegistry: dagger.Lazy<com.kachat.app.services.kachatnames.KachatNamesRegistry>,
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class DiagnosticsArchive(
        val generatedAt: String,
        val app: AppInfo,
        val device: DeviceInfo,
        /** How many crash reports the archive carries under `crashes/`, newest first. */
        val recordedCrashes: Int,
        val settings: Map<String, String?>,
        /** The wallet's own state - no chats, no message store (iOS wallet 59593af `WalletInfo`). */
        val wallet: WalletInfo,
        val nodePool: NodePoolSummary
    ) {
        data class AppInfo(val packageName: String, val versionName: String?, val versionCode: Long, val build: String = com.kachat.app.util.AppVersion.display)
        data class DeviceInfo(val manufacturer: String, val model: String, val androidRelease: String, val sdkInt: Int)
        data class WalletInfo(
            val hasActiveAccount: Boolean,
            val pushRegistered: Boolean,
            /** "indexer" or "chain"; null before the first check. */
            val kachatNamesSource: String?,
            val nextcloudConnected: Boolean,
        )
        data class NodePoolSummary(val counts: Map<String, Int>, val totalRecords: Int, val activeRecords: Int)
        data class NodeRecordSummary(val ip: String, val type: String, val status: String, val latency: String, val daaScore: String)
    }

    /** Builds the diagnostics zip in app-private cache and returns a content:// URI ready for a share sheet. */
    suspend fun exportDiagnostics(): Uri {
        val exportDir = File(context.cacheDir, "diagnostics_exports").apply { mkdirs() }
        val fileTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-")
        val zipFile = File(exportDir, "kapay-diagnostics-$fileTimestamp.zip")

        val diagnosticsJson = gson.toJson(buildDiagnosticsArchive())
        val logs = collectAppLogs()

        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("diagnostics.json"))
            zip.write(diagnosticsJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("app.log"))
            zip.write(logs.toByteArray())
            zip.closeEntry()

            // The system's crash log buffer holds this app's fatal entries - Java and native -
            // from EARLIER processes too, which the pid-filtered app.log above cannot see.
            zip.putNextEntry(ZipEntry("crash-buffer.log"))
            zip.write(collectCrashBuffer().toByteArray())
            zip.closeEntry()

            // Crashes from EARLIER launches. app.log above is this process only, so a crash
            // that ended the previous process - the kind that leaves the app "unable to open"
            // - would otherwise never make it into the archive. See CrashRecorder.
            for (crash in CrashRecorder.crashFiles(context)) {
                zip.putNextEntry(ZipEntry("crashes/${crash.name}"))
                zip.write(crash.readBytes())
                zip.closeEntry()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }

    private suspend fun buildDiagnosticsArchive(): DiagnosticsArchive {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: -1L
        } else {
            @Suppress("DEPRECATION") (packageInfo?.versionCode?.toLong() ?: -1L)
        }

        // Exporting must also work from the accounts screen with NO active account (the App
        // Settings gear there includes Diagnostics).
        val wallet = DiagnosticsArchive.WalletInfo(
            hasActiveAccount = runCatching { walletManager.getAddress() }.isSuccess,
            pushRegistered = pushState.isActive,
            kachatNamesSource = if (com.kachat.app.services.kachatnames.KachatNamesService.isLaunched) {
                when (kachatRegistry.get().source.value) {
                    is com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Indexer -> "indexer"
                    com.kachat.app.services.kachatnames.KachatNamesRegistry.Source.Chain -> "chain"
                    null -> null
                }
            } else {
                null
            },
            nextcloudConnected = nextcloudService.account.value != null,
        )

        val allNodes = nodePoolManager.allNodes.value
        val nodePool = DiagnosticsArchive.NodePoolSummary(
            counts = allNodes.groupingBy { it.status }.eachCount(),
            totalRecords = allNodes.size,
            activeRecords = nodePoolManager.activeNodes.value.size
        )

        val settings = mapOf(
            "network" to settingsRepository.network.first(),
            "indexerUrl" to settingsRepository.indexerUrl.first(),
            "knsApiUrl" to settingsRepository.knsApiUrl.first(),
            "kaspaRestUrl" to settingsRepository.kaspaRestUrl.first(),
            // No address of the wallet's (iOS wallet DiagnosticsArchive): whether there is an
            // account is in `wallet.hasActiveAccount`.
            "notificationsEnabled" to settingsRepository.notificationsEnabled.first().toString(),
            "addressActivityNotificationsEnabled" to settingsRepository.addressActivityNotificationsEnabled.first().toString(),
            "nextcloudServer" to (nextcloudService.account.value?.server ?: "none"),
            // Push: the state to check when a Received or .kachat notification doesn't arrive.
            "pushActive" to pushState.isActive.toString(),
            "pushLastAttemptAtMs" to (pushState.diagnostics.value.lastAttemptAtMs?.toString() ?: "never"),
            "pushLastAction" to (pushState.diagnostics.value.lastAction ?: "none"),
            "pushLastAttemptSucceeded" to (pushState.diagnostics.value.lastAttemptSucceeded?.toString() ?: "n/a"),
            "pushLastError" to (pushState.diagnostics.value.lastError ?: "none"),
            "pushFcmTokenPresent" to pushState.diagnostics.value.fcmTokenPresent.toString()
        )

        return DiagnosticsArchive(
            generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            app = DiagnosticsArchive.AppInfo(
                packageName = context.packageName,
                versionName = packageInfo?.versionName,
                versionCode = versionCode
            ),
            device = DiagnosticsArchive.DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT
            ),
            recordedCrashes = CrashRecorder.crashFiles(context).size,
            settings = settings,
            wallet = wallet,
            nodePool = nodePool
        )
    }

    /** The `crash` log buffer: fatal exceptions and native crash summaries for this app's UID,
     *  whichever process wrote them. An app can read only its own entries, so no permission. */
    private fun collectCrashBuffer(maxLines: Int = 2000): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-b", "crash", "-v", "time"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val lines = output.lines()
            (if (lines.size > maxLines) lines.takeLast(maxLines).joinToString("\n") else output)
                .redactingAddressesForLog()
        } catch (e: Exception) {
            "Failed to collect crash buffer: ${e.message}"
        }
    }

    /** This process's own recent logcat output — capped since a long-running session's buffer can be large. */
    private fun collectAppLogs(maxLines: Int = 5000): String {
        return try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "--pid=$pid"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val lines = output.lines()
            // Every address in a line, whoever logged it (an exception's message, a library's line),
            // leaves cut to its last 8 characters, as iOS's AppLog writes them (1652437).
            (if (lines.size > maxLines) lines.takeLast(maxLines).joinToString("\n") else output)
                .redactingAddressesForLog()
        } catch (e: Exception) {
            "Failed to collect logs: ${e.message}"
        }
    }
}
