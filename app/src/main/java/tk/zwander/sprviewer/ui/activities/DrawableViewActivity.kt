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
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.android.synthetic.main.activity_drawable_view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.extensionsToRasterize
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.mainHandler
import tk.zwander.sprviewer.views.DimensionInputDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.util.*

class DrawableViewActivity : AppCompatActivity() {
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
    private val ext by lazy { getExtension() }
    private val drawableName by lazy { intent.getStringExtra(EXTRA_DRAWABLE_NAME) }
    private val drawableId: Int
        get() = remRes.getIdentifier(drawableName, "drawable", pkg)
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }

    private val isViewingAnimatedImage: Boolean
        get() = image.anim != null
    private val isXmlOnly: Boolean
        get() = try {
            remRes.getDrawable(drawableId, remRes.newTheme())
            false
        } catch (e: Exception) {
            true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawable_view)

        if (drawableName.isNullOrBlank() || pkg.isNullOrBlank()) {
            finish()
            return
        }

        if (!isXmlOnly) {
            try {
                makePicasso(
                    Picasso.Listener { _, _, _ ->
                        try {
                            image.setImageDrawable(remRes.getDrawable(drawableId, remRes.newTheme()))
                        } catch (e: Exception) {
                            Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                ).into(image)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            image.isVisible = false
            text.isVisible = true

            text.text = drawableXml
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save, menu)

        val saveImg = menu.findItem(R.id.action_save_png)
        val saveXml = menu.findItem(R.id.action_save_xml)

        saveImg.isVisible = !isViewingAnimatedImage && !isXmlOnly
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
            GlobalScope.launch {
                when (requestCode) {
                    SAVE_PNG_REQ -> {
                        val os = contentResolver.openOutputStream(data!!.data!!)!!

                        saveAsPng(os)
                    }
                    SAVE_XML_REQ -> {
                        val os = contentResolver.openOutputStream(data!!.data!!)!!

                        saveXml(os)
                    }
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
            savePngDirect(os)
        } else {
            handlePngSave(os)
        }
    }

    private fun handlePngSave(os: OutputStream) {
        image.drawable.run {
            if (!extensionsToRasterize.contains(ext)) {
                compressPng(toBitmap(), os)
            } else {
                getDimensions(this, os)
            }
        }
    }

    private fun compressPng(bmp: Bitmap, os: OutputStream) {
        setProgressVisible(true, indet = true)

        try {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        } finally {
            os.close()
            setProgressVisible(false)
        }
    }

    private fun savePngDirect(os: OutputStream, bytes: ByteArray? = null) {
        val res = if (bytes != null) ByteArrayInputStream(bytes) else remRes.openRawResource(drawableId)

        val buffer = ByteArray(16384)

        setProgressVisible(true)

        val max = res.available()
        setMaxProgress(max)

        var n: Int

        try {
            while (true) {
                n = res.read(buffer)

                if (n <= 0) break

                os.write(buffer, 0, n)
                setCurrentProgress(max - res.available())
            }
        } finally {
            res.close()
            os.close()

            setProgressVisible(false)
        }
    }

    private fun saveXml(os: OutputStream) {
        os.bufferedWriter().use { writer ->
            writer.write(drawableXml ?: return@use)
        }
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

    private fun getExtension(): String {
        val v = TypedValue()
        remRes.getValue(drawableId, v, true)

        val string = v.coerceToString()

        return string.split(".")[1]
    }

    private fun setProgressVisible(visible: Boolean, indet: Boolean = false) {
        mainHandler.post {
            export_progress.visibility = if (visible) View.VISIBLE else View.GONE
            export_progress.isIndeterminate = indet
        }
    }

    private fun setMaxProgress(max: Int) {
        mainHandler.post {
            export_progress.max = max
        }
    }

    private fun setCurrentProgress(current: Int) {
        mainHandler.post {
            export_progress.progress = current
        }
    }

    private fun getDimensions(drawable: Drawable, os: OutputStream) {
        mainHandler.post {
            DimensionInputDialog(this, drawable)
                .apply {
                    saveListener = { width, height -> compressPng(drawable.toBitmap(width, height), os) }
                }
                .show()
        }
    }
}
