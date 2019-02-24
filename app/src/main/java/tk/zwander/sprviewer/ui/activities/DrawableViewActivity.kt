package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.android.synthetic.main.activity_drawable_view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.getAppRes
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream

class DrawableViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DRAWABLE_NAME = "drawable_name"

        private const val SAVE_PNG_REQ = 101
        private const val SAVE_XML_REQ = 102
    }

    private val apk by lazy { ApkFile(File(packageManager.getApplicationInfo(pkg, 0).sourceDir)) }
    private val drawableXml by lazy {
        try {
            apk.transBinaryXml("res/drawable/$drawableName.xml")
        } catch (e: Exception) {
            null
        }
    }
    private val drawableName by lazy { intent.getStringExtra(EXTRA_DRAWABLE_NAME) }
    private val drawableId: Int
        get() = remRes.getIdentifier(drawableName, "drawable", pkg)
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val remRes by lazy { getAppRes(pkg) }

    private val isViewingAnimatedImage: Boolean
        get() = image.anim != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawable_view)

        if (drawableName.isNullOrBlank() || pkg.isNullOrBlank()) {
            finish()
            return
        }

        try {
            makePicasso(
                Picasso.Listener { _, _, _ ->
                    image.setImageDrawable(remRes.getDrawable(drawableId))
                }
            ).into(image)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
            finish()
        }

        save_png.visibility = if (isViewingAnimatedImage) View.GONE else View.VISIBLE
        save_xml.visibility = if (drawableXml != null) View.VISIBLE else View.GONE

        save_png.setOnClickListener {
            startPngSave()
        }

        save_xml.setOnClickListener {
            startXmlSave()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            GlobalScope.launch {
                when (requestCode) {
                    SAVE_PNG_REQ -> {
                        val desc = contentResolver.openFileDescriptor(data!!.data!!, "rw")!!.fileDescriptor

                        image.drawable.run {
                            when {
                                this is BitmapDrawable -> savePngDirect(desc)
                                else -> {
                                    handlePngSave(
                                        kotlin.run {
                                            val ratio = intrinsicHeight.toFloat() / intrinsicWidth.toFloat()
                                            toBitmap(512, (512 * ratio).toInt())
                                        },
                                        desc
                                    )
                                }
                            }
                        }
                    }
                    SAVE_XML_REQ -> {
                        val desc = contentResolver.openFileDescriptor(data!!.data!!, "rw")!!.fileDescriptor

                        saveXml(desc)
                    }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startPngSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "images/png"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.png")

        startActivityForResult(saveIntent, SAVE_PNG_REQ)
    }

    private fun startXmlSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "text/xml"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.xml")

        startActivityForResult(saveIntent, SAVE_XML_REQ)
    }

    private fun handlePngSave(bitmap: Bitmap, desc: FileDescriptor) {
        FileOutputStream(desc).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun savePngDirect(desc: FileDescriptor) {
        val res = remRes.openRawResource(drawableId)
        val file = FileOutputStream(desc)

        val buffer = ByteArray(8192)

        var n: Int

        try {
            while (true) {
                n = res.read(buffer)

                if (n <= 0) break

                file.write(buffer, 0, n)
            }
        } finally {
            res.close()
            file.close()
        }
    }

    private fun saveXml(desc: FileDescriptor) {
        FileOutputStream(desc).bufferedWriter().use { writer ->
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
}
