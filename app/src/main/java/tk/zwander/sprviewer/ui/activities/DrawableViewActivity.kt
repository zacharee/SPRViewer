package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import kotlinx.android.synthetic.main.activity_drawable_view.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.getAppRes
import java.io.FileOutputStream

class DrawableViewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DRAWABLE_NAME = "drawable_name"

        private const val PERM_REQ = 100
        private const val SAVE_REQ = 101
    }

    val drawableId by lazy { intent.getStringExtra(EXTRA_DRAWABLE_NAME) }
    val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }

    private val isViewingAnimatedImage: Boolean
        get() = image.anim != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawable_view)

        if (drawableId.isNullOrBlank() || pkg.isNullOrBlank()) {
            finish()
            return
        }

        val res = getAppRes(pkg)

        try {
            val id = res.getIdentifier(drawableId, "drawable", pkg)
            val img = res.getDrawable(id)

            image.setImageDrawable(img)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.load_image_error, Toast.LENGTH_SHORT).show()
            finish()
        }

        save.setOnClickListener {
//            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
//                handleSave()
//            } else {
//                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), PERM_REQ)
//            }

            handleSave()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            PERM_REQ -> {
                if (resultCode == Activity.RESULT_OK) handleSave()
            }
            SAVE_REQ -> {
                val desc = contentResolver.openFileDescriptor(data!!.data!!, "rw")!!.fileDescriptor

                FileOutputStream(desc).use { output ->
                    if (isViewingAnimatedImage) {
                        //TODO something...
                    } else {
                        image.drawable.run {
                            val ratio = intrinsicHeight.toFloat() / intrinsicWidth.toFloat()
                            toBitmap(512, (512 * ratio).toInt())
                        }.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleSave() {
        val animated = isViewingAnimatedImage

        if (animated) {
            Toast.makeText(this, R.string.cant_save_anim, Toast.LENGTH_SHORT).show()
            return
        }

        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = if (animated) "text/xml" else "images/png"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableId.${if (animated) "xml" else "png"}")

        startActivityForResult(saveIntent, SAVE_REQ)
    }
}
