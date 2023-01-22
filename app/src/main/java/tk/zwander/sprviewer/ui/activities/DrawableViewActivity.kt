package tk.zwander.sprviewer.ui.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import ar.com.hjg.pngj.ImageInfo
import ar.com.hjg.pngj.ImageLineHelper
import ar.com.hjg.pngj.ImageLineInt
import ar.com.hjg.pngj.PngWriter
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.databinding.ActivityDrawableViewBinding
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.DimensionInputDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.util.*

@SuppressLint("InlinedApi")
@Suppress("DeferredResultUnused")
class DrawableViewActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_DRAWABLE_INFO = "drawable_info"
    }

    private val apkPath by lazy {
        if (pkg != null) {
            File(packageManager.getApplicationInfoCompat(pkg).sourceDir)
        } else {
            file
        }
    }
    private val apk: ApkFile by lazy {
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
    private val drawableInfo by lazy { intent.getParcelableExtraCompat<DrawableData?>(EXTRA_DRAWABLE_INFO) }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val file by lazy { intent.getSerializableExtra(BaseListActivity.EXTRA_FILE) as File? }
    private val remRes by lazy { getAppRes(apkPath!!) }
    private val table by lazy {
        apk.resourceTable
    }
    private val paths by lazy {
        table.getResourcesById(drawableId.toLong()).map {
            it.resourceEntry.toStringValue(table, Locale.getDefault())
        }
    }
    private val packageInfo by lazy { parsePackageCompat(apk.getFile(), pkg, 0, true) }
    private val drawableName: String
        get() = drawableInfo!!.name
    private val drawableId: Int
        get() = drawableInfo!!.id
    private val ext: String?
        get() = drawableInfo!!.ext

    private val isViewingAnimatedImage: Boolean
        get() = binding.image.anim != null

    private val binding by lazy { ActivityDrawableViewBinding.inflate(layoutInflater) }

    private val savePngRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val os = contentResolver.openOutputStream(result.data!!.data!!)!!

            saveAsPng(os)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val saveXmlRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val os = contentResolver.openOutputStream(result.data!!.data!!)!!

            saveXmlAsync(os)
        }
    }

    private val saveOrigRequester = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val os = contentResolver.openOutputStream(result.data!!.data!!)!!

            saveOrigAsync(os)
        }
    }

    private var saveImg: MenuItem? = null
    private var saveOrig: MenuItem? = null
    private var saveXml: MenuItem? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (drawableInfo == null || (pkg.isNullOrBlank() && file == null)) {
            finish()
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        title = "$drawableName.$ext"

        window.decorView?.findViewById<View>(R.id.action_bar)?.apply {
            setOnLongClickListener {
                showTitleSnackBar(it)

                true
            }
        }

        launch {
            val drawableXml = drawableXml.getOrAwaitResult()

            if (drawableXml == null) {
                try {
                    Picasso.get().load(
                        Uri.parse(
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${packageInfo.packageName}/" +
                                    "${remRes.getResourceTypeName(drawableId)}/" +
                                    "$drawableId"
                        )
                    ).into(binding.image, object : Callback {
                        override fun onError(e: Exception?) {
                            try {
                                binding.image.setImageDrawable(ResourcesCompat.getDrawable(remRes, drawableId, remRes.newTheme()))

                                saveImg?.isVisible = !isViewingAnimatedImage
                            } catch (e: Exception) {
                                binding.image.isVisible = false
                                binding.text.isVisible = true

                                binding.text.text = drawableXml
                            }

                            imageLoadingDone()
                        }

                        override fun onSuccess() {
                            saveImg?.isVisible = !isViewingAnimatedImage
                            imageLoadingDone()
                        }
                    })
                } catch (e: Exception) {
                    Log.e("SPRViewer", "ERR", e)
                    Toast.makeText(this@DrawableViewActivity, R.string.load_image_error, Toast.LENGTH_SHORT).show()
                    imageLoadingDone()
                }
            } else {
                try {
                    binding.image.setImageDrawable(ResourcesCompat.getDrawable(remRes, drawableId, remRes.newTheme()))

                    saveImg?.isVisible = !isViewingAnimatedImage
                } catch (e: Exception) {
                    binding.image.isVisible = false
                    binding.text.isVisible = true

                    binding.text.text = drawableXml
                }

                imageLoadingDone()
            }
        }
    }

    private fun imageLoadingDone() {
        binding.loadingProgress.isVisible = false
        binding.imageTextWrapper.isVisible = true
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        destroyAppRes(apk.getFile())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startPngSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        val extension = if (extensionsToRasterize.contains(ext)) "png" else ext

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "images/$extension"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.$extension")

        savePngRequester.launch(saveIntent)
    }

    private fun startXmlSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "text/xml"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.xml")

        saveXmlRequester.launch(saveIntent)
    }

    private fun startOrigSave() {
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)

        saveIntent.addCategory(Intent.CATEGORY_OPENABLE)
        saveIntent.type = "image/$ext"
        saveIntent.putExtra(Intent.EXTRA_TITLE, "$drawableName.$ext")

        saveOrigRequester.launch(saveIntent)
    }

    private fun saveAsPng(os: OutputStream) {
        if (!extensionsToRasterize.contains(ext)) {
            savePngDirectAsync(os)
        } else {
            handlePngSave(os)
        }
    }

    private fun handlePngSave(os: OutputStream) {
        binding.image.drawable.run {
            if (!extensionsToRasterize.contains(ext)) {
                compressPngAsync(toBitmap(), os)
            } else {
                getDimensions(this, os)
            }
        }
    }

    private fun compressPngAsync(bmp: Bitmap, os: OutputStream) = async(context = Dispatchers.IO) {
        setProgressVisible(true, indeterminate = false).join()

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
        setProgressVisible(visible = true, indeterminate = false)

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
        setProgressVisible(visible = true, indeterminate = false)

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

    private fun setProgressVisible(visible: Boolean, indeterminate: Boolean = false) = launch {
        binding.exportProgress.isVisible = visible
        binding.exportProgress.isIndeterminate = indeterminate
        binding.exportProgress.progress = 0
    }

    private fun setMaxProgress(max: Int) = launch {
        binding.exportProgress.max = max
    }

    private fun setCurrentProgress(current: Int, max: Int = 100) = launch {
        setMaxProgress(max)
        binding.exportProgress.progress = current
    }

    private fun getDimensions(drawable: Drawable, os: OutputStream) = launch {
        DimensionInputDialog(this@DrawableViewActivity, drawable)
            .apply {
                saveListener = { width, height, tint -> compressPngAsync(
                    drawable.mutate().apply {
                        if (tint != Color.TRANSPARENT) {
                            setTint(tint)
                        }
                    }.toBitmap(width, height), os)
                }
            }
            .show()
    }
}
