package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.util.extensionsToRasterize
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.CircularProgressDialog
import java.io.File
import java.io.PrintWriter
import java.util.*
import kotlin.math.floor

class DrawableListActivity : BaseActivity<DrawableListAdapter>(), CoroutineScope by MainScope() {
    override val contentView = R.layout.activity_main
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_NAME, it.name)
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }

    private val apk by lazy {
        ApkFile(File(packageManager.getApplicationInfo(pkg, 0).sourceDir))
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pkg == null)  {
            finish()
            return
        }

        adapter.loadItems(this, pkg, this::onLoadFinished) { size, count ->
            progress?.apply {
                progress += floor((count.toFloat() / size.toFloat() * 100f)
                    .toDouble()).toInt()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.batch, menu)

        menu.findItem(R.id.all).setOnMenuItemClickListener {
            BaseDimensionInputDialog(this) {
                handleBatchExport(it)
            }.show()

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun handleBatchExport(dimen: Int) = launch {
        val items = adapter.allItemsCopy
        val dialog = CircularProgressDialog(this@DrawableListActivity, items.size)
        val d = dialog.show()

        val done = async {
            val dir = File(externalCacheDir, "batch/$pkg")
            if (!dir.exists()) dir.mkdirs()

            items.forEachIndexed { index, drawableData ->
                val drawableXml = try {
                    apk.transBinaryXml("res/drawable/${drawableData.name}.xml")
                } catch (e: Exception) {
                    null
                }
                val ext = getExtension(drawableData.id)
                val raster = try {
                    remRes.getDrawable(drawableData.id, remRes.newTheme())?.run {
                        toBitmap(dimen, (dimen * intrinsicHeight.toFloat() / intrinsicWidth.toFloat()).toInt())
                    }
                } catch (e: Exception) {
                    null
                }
                val rasterExtension = if (extensionsToRasterize.contains(ext)) "png" else ext

                if (raster != null) {
                    val target = File(dir, "${drawableData.name}.$rasterExtension")
                    target.outputStream().use { output ->
                        raster.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                }

                if (drawableXml != null) {
                    val target = File(dir, "${drawableData.name}.xml")
                    target.outputStream().use { out ->
                        PrintWriter(out).use { writer ->
                            writer.println(drawableXml)
                        }
                    }
                }

                raster?.recycle()

                this@DrawableListActivity.launch {
                    dialog.updateProgress(index + 1)
                }.join()
            }
        }

        dialog.onCancelListener = {
            done.cancel()
        }

        done.invokeOnCompletion {
            d.dismiss()
        }
    }

    private fun getExtension(drawableId: Int): String? {
        val v = TypedValue()
        remRes.getValue(drawableId, v, false)

        val string = v.coerceToString()

        return try {
            string.split(".").run { subList(1, size) }.joinToString(".")
        } catch (e: Exception) {
            null
        }
    }
}
