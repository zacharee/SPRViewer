package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import ar.com.hjg.pngj.ImageInfo
import ar.com.hjg.pngj.ImageLineHelper
import ar.com.hjg.pngj.ImageLineInt
import ar.com.hjg.pngj.PngWriter
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.android.synthetic.main.activity_drawable_view.*
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.extensionsToRasterize
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.getExtension
import tk.zwander.sprviewer.views.DimensionInputDialog
import java.io.*
import java.util.*

@Suppress("DeferredResultUnused")
class DrawableViewActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_DRAWABLE_NAME = "drawable_name"

        private const val SAVE_PNG_REQ = 101
        private const val SAVE_XML_REQ = 102
    }

    private val apk by lazy {
        ApkFile(File(packageManager.getApplicationInfo(pkg, 0).sourceDir))
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val drawableXml by lazy {
        try {
            apk.transBinaryXml("res/drawable/$drawableName.xml")
        } catch (e: Exception) {
            null
        }
    }
    private val ext by lazy { remRes.getExtension(drawableId) }
    private val drawableName by lazy { intent.getStringExtra(EXTRA_DRAWABLE_NAME) }
    private val drawableId: Int
        get() = remRes.getIdentifier(drawableName, "drawable", pkg)
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }

    private val isViewingAnimatedImage: Boolean
        get() = image.anim != null

    private var saveImg: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawable_view)

        if (drawableName.isNullOrBlank() || pkg.isNullOrBlank()) {
            finish()
            return
        }

        if (drawableXml == null) {
            try {
                makePicasso(
                    Picasso.Listener { _, _, _ ->
                        try {
                            image.setImageDrawable(remRes.getDrawable(drawableId, remRes.newTheme()))
                        } catch (e: Exception) {
                            Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                ).into(image, object : Callback {
                    override fun onError(e: java.lang.Exception?) {
                        image.isVisible = false
                        text.isVisible = true

                        saveImg?.isVisible = false

                        text.text = drawableXml
                    }

                    override fun onSuccess() {

                    }
                })
            } catch (e: Exception) {
                Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                image.setImageDrawable(remRes.getDrawable(drawableId, remRes.newTheme()))
            } catch (e: Exception) {
                image.isVisible = false
                text.isVisible = true

                saveImg?.isVisible = false

                text.text = drawableXml
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save, menu)

        saveImg = menu.findItem(R.id.action_save_png)
        val saveXml = menu.findItem(R.id.action_save_xml)

        saveImg?.isVisible = !isViewingAnimatedImage
        saveXml.isVisible = drawableXml != null

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_save_png -> {
                startPngSave()
                true
            }
            R.id.action_save_xml -> {
                startXmlSave()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SAVE_PNG_REQ -> {
                    val os = contentResolver.openOutputStream(data!!.data!!)!!

                    saveAsPng(os)
                }
                SAVE_XML_REQ -> {
                    val os = contentResolver.openOutputStream(data!!.data!!)!!

                    saveXmlAsync(os)
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startPngSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        val extension = if (extensionsToRasterize.contains(ext)) "png" else ext

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "images/$extension"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.$extension")

        startActivityForResult(saveIntent, SAVE_PNG_REQ)
    }

    private fun startXmlSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "text/xml"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.xml")

        startActivityForResult(saveIntent, SAVE_XML_REQ)
    }

    private fun saveAsPng(os: OutputStream) {
        if (!extensionsToRasterize.contains(ext)) {
            savePngDirectAsync(os)
        } else {
            handlePngSave(os)
        }
    }

    private fun handlePngSave(os: OutputStream) {
        image.drawable.run {
            if (!extensionsToRasterize.contains(ext)) {
                compressPngAsync(toBitmap(), os)
            } else {
                getDimensions(this, os)
            }
        }
    }

    private fun compressPngAsync(bmp: Bitmap, os: OutputStream) = async(context = Dispatchers.IO) {
        setProgressVisible(true, indet = false).join()
        setMaxProgress(bmp.height)

        try {
            val info = ImageInfo(bmp.width, bmp.height, 8, bmp.hasAlpha())
            val writer = PngWriter(os, info)

            writer.pixelsWriter.deflaterCompLevel = 0

            for (row in 0 until bmp.height) {
                val line = ImageLineInt(info)

                for (col in 0 until bmp.width) {
                    if (bmp.hasAlpha()) {
                        ImageLineHelper.setPixelRGBA8(line, col, bmp.getPixel(col, row))
                    } else {
                        ImageLineHelper.setPixelRGB8(line, col, bmp.getPixel(col, row))
                    }
                }

                writer.writeRow(line)
                setCurrentProgress(row + 1)
            }

            writer.end()
        } finally {
            os.close()
            setProgressVisible(false).join()
        }
    }

    private fun savePngDirectAsync(os: OutputStream, bytes: ByteArray? = null) = async(context = Dispatchers.IO) {
        val res = if (bytes != null) ByteArrayInputStream(bytes) else remRes.openRawResource(drawableId)

        val buffer = ByteArray(16384)

        setProgressVisible(true).join()

        val max = res.available()
        setMaxProgress(max).join()

        var n: Int

        try {
            while (true) {
                n = res.read(buffer)

                if (n <= 0) break

                os.write(buffer, 0, n)
                setCurrentProgress(max - res.available()).join()
            }
        } finally {
            res.close()
            os.close()

            setProgressVisible(false).join()
        }
    }

    private fun saveXmlAsync(os: OutputStream) = async(context = Dispatchers.IO) {
        setProgressVisible(visible = true, indet = true)

        os.bufferedWriter().use { writer ->
            writer.write(drawableXml ?: return@use)
        }

        setProgressVisible(false)
    }

    private fun makePicasso(listener: Picasso.Listener? = null): RequestCreator {
        return Picasso.Builder(this)
            .apply {
                if (listener != null) {
                    listener(listener)
                }
            }
            .build()
            .load(
                Uri.parse(
                    "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                            "$pkg/" +
                            "${remRes.getResourceTypeName(drawableId)}/" +
                            "$drawableId"
                )
            )
    }

    private fun setProgressVisible(visible: Boolean, indet: Boolean = false) = launch {
        export_progress.isVisible = visible
        export_progress.isIndeterminate = indet
        export_progress.progress = 0
    }

    private fun setMaxProgress(max: Int) = launch {
        export_progress.max = max
    }

    private fun setCurrentProgress(current: Int) = launch {
        export_progress.progress = current
    }

    private fun getDimensions(drawable: Drawable, os: OutputStream) = launch {
        DimensionInputDialog(this@DrawableViewActivity, drawable)
            .apply {
                saveListener = { width, height -> compressPngAsync(drawable.toBitmap(width, height), os) }
            }
            .show()
    }
}
