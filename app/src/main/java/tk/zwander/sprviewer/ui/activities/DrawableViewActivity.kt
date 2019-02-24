package tk.zwander.sprviewer.ui.activities

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import kotlinx.android.synthetic.main.activity_drawable_view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.getAppRes
import java.io.FileDescriptor
import java.io.FileOutputStream

class DrawableViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DRAWABLE_NAME = "drawable_name"

        private const val SAVE_REQ = 101
    }

    val drawableName by lazy { intent.getStringExtra(EXTRA_DRAWABLE_NAME) }
    val drawableId: Int
        get() = remRes.getIdentifier(drawableName, "drawable", pkg)
    val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    val remRes by lazy { getAppRes(pkg) }

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

        save.setOnClickListener {
            startSave()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SAVE_REQ -> {
                val desc = contentResolver.openFileDescriptor(data!!.data!!, "rw")!!.fileDescriptor

                GlobalScope.launch {
                    handleSave(
                        image.drawable.run {
                            if (this is BitmapDrawable) {
                                saveDirect(desc)
                                return@launch
                            } else {
                                val ratio = intrinsicHeight.toFloat() / intrinsicWidth.toFloat()
                                toBitmap(512, (512 * ratio).toInt())
                            }
                        },
                        desc
                    )
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startSave() {
        val animated = isViewingAnimatedImage

        if (animated) {
            Toast.makeText(this, R.string.cant_save_anim, Toast.LENGTH_SHORT).show()
            return
        }

        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = if (animated) "text/xml" else "images/png"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.${if (animated) "xml" else "png"}")

        startActivityForResult(saveIntent, SAVE_REQ)
    }

    private fun handleSave(bitmap: Bitmap, desc: FileDescriptor) {
        FileOutputStream(desc).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun saveDirect(desc: FileDescriptor) {
        val res = remRes.openRawResource(drawableId)
        val file = FileOutputStream(desc)

        val buffer = ByteArray(8192)

        var n: Int

        try {
            while(true) {
                n = res.read(buffer)

                if (n <= 0) break

                file.write(buffer, 0, n)
            }
        } finally {
            res.close()
            file.close()
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
