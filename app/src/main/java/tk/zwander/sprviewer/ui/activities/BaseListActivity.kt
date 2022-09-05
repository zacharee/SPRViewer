package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.databinding.ActivityMainBinding
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import tk.zwander.sprviewer.util.*
import java.io.File
import java.util.*

abstract class BaseListActivity<Data : BaseData, VH : BaseListAdapter.BaseVH> :
    BaseActivity<Data, VH>() {
    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_APP_LABEL = "app_label"
    }

    internal abstract val saveAllAct: ActivityResultLauncher<Uri?>

    override val contentView by lazy { binding.root }
    override val hasBackButton = true

    override val recycler: RecyclerView
        get() = binding.recycler
    override val scrollerThumb: FastScrollerThumbView
        get() = binding.scrollerThumb
    override val scroller: FastScrollerView
        get() = binding.scroller

    internal val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val apkPath by lazy {
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

    protected val pkg by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    protected val appLabel by lazy {
        intent.getStringExtra(EXTRA_APP_LABEL)
            ?: run {
                val l = packageInfo.appInfo?.loadLabel(packageManager)?.toString()
                if (l == packageInfo.appInfo?.name) null else l
            }
            ?: try {
                packageManager.getPackageInfo(
                    pkg ?: apk.apkMeta.packageName, 0
                ).applicationInfo.loadLabel(packageManager).toString()
            } catch (e: Exception) {
                apk.apkMeta.label ?: pkg ?: apk.apkMeta.packageName
            }
    }
    internal val file by lazy { intent.getSerializableExtra(EXTRA_FILE) as File? }
    internal val remRes by lazy { getAppRes(apk.getFile()) }

    internal val packageInfo by lazy {
        parsePackageCompat(apk.getFile(), pkg ?: apk.apkMeta.packageName, 0, true)
    }

    private var saveAll: MenuItem? = null

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
            saveAllAct.launch(null)

//            val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
//            startActivityForResult(openIntent, REQ_CHOOSE_OUTPUT_DIR)

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

    override fun onDestroy() {
        super.onDestroy()

        destroyAppRes(apk.getFile())
    }

    private fun updateTitle(numberItems: Int = -1) = launch {
        title = withContext(Dispatchers.IO) {
            appLabel
        } + if (numberItems > -1) " ($numberItems)" else ""
    }
}