package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter
import tk.zwander.sprviewer.ui.services.BatchExportService
import tk.zwander.sprviewer.util.getFile
import tk.zwander.sprviewer.views.BaseDimensionInputDialog
import tk.zwander.sprviewer.views.ExportInfo


@Suppress("DeferredResultUnused")
class DrawableListActivity : BaseListActivity<UDrawableData, DrawableListAdapter.ListVH>() {
    override val adapter by lazy {
        DrawableListAdapter(remRes) {
            val viewIntent = Intent(this, DrawableViewActivity::class.java)
            viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_INFO, it.toDrawableData())
            viewIntent.putExtras(intent)

            startActivity(viewIntent)
        }
    }

    override val saveAllAct: ActivityResultLauncher<Uri> = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
        BaseDimensionInputDialog(this) { info ->
            handleBatchExport(info, result)
        }.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.loadItemsAsync(apk, packageInfo, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
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
