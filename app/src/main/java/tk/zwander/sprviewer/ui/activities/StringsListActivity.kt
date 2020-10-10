package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.content.pm.PackageParser
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.ui.adapters.StringsListAdapter
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File
import java.util.*


@Suppress("DeferredResultUnused")
class StringsListActivity : BaseActivity<StringXmlData, StringsListAdapter.StringListVH>() {
    companion object {
        const val EXTRA_FILE = "file"

        const val REQ_CHOOSE_OUTPUT_DIR = 2100
    }

    override val contentView = R.layout.activity_main
    override val adapter = StringsListAdapter {
        StringsViewActivity.start(this, it, packageInfo.packageName)
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
    private val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val file by lazy { intent.getSerializableExtra(EXTRA_FILE) as File? }
    private val appLabel by lazyDeferred { packageInfo.applicationInfo.loadLabel(packageManager).toString() }

    private val parser = PackageParser()
    private val packageInfo by lazy { parser.parsePackageCompat(apk.getFile(), 0, true) }

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        //TODO: implement batch
//        menuInflater.inflate(R.menu.batch, menu)

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
            //TODO: fill in
        }
    }

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }
    }

    private fun handleBatchExport(info: ExportInfo, uri: Uri) = launch {
        contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

//        BatchExportService.startBatchExport(
//            this@ValueListActivity,
//            uri,
//            adapter.allItemsCopy,
//            info,
//            appLabel.await(),
//            apk.getFile()
//        )
    }

    override fun onLoadFinished() {
        super.onLoadFinished()

        updateTitle(adapter.itemCount)
    }

    private fun updateTitle(numberLocales: Int = -1) = launch {
        title = withContext(Dispatchers.IO) {
            appLabel.await()
        } + if (numberLocales > -1) " ($numberLocales)" else ""
    }
}
