package tk.zwander.sprviewer.ui.services

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import ar.com.hjg.pngj.*
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.BatchExportSessionData
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.ui.activities.BatchExportDialogActivity
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

class BatchExportService : Service(), CoroutineScope by MainScope() {
    companion object {
        const val ACTION_BATCH_EXPORT = "BATCH_EXPORT"
        const val ACTION_CANCEL_CURRENT_EXPORT = "CANCEL_EXPORT"

        const val EXTRA_EXPORT_URI = "export_uri"
        const val EXTRA_DRAWABLES = "drawables"
        const val EXTRA_EXPORT_INFO = "export_info"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_APP_FILE = "app_file"

        fun startBatchExport(
            context: Context,
            uri: Uri,
            drawables: List<UDrawableData>,
            exportInfo: ExportInfo,
            appName: String,
            appFile: File
        ) {
            val safeDrawables = ArrayList(drawables.map { it.toDrawableData() })
            val startIntent = Intent(context, BatchExportService::class.java)

            startIntent.action = ACTION_BATCH_EXPORT
            startIntent.putExtra(EXTRA_EXPORT_URI, uri)
            startIntent.putExtra(EXTRA_DRAWABLES, safeDrawables)
            startIntent.putExtra(EXTRA_EXPORT_INFO, exportInfo)
            startIntent.putExtra(EXTRA_APP_NAME, appName)
            startIntent.putExtra(EXTRA_APP_FILE, appFile.absolutePath)

            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    private val listeners by lazy { app.batchExportListeners }
    private val queuedExports = ArrayList<BatchExportSessionData>()

    private val picasso by lazy { Picasso.Builder(this).build() }

    private var currentExport: Deferred<*>? = null

    private val nm by lazy { NotificationManagerCompat.from(this) }

    private val progressViews by lazy {
        RemoteViews(
            packageName,
            R.layout.batch_export_notification_content_layout
        )
    }

    private val progressNotification by lazy {
        NotificationCompat.Builder(this, "batch_export")
            .setContentTitle(resources.getString(R.string.app_name))
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    102,
                    Intent(this, BatchExportDialogActivity::class.java),
                    0
                )
            )
    }

    private val completeNotification by lazy {
        NotificationCompat.Builder(this, "batch_export")
            .setContentTitle(resources.getString(R.string.save_all_complete_desc))
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setOngoing(false)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
            nm.createNotificationChannel(
                NotificationChannel(
                    "batch_export",
                    resources.getString(R.string.export_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    enableVibration(false)
                }
            )
        }

        startForeground(100, progressNotification.build())
    }

    override fun onDestroy() {
        super.onDestroy()

        stopForeground(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BATCH_EXPORT) {
            val uri = intent.getParcelableExtra<Uri>(EXTRA_EXPORT_URI)
            val drawables = intent.getParcelableArrayListExtra<DrawableData>(EXTRA_DRAWABLES)
            val exportInfo = intent.getParcelableExtra<ExportInfo>(EXTRA_EXPORT_INFO)
            val appName = intent.getStringExtra(EXTRA_APP_NAME)
            val appFile = File(intent.getStringExtra(EXTRA_APP_FILE))
            val apkFile = ApkFile(appFile)

            val session = BatchExportSessionData(
                uri, drawables, exportInfo, appName,
                appFile, apkFile,
                packageManager.getResourcesForApplication(apkFile.apkMeta.packageName)
            )

            onNewExportQueued(session)
        } else if (intent?.action == ACTION_CANCEL_CURRENT_EXPORT) {
            onExportCompleted(queuedExports.first(), true)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun onNewExportQueued(session: BatchExportSessionData) {
        queuedExports.add(session)

        if (currentExport == null || !currentExport!!.isActive) {
            startNewExport(session)
        }
    }

    private fun onExportCompleted(session: BatchExportSessionData, cancelled: Boolean = false) {
        queuedExports.remove(session)

        if (!cancelled) {
            nm.notify(session.hashCode(), createCompletedNotification(session))
        } else {
            currentExport?.cancel()
        }

        nm.cancel(100)

        listeners.forEach {
            it.onExportComplete()
        }

        if (queuedExports.isEmpty()) {
            stopSelf()
        } else {
            startNewExport(queuedExports.first())
        }
    }

    var previousNotification = 0L

    private fun startNewExport(session: BatchExportSessionData) {
        val runner = runBatchExport(
            this,
            session,
            { currentFile: String?, currentFileProgress: Int?,
              currentBaseProgress: Int?, currentBaseMaxProgress: Int?, currentDrawableName: String? ->

                listeners.forEach {
                    launch(Dispatchers.Main) {
                        if (currentBaseProgress != null && currentBaseMaxProgress != null) {
                            it.onProgressUpdate(currentBaseProgress, currentBaseMaxProgress)
                        }

                        if (currentDrawableName != null) {
                            it.onBaseFilenameUpdate(currentDrawableName)
                        }

                        if (currentFile != null) {
                            it.onCurrentFilenameUpdate(currentFile)
                        }

                        if (currentFileProgress != null) {
                            it.onSubProgressUpdate(currentFileProgress, 100)
                        }
                    }
                }

                val newTime = System.currentTimeMillis()

                if (newTime - previousNotification > 500) {
                    previousNotification = newTime

                    updateProgressNotification(currentFile, currentFileProgress,
                        currentBaseProgress, currentBaseMaxProgress, currentDrawableName, session)
                }
            },
            { s ->
                onExportCompleted(s)
            }
        )

        currentExport = runner
    }

    private fun createCompletedNotification(session: BatchExportSessionData): Notification {
        return completeNotification
            .setContentText(session.appName)
            .addAction(
                0,
                resources.getString(R.string.open_dir),
                PendingIntent.getActivity(
                    this,
                    101,
                    Intent(Intent.ACTION_VIEW).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setDataAndType(session.uri, "resource/folder")
                    },
                    0
                )
            )
            .build()
    }

    private fun updateProgressNotification(
        currentFile: String? = null,
        currentFileProgress: Int? = null,
        currentBaseProgress: Int? = null,
        currentBaseMaxProgress: Int? = null,
        currentDrawableName: String? = null,
        currentSession: BatchExportSessionData
    ) {
        val newNotification =
            createProgressNotification(
                currentFile,
                currentFileProgress, currentBaseProgress,
                currentBaseMaxProgress, currentDrawableName, currentSession
            )

        nm.notify(100, newNotification)
    }

    private fun createProgressNotification(
        currentFile: String? = null,
        currentFileProgress: Int? = null,
        currentBaseProgress: Int? = null,
        currentBaseMaxProgress: Int? = null,
        currentDrawableName: String? = null,
        currentSession: BatchExportSessionData
    ): Notification {
        val progressContent = generateProgressContent(
            currentFile,
            currentFileProgress,
            currentBaseProgress,
            currentBaseMaxProgress,
            currentDrawableName
        )

        return progressNotification
            .setContent(progressContent)
            .setCustomBigContentView(progressContent)
            .setNotificationSilent()
            .setContentTitle("${queuedExports.indexOf(currentSession) + 1} / ${queuedExports.size}")
            .build()
    }

    private fun generateProgressContent(
        currentFile: String? = null,
        currentFileProgress: Int? = null,
        currentBaseProgress: Int? = null,
        currentBaseMaxProgress: Int? = null,
        currentDrawableName: String? = null,
    ): RemoteViews {
        progressViews.setOnClickPendingIntent(
            R.id.cancel,
            PendingIntent.getService(
                this,
                100,
                Intent(this, BatchExportService::class.java)
                    .setAction(ACTION_CANCEL_CURRENT_EXPORT),
                0
            )
        )

        if (currentBaseProgress != null && currentBaseMaxProgress != null) {
            progressViews.setTextViewText(
                R.id.total_progress,
                "$currentBaseProgress / $currentBaseMaxProgress"
            )
            progressViews.setProgressBar(
                R.id.base_progress,
                100,
                ((currentBaseProgress.toDouble() / currentBaseMaxProgress) * 100).toInt(),
                false
            )
        }

        if (currentDrawableName != null) {
            progressViews.setTextViewText(R.id.base_filename, currentDrawableName)
        }

        if (currentFile != null) {
            progressViews.setTextViewText(R.id.current_filename, currentFile)
        }

        if (currentFileProgress != null) {
            progressViews.setProgressBar(R.id.current_progress, 100, currentFileProgress, false)
        }

        return progressViews
    }

    private fun runBatchExport(
        context: Context,
        session: BatchExportSessionData,
        progressCallback: (
            currentFile: String?,
            currentFileProgress: Int?,
            currentBaseProgress: Int?,
            currentBaseMaxProgress: Int?,
            currentDrawableName: String?
        ) -> Unit,
        completeCallback: (session: BatchExportSessionData) -> Unit
    ) = async(Dispatchers.IO) {
        val info = session.exportInfo
        val items = session.drawables
        val uri = session.uri
        val apk = session.apkFile
        val table = session.apkResourceTable
        val remRes = session.remRes

        val parentDir = DocumentFile.fromTreeUri(context, uri)
        val dir = parentDir?.createDirectory(apk.apkMeta.packageName)

        items.forEachIndexed { index, drawableData ->
            launch {
                progressCallback(
                    null,
                    null,
                    null,
                    null,
                    drawableData.name
                )
            }

            val ext = drawableData.ext

            if ((!info.rasterizeXmls && !info.exportXmls && ext == "xml")
                || (!info.rasterizeAstcs && !info.exportAstcs && ext == "astc")
                || (!info.rasterizeSprs && !info.exportSprs && ext == "spr")
                || (!info.exportRasters && !extensionsToRasterize.contains(ext))
            ) {
                launch {
                    progressCallback(
                        null,
                        null,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }

                return@forEachIndexed
            }

            val paths = withContext(Dispatchers.IO) {
                table.getResourcesById(drawableData.id.toLong()).map {
                    it.resourceEntry.toStringValue(table, Locale.getDefault())
                }
            }

            val path = paths.last()
            val drawableXml = withContext(Dispatchers.IO) {
                try {
                    if (ext == "xml") apk.transBinaryXml(path)
                    else null
                } catch (e: Exception) {
                    null
                }
            }
            val rasterExtension = "png"
            val loaded: Bitmap?
            val loadBmpFromRes by lazyDeferred(context = Dispatchers.IO) {
                try {
                    val drawable = ResourcesCompat.getDrawable(
                        remRes,
                        drawableData.id,
                        remRes.newTheme()
                    )

                    if (drawable is Animatable && drawable::class.java.canonicalName?.contains("SemPathRenderingDrawable") == false) {
                        null
                    } else {
                        val bmp = drawable!!.toBitmap(
                            max(info.dimen, 1),
                            max(
                                (info.dimen * drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth.toFloat()).toInt(),
                                1
                            )
                        )

                        bmp.copy(bmp.config, bmp.isMutable).also { bmp.recycle() }
                    }
                } catch (e: Exception) {
                    null
                }
            }

            loaded = when {
                drawableXml == null -> {
                    val rasterize = extensionsToRasterize.contains(ext)
                    val isAstc = ext == "astc"
                    val isSpr = ext == "spr"
                    if ((isAstc && info.rasterizeAstcs) || (isSpr && info.rasterizeSprs) || (!rasterize && info.exportRasters)) {
                        try {
                            withContext(Dispatchers.IO) {
                                val picassoBmp = picasso.load(
                                    Uri.parse(
                                        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                                "${apk.apkMeta.packageName}/" +
                                                "${remRes.getResourceTypeName(drawableData.id)}/" +
                                                "${drawableData.id}"
                                    )
                                ).get()

                                if (extensionsToRasterize.contains(ext)) {
                                    Bitmap.createScaledBitmap(
                                        picassoBmp,
                                        info.dimen,
                                        info.dimen * (picassoBmp.height.toFloat() / picassoBmp.width.toFloat()).toInt(),
                                        true
                                    )
                                } else {
                                    picassoBmp.copy(
                                        picassoBmp.config,
                                        picassoBmp.isMutable
                                    )
                                }.also {
                                    picassoBmp.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            loadBmpFromRes.getOrAwaitResult()
                        }
                    } else null
                }
                info.rasterizeXmls -> {
                    loadBmpFromRes.getOrAwaitResult()
                }
                else -> {
                    null
                }
            }

            if (loaded != null) {
                val target = dir?.createFile(
                    "image/$rasterExtension",
                    drawableData.path.replace("/", ".")
                )

                launch {
                    progressCallback(
                        target!!.name,
                        null,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }

                context.contentResolver.openOutputStream(target!!.uri).use { output ->
                    val imgInfo =
                        ImageInfo(loaded.width, loaded.height, 8, loaded.hasAlpha())
                    val writer = PngWriter(output, imgInfo)

                    writer.setFilterType(FilterType.FILTER_ADAPTIVE_FAST)
                    writer.pixelsWriter.deflaterCompLevel = 0

                    for (row in 0 until loaded.height) {
                        val line = ImageLineInt(imgInfo)

                        withContext(Dispatchers.IO) {
                            for (col in 0 until loaded.width) {
                                if (loaded.hasAlpha()) {
                                    ImageLineHelper.setPixelRGBA8(
                                        line,
                                        col,
                                        loaded.getPixel(col, row)
                                    )
                                } else {
                                    ImageLineHelper.setPixelRGB8(
                                        line,
                                        col,
                                        loaded.getPixel(col, row)
                                    )
                                }
                            }

                            writer.writeRow(line)
                        }

                        val height = loaded.height

                        launch {
                            progressCallback(
                                target.name,
                                ((row + 1).toDouble() / height * 100).toInt(),
                                index + 1,
                                items.size,
                                drawableData.name
                            )
                        }
                    }

                    withContext(Dispatchers.IO) {
                        writer.end()
                    }

                    launch {
                        progressCallback(
                            target.name,
                            0,
                            index + 1,
                            items.size,
                            drawableData.name
                        )
                    }

                    loaded.recycle()
                }
            }

            if (info.exportXmls && drawableXml != null) {
                val target =
                    dir?.createFile("text/xml", drawableData.path.replace("/", "."))

                launch {
                    progressCallback(
                        target!!.name,
                        null,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target!!.uri).use { out ->
                        drawableXml.byteInputStream().use { input ->
                            val buffer = ByteArray(16384)
                            val max = input.available()

                            var n: Int

                            while (true) {
                                n = input.read(buffer)

                                if (n <= 0) break

                                out.write(buffer, 0, n)

                                val avail = input.available()

                                launch(context = Dispatchers.Main) {
                                    progressCallback(
                                        target!!.name,
                                        ((max - avail).toDouble() / max).toInt(),
                                        index + 1,
                                        items.size,
                                        drawableData.name
                                    )
                                }
                            }
                        }
                    }
                }

                launch {
                    progressCallback(
                        target!!.name,
                        0,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }
            }

            if ((ext == "astc" && info.exportAstcs) || (ext == "spr" && info.exportSprs)) {
                val target =
                    dir?.createFile("image/$ext", drawableData.path.replace("/", "."))

                launch {
                    progressCallback(
                        target!!.name,
                        null,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target!!.uri).use { output ->
                        remRes.openRawResource(drawableData.id).use { input ->
                            val buffer = ByteArray(16384)
                            val max = input.available()

                            var n: Int

                            while (true) {
                                n = input.read(buffer)

                                if (n <= 0) break

                                output.write(buffer, 0, n)

                                val avail = input.available()

                                launch(context = Dispatchers.Main) {
                                    progressCallback(
                                        target!!.name,
                                        ((max - avail).toDouble() / max).toInt(),
                                        index + 1,
                                        items.size,
                                        drawableData.name
                                    )
                                }
                            }
                        }
                    }
                }

                launch {
                    progressCallback(
                        target!!.name,
                        0,
                        index + 1,
                        items.size,
                        drawableData.name
                    )
                }
            }

            launch {
                progressCallback(
                    null,
                    null,
                    index + 1,
                    items.size,
                    drawableData.name
                )
            }
        }

        launch {
            completeCallback(session)
        }
    }
}