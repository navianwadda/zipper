package com.livetvpro.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.livetvpro.app.BuildConfig
import com.livetvpro.app.MainActivity
import com.livetvpro.app.R
import com.livetvpro.app.data.local.ThemeManager
import com.livetvpro.app.data.repository.NativeDataRepository
import com.livetvpro.app.ui.theme.LiveTVProTheme
import com.livetvpro.app.utils.DeviceUtils
import com.livetvpro.app.utils.NativeListenerManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject

private val BergenSans = FontFamily(Font(R.font.bergen_sans))

private sealed interface SplashState {
    object Loading : SplashState
    data class Error(val message: String) : SplashState
    object UpdateRequired : SplashState
}

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var dataRepository: NativeDataRepository
    @Inject lateinit var listenerManager: NativeListenerManager
    @Inject lateinit var themeManager: ThemeManager

    private var uiState          by mutableStateOf<SplashState>(SplashState.Loading)
    private var downloadProgress by mutableFloatStateOf(0f)
    private var downloadLabel    by mutableStateOf("")
    private var isDownloading    by mutableStateOf(false)
    private var downloadedApk: File? = null
    private var cachedWebUrl     = ""
    private var downloadCancelled = false
    private var qrBitmap by mutableStateOf<Bitmap?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startFetch() }

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val apk = downloadedApk
        if (apk != null && apk.exists() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) launchInstaller(apk)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themeManager.registerActivityContext(this)

        if (DeviceUtils.isBigScreenLayout) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()

        setContent {
            LiveTVProTheme(themeManager = themeManager) {
                SplashScreen(
                    state            = uiState,
                    versionName      = BuildConfig.VERSION_NAME,
                    isDownloading    = isDownloading,
                    downloadProgress = downloadProgress,
                    downloadLabel    = downloadLabel,
                    hasApkReady      = downloadedApk?.exists() == true,
                    onRetry          = { startFetch() },
                    onUpdate         = {
                        when {
                            isDownloading                    -> cancelDownload()
                            downloadedApk?.exists() == true -> installApk(downloadedApk!!)
                            else                             -> startDownload()
                        }
                    },
                    onWebsite        = {
                        val url = listenerManager.getWebUrl().ifBlank { cachedWebUrl }
                        if (url.isNotBlank()) openUrl(url)
                    },
                    onLater          = { finishAndRemoveTask() },
                    isTv             = DeviceUtils.isBigScreenLayout,
                    canSelfUpdate    = true,
                    qrBitmap         = qrBitmap,
                )
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState is SplashState.UpdateRequired) finishAndRemoveTask()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        FirebaseMessaging.getInstance().subscribeToTopic("all")
        requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startFetch()
        } else startFetch()
    }

    private fun startFetch() {
        uiState = SplashState.Loading
        lifecycleScope.launch {
            val success = fetchData()
            if (success) {
                val remoteSignatures = dataRepository.getAppSignature()
                val localSignature   = getApkSignature()
                val allowed          = remoteSignatures.split(",").map { it.trim() }
                if (localSignature.isEmpty() || allowed.none { it.equals(localSignature, ignoreCase = true) }) {
                    uiState = SplashState.Error("Connection error")
                    return@launch
                }
                val url = listenerManager.getWebUrl()
                if (url.isNotBlank()) cachedWebUrl = url
                if (isUpdateRequired()) {
                    if (DeviceUtils.isBigScreenLayout) {
                        val apkUrl = listenerManager.getDownloadUrl()
                        if (apkUrl.isNotBlank()) {
                            qrBitmap = withContext(Dispatchers.Default) {
                                generateQrBitmap(apkUrl)
                            }
                        }
                    }
                    uiState = SplashState.UpdateRequired
                } else navigateToMain()
            } else {
                uiState = SplashState.Error("Connection error")
            }
        }
    }

    private fun generateQrBitmap(content: String, sizePx: Int = 320): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun getApkSignature(): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val cert   = signatures?.firstOrNull()?.toByteArray() ?: return ""
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) { "" }
    }

    private suspend fun fetchData(): Boolean = try {
        dataRepository.fetchRemoteConfig()
        dataRepository.refreshData()
    } catch (e: Exception) { false }

    private fun isUpdateRequired(): Boolean = try {
        val remote = listenerManager.getAppVersion().trim()
        if (remote.isEmpty()) false
        else compareVersions(remote, BuildConfig.VERSION_NAME.trim()) > 0
    } catch (e: Exception) { false }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(p1.size, p2.size)) {
            val diff = (p1.getOrElse(i) { 0 }) - (p2.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun startDownload() {
        val url = listenerManager.getDownloadUrl()
        if (url.isBlank()) return
        isDownloading     = true
        downloadCancelled = false
        downloadProgress  = 0f
        downloadLabel     = "Preparing…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { downloadApk(url) }
            isDownloading = false
            if (result != null) {
                downloadedApk = result
                installApk(result)
            }
        }
    }

    private fun cancelDownload() {
        downloadCancelled = true
        isDownloading     = false
        downloadProgress  = 0f
        downloadLabel     = ""
    }

    private suspend fun downloadApk(url: String): File? = try {
        val dir     = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: cacheDir
        val apkFile = File(dir, "update.apk").also { if (it.exists()) it.delete() }
        val conn    = URL(url).openConnection().also {
            it.connectTimeout = 15_000; it.readTimeout = 30_000; it.connect()
        }
        val total = conn.contentLength.toLong()
        conn.getInputStream().use { input ->
            FileOutputStream(apkFile).use { output ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buf).also { bytes = it } != -1) {
                    if (downloadCancelled) { apkFile.delete(); return null }
                    output.write(buf, 0, bytes)
                    downloaded += bytes
                    val pct   = if (total > 0) downloaded.toFloat() / total else 0f
                    val dlMb  = "%.1f".format(downloaded / 1_048_576.0)
                    val totMb = if (total > 0) "%.1f".format(total / 1_048_576.0) else "?"
                    withContext(Dispatchers.Main) {
                        downloadProgress = pct
                        downloadLabel    = "$dlMb MB / $totMb MB  ${(pct * 100).toInt()}%"
                    }
                }
            }
        }
        apkFile
    } catch (e: Exception) { null }

    private fun installApk(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                installPermissionLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return
            }
            launchInstaller(apkFile)
        } catch (e: Exception) { }
    }

    private fun launchInstaller(apkFile: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            else Uri.fromFile(apkFile)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            android.os.Handler(mainLooper).postDelayed({ apkFile.delete() }, 3000)
        } catch (e: Exception) { }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadedApk?.let { if (it.exists()) it.delete() }
    }
}

@Composable
private fun SplashScreen(
    state: SplashState,
    versionName: String,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadLabel: String,
    hasApkReady: Boolean,
    onRetry: () -> Unit,
    onUpdate: () -> Unit,
    onWebsite: () -> Unit,
    onLater: () -> Unit,
    isTv: Boolean,
    canSelfUpdate: Boolean = true,
    qrBitmap: Bitmap? = null,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is SplashState.Loading ->
                LoadingScreen(versionName = versionName)

            is SplashState.Error ->
                ErrorScreen(
                    message      = state.message,
                    versionName  = versionName,
                    onRetry      = onRetry,
                    
                )

            is SplashState.UpdateRequired ->
                if (isTv) {
                    UpdateScreenLandscape(
                        isDownloading    = isDownloading,
                        downloadProgress = downloadProgress,
                        downloadLabel    = downloadLabel,
                        hasApkReady      = hasApkReady,
                        onUpdate         = onUpdate,
                        onWebsite        = onWebsite,
                        onLater          = onLater,
                        canSelfUpdate    = canSelfUpdate,
                        qrBitmap         = qrBitmap,
                    )
                } else {
                    UpdateScreenPortrait(
                        isDownloading    = isDownloading,
                        downloadProgress = downloadProgress,
                        downloadLabel    = downloadLabel,
                        hasApkReady      = hasApkReady,
                        onUpdate         = onUpdate,
                        onWebsite        = onWebsite,
                        onLater          = onLater,
                    
                    )
                }
        }
    }
}

@Composable
private fun LoadingScreen(versionName: String) {
    val primary   = MaterialTheme.colorScheme.primary
    val onBg      = MaterialTheme.colorScheme.onBackground

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(0.38f))

        AppIcon()

        Spacer(Modifier.height(16.dp))

        Text(
            text          = "Live TV Pro",
            color         = onBg,
            fontSize      = 26.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = BergenSans,
            letterSpacing = 0.08.sp,
        )

        Spacer(Modifier.weight(0.1f))

        SignalBars(color = primary)

        Spacer(Modifier.weight(0.1f))

        Spacer(Modifier.weight(0.36f))

        Text(
            text          = "VERSION $versionName",
            color         = onBg.copy(alpha = 0.5f),
            fontSize      = 12.sp,
            fontFamily    = BergenSans,
            letterSpacing = 0.1.sp,
            modifier      = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun ErrorScreen(message: String, versionName: String, onRetry: () -> Unit) {
    val onBg    = MaterialTheme.colorScheme.onBackground

    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.weight(0.38f))

        AppIcon()

        Spacer(Modifier.height(16.dp))

        Text(
            text       = "Live TV Pro",
            color      = onBg,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
        )

        Spacer(Modifier.weight(0.1f))

        Text(
            text       = message,
            color      = onBg,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
            textAlign  = TextAlign.Center,
        )

        Spacer(Modifier.weight(0.1f))

        SplashButton(text = "RETRY", onClick = onRetry)

        Spacer(Modifier.weight(0.42f))

        Text(
            text       = "VERSION $versionName",
            color      = onBg.copy(alpha = 0.5f),
            fontSize   = 12.sp,
            fontFamily = BergenSans,
            modifier   = Modifier.padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun UpdateScreenPortrait(
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadLabel: String,
    hasApkReady: Boolean,
    onUpdate: () -> Unit,
    onWebsite: () -> Unit,
    onLater: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onBg    = MaterialTheme.colorScheme.onBackground

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        AppIcon()

        Spacer(Modifier.height(14.dp))

        Text(
            text       = "New Update Available",
            color      = onBg,
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BergenSans,
        )

        if (isDownloading) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress   = { downloadProgress },
                modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color      = primary,
                trackColor = primary.copy(alpha = 0.2f),
                strokeCap  = StrokeCap.Round,
            )
            Spacer(Modifier.height(4.dp))
            Text(text = downloadLabel, color = onBg, fontSize = 12.sp, fontFamily = BergenSans)
        }

        Spacer(Modifier.height(14.dp))

        SplashButton(
            text = when {
                isDownloading -> "CANCEL"
                hasApkReady   -> "INSTALL"
                else          -> "UPDATE APP"
            },
            onClick = onUpdate,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text       = "You need to install the latest version. We will discontinue all the old version soon. Please download and install it. If the in-app update does not work, please download from our website.",
            color      = onBg.copy(alpha = 0.8f),
            fontSize   = 13.sp,
            fontFamily = BergenSans,
            lineHeight = 20.sp,
            modifier   = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        SplashButton(text = "DOWNLOAD FROM WEBSITE", onClick = onWebsite)

        Spacer(Modifier.height(8.dp))

        SplashButton(text = "UPDATE LATER", onClick = onLater)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun UpdateScreenLandscape(
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadLabel: String,
    hasApkReady: Boolean,
    onUpdate: () -> Unit,
    onWebsite: () -> Unit,
    onLater: () -> Unit,
    canSelfUpdate: Boolean = true,
    qrBitmap: Bitmap? = null,
) {
    val primary  = MaterialTheme.colorScheme.primary
    val onBg     = MaterialTheme.colorScheme.onBackground
    val divider  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)

    Row(
        modifier          = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.weight(0.45f).fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (qrBitmap != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon()
                    Spacer(Modifier.height(20.dp))
                    Image(
                        bitmap             = qrBitmap.asImageBitmap(),
                        contentDescription = "Scan to download the update APK",
                        modifier           = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(8.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = "Scan to download",
                        color      = onBg.copy(alpha = 0.8f),
                        fontSize   = 12.sp,
                        fontFamily = BergenSans,
                    )
                }
            } else {
                AppIcon()
            }
        }

        Box(
            modifier = Modifier.width(1.dp).fillMaxSize().background(divider)
        )

        Column(
            modifier            = Modifier
                .weight(0.55f)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text       = "New Update Available",
                color      = onBg,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BergenSans,
            )

            if (isDownloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress   = { downloadProgress },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = primary,
                    trackColor = primary.copy(alpha = 0.2f),
                    strokeCap  = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = downloadLabel, color = onBg, fontSize = 12.sp, fontFamily = BergenSans)
            }

            Spacer(Modifier.height(14.dp))

            if (canSelfUpdate) {
                SplashButton(
                    text = when {
                        isDownloading -> "CANCEL"
                        hasApkReady   -> "INSTALL"
                        else          -> "UPDATE APP"
                    },
                    onClick = onUpdate,
                )
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text       = "You need to install the latest version. We will discontinue all the old version soon. Please download and install it. If the in-app update does not work, please download from our website.",
                color      = onBg.copy(alpha = 0.8f),
                fontSize   = 13.sp,
                fontFamily = BergenSans,
                lineHeight = 20.sp,
                maxLines   = 3,
            )

            Spacer(Modifier.height(10.dp))

            if (canSelfUpdate) {
                SplashButton(text = "DOWNLOAD FROM WEBSITE", onClick = onWebsite)
                Spacer(Modifier.height(8.dp))
            }

            SplashButton(text = "UPDATE LATER", onClick = onLater)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppIcon() {
    val context = LocalContext.current
    val isBlack = remember { com.livetvpro.app.utils.AppIconUtils.isBlackIconActive(context) }
    val bgColor = if (isBlack) Color(0xFF121212) else Color(0xFFEF4444)
    Box(
        modifier         = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter            = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "App Icon",
            tint               = Color.Unspecified,
            modifier           = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun SplashButton(text: String, onClick: () -> Unit) {
    val primary  = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Button(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(8.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = primary,
            contentColor   = onPrimary,
        ),
    ) {
        Text(
            text          = text,
            fontSize      = 14.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = BergenSans,
            letterSpacing = 0.08.sp,
        )
    }
}

@Composable
private fun SignalBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "signal")

    val barDefs = listOf(
        BarDef(from = 0.15f, to = 1.00f, duration = 500, delay = 0),
        BarDef(from = 1.00f, to = 0.20f, duration = 650, delay = 100),
        BarDef(from = 0.40f, to = 1.00f, duration = 450, delay = 200),
        BarDef(from = 0.80f, to = 0.15f, duration = 600, delay = 80),
        BarDef(from = 0.20f, to = 0.90f, duration = 550, delay = 300),
    )

    Row(
        modifier              = Modifier.height(40.dp),
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        barDefs.forEach { def ->
            val scale by transition.animateFloat(
                initialValue  = def.from,
                targetValue   = def.to,
                animationSpec = infiniteRepeatable(
                    animation  = tween(
                        durationMillis = def.duration,
                        delayMillis    = def.delay,
                        easing         = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar",
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(40.dp)
                    .scale(scaleX = 1f, scaleY = scale)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

private data class BarDef(val from: Float, val to: Float, val duration: Int, val delay: Int)
