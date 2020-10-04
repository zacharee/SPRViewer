package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.documentfile.provider.DocumentFile
import ar.com.hjg.pngj.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.CircularProgressDialog
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File
import java.lang.NullPointerException
import java.util.*
import kotlin.math.max


@Suppress("DeferredResultUnused")
class DrawableListActivity : BaseActivity<DrawableListAdapter>(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_FILE = "file"

        const val REQ_CHOOSE_OUTPUT_DIR = 2100
    }

    override val contentView = R.layout.activity_main
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_INFO, it.toDrawableData())
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }
    override val hasBackButton = true

    private val apkPath by lazy {
        if (pkg != null) {
            File(packageManager.getApplicationInfo(pkg, 0).sourceDir)
        } else {
            file
        }
    }
    private val apk by lazy {
        ApkFile(apkPath)
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val table by lazy {
        apk.getResourceTable()
    }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val file by lazy { intent.getSerializableExtra(EXTRA_FILE) as File? }
    private val remRes by lazy { getAppRes(apkPath!!) }

    private var saveAll: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pkg == null && file == null) {
            finish()
            return
        }

        adapter.loadItemsAsync(apk, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
        }

        updateTitle()
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.batch, menu)

        saveAll = menu.findItem(R.id.all)
        saveAll?.setOnMenuItemClickListener {
            val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(openIntent, REQ_CHOOSE_OUTPUT_DIR)

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == REQ_CHOOSE_OUTPUT_DIR
            && data != null) {
            BaseDimensionInputDialog(this) { info ->
                handleBatchExport(info, data.data)
            }.show()
        }
    }

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }
    }

    private fun handleBatchExport(
        info: ExportInfo, uri: Uri
    ) = launch {
        val items = adapter.allItemsCopy
        val dialog = CircularProgressDialog(this@DrawableListActivity, items.size)
        val d = dialog.show()

        val parentDir = DocumentFile.fromTreeUri(this@DrawableListActivity, uri)
        val dir = parentDir?.createDirectory(apk.apkMeta.packageName)

        val done = launch(context = Dispatchers.Main) {
            items.forEachIndexed { index, drawableData ->
                launch {
                    dialog.setBaseFileName(drawableData.name)
                }

                val ext = drawableData.ext

                if ((!info.rasterizeXmls && !info.exportXmls && ext == "xml")
                    || (!info.rasterizeAstcs && !info.exportAstcs && ext == "astc")
                    || (!info.rasterizeSprs && !info.exportSprs && ext == "spr")
                    || (!info.exportRasters && !extensionsToRasterize.contains(ext))) {
                    launch {
                        dialog.updateProgress(index + 1)
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
                        ResourcesCompat.getDrawable(remRes, drawableData.id, remRes.newTheme())!!.run {
                            if (this is Animatable && this::class.java.canonicalName?.contains("SemPathRenderingDrawable") == false) null else toBitmap(
                                width = max(
                                    info.dimen,
                                    1
                                ),
                                height = max(
                                    (info.dimen * intrinsicHeight.toFloat() / intrinsicWidth.toFloat()).toInt(),
                                    1
                                )
                            ).run {
                                copy(this.config, this.isMutable).also { this.recycle() }
                            }
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
                                    picasso.load(
                                            Uri.parse(
                                                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                                        "${apk.apkMeta.packageName}/" +
                                                        "${remRes.getResourceTypeName(drawableData.id)}/" +
                                                        "${drawableData.id}"
                                            )
                                        )
                                        .get().run {
                                            if (extensionsToRasterize.contains(ext)) Bitmap.createScaledBitmap(
                                                this,
                                                info.dimen,
                                                info.dimen * (height.toFloat() / width.toFloat()).toInt(),
                                                true
                                            ).also {
                                                this.recycle()
                                            }
                                            else this.run {
                                                copy(this.config, this.isMutable).also { this.recycle() }
                                            }
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
                    val target = dir?.createFile("image/$rasterExtension", drawableData.path.replace("/", "."))

                    launch {
                        dialog.setCurrentFileName(target!!.name)
                    }

                    contentResolver.openOutputStream(target!!.uri).use { output ->
                        val imgInfo = ImageInfo(loaded.width, loaded.height, 8, loaded.hasAlpha())
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

                            launch {
                                dialog.updateSubProgress(row + 1, loaded.height)
                            }
                        }

                        withContext(Dispatchers.IO) {
                            writer.end()
                        }

                        launch {
                            dialog.updateSubProgress(0)
                            loaded.recycle()
                        }
                    }
                }

                if (info.exportXmls && drawableXml != null) {
                    val target = dir?.createFile("text/xml", drawableData.path.replace("/", "."))

                    launch {
                        dialog.setCurrentFileName(target!!.name)
                    }

                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(target!!.uri).use { out ->
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
                                        dialog.updateSubProgress(max - avail, max)
                                    }
                                }
                            }
                        }
                    }

                    launch {
                        dialog.updateSubProgress(0)
                    }
                }

                if ((ext == "astc" && info.exportAstcs) || (ext == "spr" && info.exportSprs)) {
                    val target = dir?.createFile("image/$ext", drawableData.path.replace("/", "."))

                    launch {
                        dialog.setCurrentFileName(target!!.name)
                    }

                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(target!!.uri).use { output ->
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
                                        dialog.updateSubProgress(max - avail, max)
                                    }
                                }
                            }
                        }
                    }

                    launch {
                        dialog.updateSubProgress(0)
                    }
                }

                launch {
                    dialog.updateProgress(index + 1)
                }
            }
        }

        dialog.onCancelListener = {
            done.cancel()
        }

        done.join()
        d.dismiss()

        MaterialAlertDialogBuilder(this@DrawableListActivity)
            .setTitle(R.string.save_all_complete)
            .setMessage(resources.getString(R.string.save_all_complete_desc))
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.open_dir) { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.setDataAndType(dir!!.uri, "resource/folder")

                try {
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@DrawableListActivity, R.string.open_dir_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
    }

    override fun onLoadFinished() {
        super.onLoadFinished()

        updateTitle(adapter.itemCount)
    }

    private fun updateTitle(numberApps: Int = -1) = launch {
        title = withContext(Dispatchers.Main) {
            apk.run {
                try {
                    apkMeta.label
                } catch (e: NullPointerException) {
                    pkg
                }
            }
        } + if (numberApps > -1) " ($numberApps)" else ""
    }
}
