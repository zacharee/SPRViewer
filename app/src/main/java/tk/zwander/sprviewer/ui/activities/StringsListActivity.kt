package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.content.pm.PackageParser
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.ui.adapters.StringsListAdapter
import tk.zwander.sprviewer.util.*
import java.io.File
import java.util.*


@Suppress("DeferredResultUnused")
class StringsListActivity : BaseListActivity<StringXmlData, StringsListAdapter.StringListVH>() {
    override val adapter = StringsListAdapter {
        StringsViewActivity.start(this, it, packageInfo.packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.loadItemsAsync(apk, this::onLoadFinished) { size, count ->
            progress?.progress = (count.toFloat() / size.toFloat() * 100f).toInt()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == REQ_CHOOSE_OUTPUT_DIR
            && data != null) {
            contentResolver.takePersistableUriPermission(data.data,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            progressItem?.isVisible = true
            progress?.progress = 0

            launch {
                withContext(Dispatchers.IO) {
                    val parentDir = DocumentFile.fromTreeUri(this@StringsListActivity, data.data)
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
    }
}
