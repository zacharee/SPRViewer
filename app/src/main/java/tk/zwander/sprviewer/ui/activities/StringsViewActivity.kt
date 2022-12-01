package tk.zwander.sprviewer.ui.activities

import android.content.*
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringData
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.databinding.ActivityMainBinding
import tk.zwander.sprviewer.ui.adapters.StringsViewAdapter

class StringsViewActivity : BaseActivity<StringData, StringsViewAdapter.StringViewVH>() {
    companion object {
        var stringInfo: StringXmlData? = null

        fun start(context: Context, info: StringXmlData, pkg: String) {
            val intent = Intent(context, StringsViewActivity::class.java)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, pkg)

            stringInfo = info

            context.startActivity(intent)
        }
    }

    override val contentView by lazy { binding.root }
    override val hasBackButton = true

    override val recycler: RecyclerView
        get() = binding.recycler
    override val scrollerThumb: FastScrollerThumbView
        get() = binding.scrollerThumb
    override val scroller: FastScrollerView
        get() = binding.scroller

    internal val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override val adapter = StringsViewAdapter {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip = ClipData.newPlainText(
            it.value, it.value
        )
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private val pkgName by lazy { intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) }
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { result ->
        if (result != null) {
            contentResolver.openOutputStream(result).bufferedWriter().use {
                val xml = stringInfo ?: return@use
                it.write(xml.asXmlString())
            }
        }
    }

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
            saveLauncher.launch("${pkgName}-strings_${stringInfo!!.locale}.xml")

            true
        }
        if (isReadyForMenus()) {
            saveAll?.isVisible = true
        }

        return super.onCreateOptionsMenu(menu)
    }

    private fun updateTitle(numberStrings: Int = -1) = launch {
        title = withContext(Dispatchers.IO) {
            stringInfo!!.constructLabel()
        } + if (numberStrings > -1) " ($numberStrings)" else ""
    }
}