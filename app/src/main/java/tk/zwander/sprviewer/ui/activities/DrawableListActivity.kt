package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.ui.services.BatchExportService
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File
import java.lang.NullPointerException
import java.util.*


@Suppress("DeferredResultUnused")
class DrawableListActivity : BaseActivity<DrawableListAdapter>(), CoroutineScope by MainScope() {
    companion object {
        const val EXTRA_FILE = "file"

        const val REQ_CHOOSE_OUTPUT_DIR = 2100
    }

    override val contentView = R.layout.activity_main
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_INFO, it.toDrawableData())
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }
    override val hasBackButton = true

    private val apkPath by lazy {
        if (pkg != null) {
            File(packageManager.getApplicationInfo(pkg, 0).sourceDir)
        } else {
            file
        }
    }
    private val apk by lazy {
        ApkFile(apkPath)
            .apply { preferredLocale = Locale.getDefault() }
    }
    private val table by lazy {
        apk.getResourceTable()
    }
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val file by lazy { intent.getSerializableExtra(EXTRA_FILE) as File? }
    private val remRes by lazy { getAppRes(apkPath!!) }

    private var saveAll: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pkg == null && file == null) {
            finish()
            return
        }

        adapter.loadItemsAsync(apk, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
        }

        updateTitle()
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.batch, menu)

        saveAll = menu.findItem(R.id.all)
        saveAll?.setOnMenuItemClickListener {
            val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(openIntent, REQ_CHOOSE_OUTPUT_DIR)

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == REQ_CHOOSE_OUTPUT_DIR
            && data != null) {
            BaseDimensionInputDialog(this) { info ->
                handleBatchExport(info, data.data)
            }.show()
        }
    }

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }
    }

    private fun handleBatchExport(info: ExportInfo, uri: Uri) {
        BatchExportService.startBatchExport(
            this,
            uri,
            adapter.allItemsCopy,
            info,
            apk.apkMeta.label,
            apk.getFile()
        )
    }

    override fun onLoadFinished() {
        super.onLoadFinished()

        updateTitle(adapter.itemCount)
    }

    private fun updateTitle(numberApps: Int = -1) = launch {
        title = withContext(Dispatchers.Main) {
            apk.run {
                try {
                    apkMeta.label
                } catch (e: NullPointerException) {
                    pkg
                }
            }
        } + if (numberApps > -1) " ($numberApps)" else ""
    }
}
