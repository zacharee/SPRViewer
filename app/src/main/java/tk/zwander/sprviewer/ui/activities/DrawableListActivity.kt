package tk.zwander.sprviewer.ui.activities

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import androidx.core.graphics.drawable.toBitmap
import ar.com.hjg.pngj.*
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.CircularProgressDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.PrintWriter
import java.util.*
import java.util.zip.ZipFile
import kotlin.math.floor
import kotlin.math.max

class DrawableListActivity : BaseActivity<DrawableListAdapter>(), CoroutineScope by MainScope() {
    override val contentView = R.layout.activity_main
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_NAME, it.name)
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }

    private val apkPath by lazy {
        File(packageManager.getApplicationInfo(pkg, 0).sourceDir)
    }
    private val apk by lazy {
        ApkFile(apkPath)
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val zip by lazy {
        ZipFile(apkPath)
    }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }
    private val picasso by lazy {
        Picasso.Builder(this@DrawableListActivity)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pkg == null) {
            finish()
            return
        }

        adapter.loadItems(this, pkg, this::onLoadFinished) { size, count ->
            progress?.apply {
                progress += floor(
                    (count.toFloat() / size.toFloat() * 100f)
                        .toDouble()
                ).toInt()
            }
        }

        launch {
            title = withContext(Dispatchers.Main) {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        picasso.shutdown()
        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.batch, menu)

        menu.findItem(R.id.all).setOnMenuItemClickListener {
            BaseDimensionInputDialog(this) { dimen, rasterizeXmls, exportRasters, exportXmls, rasterizeOthers ->
                handleBatchExport(dimen, rasterizeXmls, exportRasters, exportXmls, rasterizeOthers)
            }.show()

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun handleBatchExport(
        dimen: Int,
        rasterizeXmls: Boolean,
        exportRasters: Boolean,
        exportXmls: Boolean,
        rasterizeOthers: Boolean
    ) = launch {
        val items = adapter.allItemsCopy
        val dialog = CircularProgressDialog(this@DrawableListActivity, items.size)
        val d = dialog.show()

        val done = launch(context = Dispatchers.Main) {
            val dir = File(externalCacheDir, "batch/$pkg")
            if (!dir.exists()) dir.mkdirs()

            items.forEachIndexed { index, drawableData ->
                launch {
                    dialog.setBaseFileName(drawableData.name)
                }

                val ext = remRes.getExtension(drawableData.id)
                val path = withContext(Dispatchers.IO) {
                    zip.entries().asSequence()
                        .find { it.name.split("/").last() == ("${drawableData.name}.$ext") }?.name
                }
                val drawableXml = withContext(Dispatchers.IO) {
                    try {
                        if (ext == "xml") apk.transBinaryXml(path)
                        else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val rasterExtension = if (extensionsToRasterize.contains(ext)) "png" else ext
                val loaded: Bitmap?
                val loadBmpFromRes by lazyDeferred(context = Dispatchers.IO) {
                    try {
                        remRes.getDrawable(drawableData.id, remRes.newTheme()).run {
                            if (this is Animatable && this::class.java.canonicalName?.contains("SemPathRenderingDrawable") == false) null else toBitmap(
                                width = max(
                                    dimen,
                                    1
                                ),
                                height = max(
                                    (dimen * intrinsicHeight.toFloat() / intrinsicWidth.toFloat()).toInt(),
                                    1
                                )
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                loaded = when {
                    drawableXml == null -> {
                        val rasterize = extensionsToRasterize.contains(ext)
                        if ((rasterize && rasterizeOthers) || (!rasterize && exportRasters)) {
                            try {
                                withContext(Dispatchers.IO) {
                                    picasso.load(
                                            Uri.parse(
                                                "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                                        "$pkg/" +
                                                        "${remRes.getResourceTypeName(drawableData.id)}/" +
                                                        "${drawableData.id}"
                                            )
                                        )
                                        .get().run {
                                            if (extensionsToRasterize.contains(ext)) Bitmap.createScaledBitmap(
                                                this,
                                                dimen,
                                                dimen * (height.toFloat() / width.toFloat()).toInt(),
                                                true
                                            ).also {
                                                this.recycle()
                                            }
                                            else this
                                        }
                                }
                            } catch (e: Exception) {
                                loadBmpFromRes.getOrAwaitResult()
                            }
                        } else null
                    }
                    rasterizeXmls -> {
                        loadBmpFromRes.getOrAwaitResult()
                    }
                    else -> {
                        null
                    }
                }

                if (loaded != null) {
                    val target = File(dir, "${drawableData.name}.$rasterExtension")

                    launch {
                        dialog.setCurrentFileName(target.name)
                    }

                    target.outputStream().use { output ->
                        val info = ImageInfo(loaded.width, loaded.height, 8, loaded.hasAlpha())
                        val writer = PngWriter(output, info)

                        writer.setFilterType(FilterType.FILTER_ADAPTIVE_FAST)
                        writer.pixelsWriter.deflaterCompLevel = 0

                        for (row in 0 until loaded.height) {
                            val line = ImageLineInt(info)

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
                        }
                    }

                    loaded.recycle()
                }

                if (exportXmls && drawableXml != null) {
                    val target = File(dir, "${drawableData.name}.xml")

                    launch {
                        dialog.setCurrentFileName(target.name)
                    }

                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out ->
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
    }
}
