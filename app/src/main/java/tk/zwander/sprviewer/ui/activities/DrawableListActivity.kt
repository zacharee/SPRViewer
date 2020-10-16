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
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.ui.services.BatchExportService
import tk.zwander.sprviewer.util.*
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File
import java.util.*


@Suppress("DeferredResultUnused")
class DrawableListActivity : BaseListActivity<UDrawableData, DrawableListAdapter.ListVH>() {
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_INFO, it.toDrawableData())
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.loadItemsAsync(apk, packageInfo, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
        }
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

    private fun handleBatchExport(info: ExportInfo, uri: Uri) = launch {
        contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        BatchExportService.startBatchExport(
            this@DrawableListActivity,
            uri,
            adapter.allItemsCopy,
            info,
            appLabel.await(),
            apk.getFile()
        )
    }
}
