package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.content.pm.PackageParser
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.getFile
import tk.zwander.sprviewer.util.lazyDeferred
import tk.zwander.sprviewer.util.parsePackageCompat
import java.io.File
import java.util.*

abstract class BaseListActivity<Data : BaseData, VH : BaseListAdapter.BaseVH> : BaseActivity<Data, VH>() {
    companion object {
        const val EXTRA_FILE = "file"

        const val REQ_CHOOSE_OUTPUT_DIR = 2100
    }

    override val contentView = R.layout.activity_main
    override val hasBackButton = true

    internal val apkPath by lazy {
        if (pkg != null) {
            File(packageManager.getApplicationInfo(pkg, 0).sourceDir)
        } else {
            file
        }
    }
    internal val apk by lazy {
        ApkFile(apkPath)
            .apply { preferredLocale = Locale.getDefault() }
    }

    internal val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    internal val file by lazy { intent.getSerializableExtra(EXTRA_FILE) as File? }
    internal val appLabel by lazyDeferred {
        val labelRes = packageInfo.applicationInfo.labelRes
        getAppRes(apk.getFile()).getString(labelRes)
    }

    internal val parser = PackageParser()
    internal val packageInfo by lazy { parser.parsePackageCompat(apk.getFile(), 0, true) }

    internal var saveAll: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pkg == null && file == null) {
            finish()
            return
        }

        updateTitle()
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

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }
    }

    override fun onLoadFinished() {
        super.onLoadFinished()

        updateTitle(adapter.itemCount)
    }

    internal fun updateTitle(numberItems: Int = -1) = launch {
        title = withContext(Dispatchers.IO) {
            appLabel.await()
        } + if (numberItems > -1) " ($numberItems)" else ""
    }
}