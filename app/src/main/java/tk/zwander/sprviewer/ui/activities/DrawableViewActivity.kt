package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
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
import kotlinx.android.synthetic.main.activity_drawable_view.*
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.DimensionInputDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.util.*

@Suppress("DeferredResultUnused")
class DrawableViewActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_DRAWABLE_INFO = "drawable_info"

        private const val SAVE_PNG_REQ = 101
        private const val SAVE_XML_REQ = 102
        private const val SAVE_ORIG_REQ = 103
    }

    private val apkPath by lazy {
        File(packageManager.getApplicationInfo(pkg, 0).sourceDir)
    }
    private val apk by lazy {
        ApkFile(apkPath)
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val drawableXml by lazyDeferred(context = Dispatchers.IO) {
        try {
            if (ext == "xml") apk.transBinaryXml(path)
            else null
        } catch (e: Exception) {
            null
        }
    }
    private val path by lazy { paths.last() }
    private val drawableInfo by lazy { intent.getParcelableExtra<DrawableData>(EXTRA_DRAWABLE_INFO) }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }
    private val picasso: Picasso by lazy {
        Picasso.Builder(this)
            .build()
    }
    private val table by lazy {
        apk.getResourceTable()
    }
    private val paths by lazy {
        table.getResourcesById(drawableId.toLong()).map {
            it.resourceEntry.toStringValue(table, Locale.getDefault())
        }
    }
    private val drawableName: String
        get() = drawableInfo.name
    private val drawableId: Int
        get() = drawableInfo.id
    private val ext: String?
        get() = drawableInfo.ext

    private val isViewingAnimatedImage: Boolean
        get() = image.anim != null

    private var saveImg: MenuItem? = null
    private var saveOrig: MenuItem? = null
    private var saveXml: MenuItem? = null

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawable_view)

        if (drawableInfo == null || pkg.isNullOrBlank()) {
            finish()
            return
        }

        title = "$drawableName.$ext"

        launch {
            val drawableXml = drawableXml.getOrAwaitResult()

            if (drawableXml == null) {
                try {
                    picasso.load(
                        Uri.parse(
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "$pkg/" +
                                    "${remRes.getResourceTypeName(drawableId)}/" +
                                    "$drawableId"
                        )
                    ).into(image, object : Callback {
                        override fun onError(e: Exception?) {
                            try {
                                image.setImageDrawable(remRes.getDrawable(drawableId, remRes.newTheme()))

                                saveImg?.isVisible = !isViewingAnimatedImage
                            } catch (e: Exception) {
                                image.isVisible = false
                                text.isVisible = true

                                text.text = drawableXml
                            }

                            imageLoadingDone()
                        }

                        override fun onSuccess() {
                            saveImg?.isVisible = !isViewingAnimatedImage
                            imageLoadingDone()
                        }
                    })
                } catch (e: Exception) {
                    Toast.makeText(this@DrawableViewActivity, R.string.load_image_error, Toast.LENGTH_SHORT).show()
                    imageLoadingDone()
                }
            } else {
                try {
                    image.setImageDrawable(remRes.getDrawable(drawableId, remRes.newTheme()))

                    saveImg?.isVisible = !isViewingAnimatedImage
                } catch (e: Exception) {
                    image.isVisible = false
                    text.isVisible = true

                    text.text = drawableXml
                }

                imageLoadingDone()
            }
        }
    }

    private fun imageLoadingDone() {
        loading_progress.isVisible = false
        image_text_wrapper.isVisible = true
    }

    override fun onDestroy() {
        super.onDestroy()

        picasso.shutdown()
        cancel()
    }

    @ExperimentalCoroutinesApi
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save, menu)

        saveImg = menu.findItem(R.id.action_save_png)
        saveXml = menu.findItem(R.id.action_save_xml)
        saveOrig = menu.findItem(R.id.action_save_orig)

        saveOrig?.isVisible = ext == "spr" || ext == "astc"

        launch {
            saveXml?.isVisible = drawableXml.getOrAwaitResult() != null
        }

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
            R.id.action_save_orig -> {
                startOrigSave()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @ExperimentalCoroutinesApi
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
                SAVE_ORIG_REQ -> {
                    val os = contentResolver.openOutputStream(data!!.data!!)!!

                    saveOrigAsync(os)
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

    private fun startOrigSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "image/$ext"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.$ext")

        startActivityForResult(saveIntent, SAVE_ORIG_REQ)
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
                setCurrentProgress(row + 1, bmp.height)
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
        var n: Int

        try {
            while (true) {
                n = res.read(buffer)

                if (n <= 0) break

                os.write(buffer, 0, n)
                setCurrentProgress(max - res.available(), max).join()
            }
        } finally {
            res.close()
            os.close()

            setProgressVisible(false).join()
        }
    }

    @ExperimentalCoroutinesApi
    private fun saveXmlAsync(os: OutputStream) = async(context = Dispatchers.IO) {
        setProgressVisible(visible = true, indet = false)

        os.use { output ->
            drawableXml.getOrAwaitResult()?.byteInputStream()?.use { input ->
                val buffer = ByteArray(16384)
                val max = input.available()

                var n: Int

                while (true) {
                    n = input.read(buffer)

                    if (n <= 0) break

                    output.write(buffer, 0, n)

                    val avail = input.available()
                    setCurrentProgress(max - avail).join()
                }
            }
        }

        setProgressVisible(false)
    }

    private fun saveOrigAsync(os: OutputStream) = async(context = Dispatchers.IO) {
        setProgressVisible(visible = true, indet = false)

        os.use { output ->
            remRes.openRawResource(drawableId).use { input ->
                val buffer = ByteArray(16384)
                val max = input.available()

                var n: Int

                while (true) {
                    n = input.read(buffer)

                    if (n <= 0) break

                    output.write(buffer, 0, n)

                    val avail = input.available()
                    setCurrentProgress(max - avail).join()
                }
            }
        }

        setProgressVisible(false).join()
    }

    private fun setProgressVisible(visible: Boolean, indet: Boolean = false) = launch {
        export_progress.isVisible = visible
        export_progress.isIndeterminate = indet
        export_progress.progress = 0
    }

    private fun setMaxProgress(max: Int) = launch {
        export_progress.max = max
    }

    private fun setCurrentProgress(current: Int, max: Int = 100) = launch {
        setMaxProgress(max)
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
