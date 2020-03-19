package tk.zwander.sprviewer.ui.activities

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import ar.com.hjg.pngj.*
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.util.extensionsToRasterize
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.getExtension
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.CircularProgressDialog
import java.io.File
import java.io.PrintWriter
import java.util.*
import kotlin.coroutines.resume
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

    private val apk by lazy {
        ApkFile(File(packageManager.getApplicationInfo(pkg, 0).sourceDir))
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }

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

        cancel()
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

        val done = launch(context = Dispatchers.Main) {
            val dir = File(externalCacheDir, "batch/$pkg")
            if (!dir.exists()) dir.mkdirs()

            items.forEachIndexed { index, drawableData ->
                dialog.setCurrentFileName(drawableData.name)

                val drawableXml = withContext(Dispatchers.IO) {
                    try {
                        apk.transBinaryXml("res/drawable/${drawableData.name}.xml")
                    } catch (e: Exception) {
                        null
                    }
                }
                val ext = remRes.getExtension(drawableData.id)
                val rasterExtension = if (extensionsToRasterize.contains(ext)) "png" else ext

                var loaded: Bitmap? = null

                if (drawableXml == null) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        val picasso = Picasso.Builder(this@DrawableListActivity)
                            .build()

                        val img = object : Target {
                            override fun onBitmapLoaded(
                                bitmap: Bitmap?,
                                from: Picasso.LoadedFrom?
                            ) {
                                loaded = bitmap
                                picasso.shutdown()
                                cont.resume(Unit)
                            }

                            override fun onBitmapFailed(
                                e: java.lang.Exception?,
                                errorDrawable: Drawable?
                            ) {
                                picasso.shutdown()
                                cont.resume(Unit)
                            }

                            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
                        }

                        picasso.load(
                                Uri.parse(
                                    "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                            "$pkg/" +
                                            "${remRes.getResourceTypeName(drawableData.id)}/" +
                                            "${drawableData.id}"
                                )
                            )
                            .into(img)
                    }
                } else {
                    try {
                        loaded = withContext(Dispatchers.IO) {
                            remRes.getDrawable(drawableData.id, remRes.newTheme()).run {
                                if (this is Animatable) null else toBitmap(
                                    width = max(
                                        dimen,
                                        1
                                    ),
                                    height = max(
                                        (dimen * intrinsicWidth.toFloat() / intrinsicHeight.toFloat()).toInt(),
                                        1
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {}
                }

                delay(100)

                if (loaded != null) {
                    val bmp = loaded!!
                    val target = File(dir, "${drawableData.name}.$rasterExtension")

                    dialog.setCurrentFileName(target.name)

                    target.outputStream().use { output ->
                        val info = ImageInfo(bmp.width, bmp.height, 8, bmp.hasAlpha())
                        val writer = PngWriter(output, info)

                        writer.setFilterType(FilterType.FILTER_ADAPTIVE_FAST)
                        writer.pixelsWriter.deflaterCompLevel = 0

                        for (row in 0 until bmp.height) {
                            val line = ImageLineInt(info)

                            withContext(Dispatchers.IO) {
                                for (col in 0 until bmp.width) {
                                    if (bmp.hasAlpha()) {
                                        ImageLineHelper.setPixelRGBA8(
                                            line,
                                            col,
                                            bmp.getPixel(col, row)
                                        )
                                    } else {
                                        ImageLineHelper.setPixelRGB8(
                                            line,
                                            col,
                                            bmp.getPixel(col, row)
                                        )
                                    }
                                }

                                writer.writeRow(line)
                            }

                            dialog.updateSubProgress(row + 1, bmp.height)
                        }

                        withContext(Dispatchers.IO) {
                            writer.end()
                        }

                        dialog.updateSubProgress(0)
                    }
                }

                if (drawableXml != null) {
                    val target = File(dir, "${drawableData.name}.xml")

                    dialog.setCurrentFileName(target.name)

                    withContext(Dispatchers.IO) {
                        target.outputStream().use { out ->
                            PrintWriter(out).use { writer ->
                                writer.println(drawableXml)
                            }
                        }
                    }
                }

                dialog.updateProgress(index + 1)
            }
        }

        dialog.onCancelListener = {
            done.cancel()
        }

        done.join()
        d.dismiss()
    }
}
