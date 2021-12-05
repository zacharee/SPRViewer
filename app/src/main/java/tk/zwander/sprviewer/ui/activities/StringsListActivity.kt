package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.ui.adapters.StringsListAdapter


@Suppress("DeferredResultUnused")
class StringsListActivity : BaseListActivity<StringXmlData, StringsListAdapter.StringListVH>() {
    override val adapter = StringsListAdapter {
        StringsViewActivity.start(this, it, packageInfo.packageName)
    }

    override val saveAllAct: ActivityResultLauncher<Uri> = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
        if (result == null) return@registerForActivityResult

        contentResolver.takePersistableUriPermission(result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        progressItem?.isVisible = true
        progress?.progress = 0

        launch {
            withContext(Dispatchers.IO) {
                val parentDir = DocumentFile.fromTreeUri(this@StringsListActivity, result)
                val dir = parentDir?.createDirectory(packageInfo.packageName)
                val items = adapter.allItemsCopy

                items.forEachIndexed { index, stringXmlData ->
                    val file = dir?.createFile("text/xml", "strings_${stringXmlData.locale}.xml")

                    contentResolver.openOutputStream(file!!.uri).bufferedWriter().use { writer ->
                        writer.write(stringXmlData.asXmlString())
                    }

                    withContext(Dispatchers.Main) {
                        progress?.progress = ((index + 1f) / items.size.toFloat() * 100f).toInt()
                    }
                }
            }

            progressItem?.isVisible = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.loadItemsAsync(apk, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
        }
    }
}
