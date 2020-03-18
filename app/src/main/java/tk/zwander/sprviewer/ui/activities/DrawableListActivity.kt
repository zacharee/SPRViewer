package tk.zwander.sprviewer.ui.activities

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.Target
import kotlinx.android.synthetic.main.activity_drawable_view.view.*
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.util.extensionsToRasterize
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.getExtension
import tk.zwander.sprviewer.views.AnimatedImageView
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.CircularProgressDialog
import java.io.File
import java.io.PrintWriter
import java.lang.reflect.GenericDeclaration
import java.lang.reflect.TypeVariable
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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

        val img = LayoutInflater.from(this@DrawableListActivity).inflate(R.layout.activity_drawable_view, null).image

        val done = async(context = Dispatchers.IO) {
            val dir = File(externalCacheDir, "batch/$pkg")
            if (!dir.exists()) dir.mkdirs()

            items.forEachIndexed { index, drawableData ->
                val drawableXml = try {
                    apk.transBinaryXml("res/drawable/${drawableData.name}.xml")
                } catch (e: Exception) {
                    null
                }
                val ext = remRes.getExtension(drawableData.id)
                val rasterExtension = if (extensionsToRasterize.contains(ext)) "png" else ext

                if (drawableXml == null) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        this@DrawableListActivity.launch {
                            Picasso.Builder(this@DrawableListActivity)
                                .build()
                                .load(Uri.parse(
                                    "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                            "$pkg/" +
                                            "${remRes.getResourceTypeName(drawableData.id)}/" +
                                            "${drawableData.id}"
                                ))
                                .into(img, object : Callback {
                                    override fun onError(e: Exception?) {
                                        cont.resume(Unit)
                                    }

                                    override fun onSuccess() {
                                        cont.resume(Unit)
                                    }
                                })
                        }
                    }
                } else {
                    try {
                        img.setImageDrawable(remRes.getDrawable(drawableData.id, remRes.newTheme()))
                    } catch (e: Exception) {}
                }

                if (img.drawable != null) {
                    val target = File(dir, "${drawableData.name}.$rasterExtension")
                    target.outputStream().use { output ->
                        img.drawable.run {
                            if (this is BitmapDrawable) bitmap
                            else toBitmap(width = max(dimen, 1), height = max((dimen * intrinsicWidth.toFloat() / intrinsicHeight.toFloat()).toInt(), 1))
                        }.compress(Bitmap.CompressFormat.PNG, 100, output)
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
}
