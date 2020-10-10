package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringData
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.ui.adapters.StringsViewAdapter

class StringsViewActivity : BaseActivity<StringData, StringsViewAdapter.StringViewVH>() {
    companion object {
        const val REQ_SAVE_STRINGS = 2011

        var stringInfo: StringXmlData? = null

        fun start(context: Context, info: StringXmlData, pkg: String) {
            val intent = Intent(context, StringsViewActivity::class.java)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)

            stringInfo = info

            context.startActivity(intent)
        }
    }

    override val contentView = R.layout.activity_main
    override val hasBackButton = true

    override val adapter = StringsViewAdapter {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip = ClipData.newPlainText(
            it.value, it.value
        )
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private val pkgName by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }

    private var saveAll: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (stringInfo == null || pkgName == null) {
            finish()
            return
        }

        adapter.loadItemsAsync(stringInfo!!, this::onLoadFinished)

        updateTitle()
    }

    override fun onDestroy() {
        stringInfo = null
        super.onDestroy()
    }

    override fun onLoadFinished() {
        super.onLoadFinished()

        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }

        updateTitle(adapter.itemCount)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.batch, menu)

        saveAll = menu.findItem(R.id.all)
        saveAll?.setOnMenuItemClickListener {
            val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            createIntent.type = "text/xml"
            createIntent.addCategory(Intent.CATEGORY_OPENABLE)
            createIntent.putExtra(Intent.EXTRA_TITLE, "${pkgName}-strings_${stringInfo!!.locale}.xml")
            startActivityForResult(createIntent, REQ_SAVE_STRINGS)

            true
        }
        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == REQ_SAVE_STRINGS
            && data != null) {
            contentResolver.openOutputStream(data.data).bufferedWriter().use {
                val xml = stringInfo ?: return@use
                it.write(xml.asXmlString())
            }
        }
    }

    private fun updateTitle(numberStrings: Int = -1) = launch {
        title = withContext(Dispatchers.IO) {
            stringInfo!!.constructLabel()
        } + if (numberStrings > -1) " ($numberStrings)" else ""
    }
}