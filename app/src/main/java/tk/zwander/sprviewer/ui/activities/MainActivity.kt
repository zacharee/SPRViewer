package tk.zwander.sprviewer.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.ui.adapters.AppListAdapter
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import java.io.File

class MainActivity : BaseActivity<AppData, BaseListAdapter.BaseVH>() {
    companion object {
        const val REQ_IMPORT_APK = 1000
    }

    override val contentView = R.layout.activity_main
    override val adapter = AppListAdapter(
        itemSelectedListener = {
            openDrawableActivity(it.pkg)
        },
        valuesSelectedListener = {
            openValuesActivity(it.pkg)
        }
    )

    private var importItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

            startActivityForResult(openIntent, REQ_IMPORT_APK)

            true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_IMPORT_APK -> {
                if (resultCode == Activity.RESULT_OK) {

                    data?.data?.also { uri ->

                        contentResolver.openInputStream(uri).use { inputStream ->
                            val apk = File(cacheDir, "temp_apk.apk")

                            apk.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }

                            openDrawableActivity(apk)
                        }
                    }
                }
            }
        }
    }

    override fun checkCount() {
        super.checkCount()

        if (isReadyForMenus()) {
            importItem?.isVisible = true
        }
    }

    private fun openDrawableActivity(pkg: String) {
        val drawableIntent = Intent(this, DrawableListActivity::class.java)
        drawableIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)

        startActivity(drawableIntent)
    }

    private fun openDrawableActivity(apk: File) {
        val drawableIntent = Intent(this, DrawableListActivity::class.java)
        drawableIntent.putExtra(DrawableListActivity.EXTRA_FILE, apk)

        startActivity(drawableIntent)
    }

    private fun openValuesActivity(pkg: String) {
        val valueIntent = Intent(this, StringsListActivity::class.java)
        valueIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)

        startActivity(valueIntent)
    }
}
