package tk.zwander.sprviewer.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hmomeni.progresscircula.ProgressCircula
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.coroutines.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import tk.zwander.sprviewer.util.showTitleSnackBar
import java.util.*
import kotlin.math.absoluteValue

abstract class BaseActivity<Data : BaseData, VH : BaseListAdapter.BaseVH> : AppCompatActivity(), CoroutineScope by MainScope() {
    abstract val contentView: View
    abstract val adapter: BaseListAdapter<Data, VH>

    internal abstract val recycler: RecyclerView
    internal abstract val scroller: FastScrollerView
    internal abstract val scrollerThumb: FastScrollerThumbView

    internal var progressItem: MenuItem? = null
    internal var progress: ProgressCircula? = null

    private var searchItem: MenuItem? = null
    private var searchView: SearchView? = null

    private var doneLoading = false

    internal open val hasBackButton = false

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)

        if (hasBackButton) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        recycler.layoutManager = object : LinearLayoutManager(this) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }

        adapter.setHasStableIds(true)

        recycler.adapter = adapter

        scroller.setupWithRecyclerView(
            recycler,
            { position ->
                val item = adapter.getInfo(position)

                FastScrollItemIndicator.Text(
                    item.constructLabel()
                        .run {
                            if (isBlank()) "?"
                            else substring(0, 1)
                        }
                        .uppercase(Locale.getDefault())
                )
            }
        )
        scrollerThumb.setupWithFastScroller(scroller)

        scroller.itemIndicatorSelectedCallbacks += object : FastScrollerView.ItemIndicatorSelectedCallback {
            override fun onItemIndicatorSelected(
                indicator: FastScrollItemIndicator,
                indicatorCenterY: Int,
                itemPosition: Int
            ) {
                scrollToPosition(itemPosition)
            }
        }

        window.decorView?.findViewById<View>(R.id.action_bar)?.apply {
            setOnClickListener {
                scrollToPosition()
            }

            setOnLongClickListener {
                showTitleSnackBar(it)

                true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as SearchView?

        searchView?.setOnQueryTextListener(adapter)
        searchView?.isSubmitButtonEnabled = true

        progressItem = menu.findItem(R.id.status_progress)
        progress = progressItem?.actionView as ProgressCircula?

        if (doneLoading) {
            progressItem?.isVisible = false
        }

        searchView?.setOnCloseListener {
            adapter.onQueryTextSubmit(null)
            false
        }

        checkCount()

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (searchView?.isIconified == false) {
            searchView?.onActionViewCollapsed()
            adapter.onQueryTextSubmit(null)
        } else {
            super.onBackPressed()
        }
    }

    open fun onLoadFinished() {
        doneLoading = true
        progressItem?.isVisible = false
        recycler.isVisible = true

        checkCount()
    }

    open fun checkCount() {
        if (isReadyForMenus() && searchItem?.isVisible == false) {
            searchItem?.isVisible = true
        }
    }

    fun scrollToPosition(position: Int = 0) {
        recycler.apply {
            val lin = (layoutManager as LinearLayoutManager)
            val firstPos = lin.findFirstVisibleItemPosition()
            val smooth = (position - firstPos).absoluteValue < 50


            if (smooth) smoothScrollToPosition(position)
            else scrollToPosition(position)
        }
    }

    internal fun isReadyForMenus(): Boolean {
        return doneLoading && adapter.itemCount > 0
    }
}