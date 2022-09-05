package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.databinding.ActivityMainBinding
import tk.zwander.sprviewer.ui.adapters.AppListAdapter
import java.io.File

class MainActivity : BaseActivity<AppData, AppListAdapter.AppVH>() {
    override val contentView by lazy { binding.root }
    override val adapter = AppListAdapter(
        itemSelectedListener = {
            openDrawableActivity(it.pkg, it.label)
        },
        valuesSelectedListener = {
            openValuesActivity(it.pkg, it.label)
        }
    )

    override val recycler: RecyclerView
        get() = binding.recycler
    override val scrollerThumb: FastScrollerThumbView
        get() = binding.scrollerThumb
    override val scroller: FastScrollerView
        get() = binding.scroller

    internal val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val notifRequester =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (!it) {
                finish()
            }
        }
    private val apkImportReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result?.data?.data?.also { uri ->
                contentResolver.openInputStream(uri).use { inputStream ->
                    val apk = File(cacheDir, "temp_apk.apk")

                    apk.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.import_apk)
                        .setMessage(R.string.import_apk_choice)
                        .setPositiveButton(R.string.view_images) { _, _ ->
                            openDrawableActivity(apk)
                        }
                        .setNegativeButton(R.string.view_strings) { _, _ ->
                            openValuesActivity(apk)
                        }
                        .setNeutralButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private var importItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkCallingOrSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifRequester.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        adapter.loadItemsAsync(this, this::onLoadFinished) { size, count ->
            progress?.apply {
                progress = (count.toFloat() / size.toFloat() * 100f).toInt()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_apk, menu)

        importItem = menu.findItem(R.id.action_import)
        importItem?.setOnMenuItemClickListener {
            val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)

            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openIntent.addCategory(Intent.CATEGORY_OPENABLE)
            openIntent.type = "application/vnd.android.package-archive"

            apkImportReq.launch(openIntent)

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            importItem?.isVisible = true
        }
    }

    private fun openDrawableActivity(pkg: String, label: String) {
        val drawableIntent = Intent(this, DrawableListActivity::class.java)
        drawableIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
        drawableIntent.putExtra(BaseListActivity.EXTRA_APP_LABEL, label)

        startActivity(drawableIntent)
    }

    private fun openDrawableActivity(apk: File) {
        val drawableIntent = Intent(this, DrawableListActivity::class.java)
        drawableIntent.putExtra(BaseListActivity.EXTRA_FILE, apk)

        startActivity(drawableIntent)
    }

    private fun openValuesActivity(pkg: String, label: String) {
        val valueIntent = Intent(this, StringsListActivity::class.java)
        valueIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)
        valueIntent.putExtra(BaseListActivity.EXTRA_APP_LABEL, label)

        startActivity(valueIntent)
    }

    private fun openValuesActivity(apk: File) {
        val valueIntent = Intent(this, StringsListActivity::class.java)
        valueIntent.putExtra(BaseListActivity.EXTRA_FILE, apk)

        startActivity(valueIntent)
    }
}
