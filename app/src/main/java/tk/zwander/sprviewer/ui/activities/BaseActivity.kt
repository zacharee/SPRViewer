package tk.zwander.sprviewer.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.hmomeni.progresscircula.ProgressCircula
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
import kotlinx.android.synthetic.main.activity_main.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.BaseData
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter
import java.util.*

abstract class BaseActivity<T : BaseListAdapter<out BaseData, out BaseListAdapter.BaseVH>> : AppCompatActivity() {
    abstract val contentView: Int
    abstract val adapter: T

    internal var progressItem: MenuItem? = null
    internal var progress: ProgressCircula? = null

    internal var searchItem: MenuItem? = null

    internal var doneLoading = false

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)

        recycler.adapter = adapter

        scroller.setupWithRecyclerView(
            recycler,
            { position ->
                val item = adapter.getItemAt(position)

                FastScrollItemIndicator.Text(
                    item.constructLabel()
                        .substring(0, 1)
                        .toUpperCase(Locale.getDefault())
                )
            }
        )
        scroller.itemIndicatorSelectedCallbacks += object : FastScrollerView.ItemIndicatorSelectedCallback {
            override fun onItemIndicatorSelected(
                indicator: FastScrollItemIndicator,
                indicatorCenterY: Int,
                itemPosition: Int
            ) {
                recycler.scrollToPosition(0)
            }
        }

        window.decorView?.findViewById<View>(androidx.appcompat.R.id.action_bar)?.setOnClickListener {
            scrollToTop()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView?

        searchView?.setOnQueryTextListener(adapter)

        progressItem = menu.findItem(R.id.status_progress)
        progress = progressItem?.actionView as ProgressCircula?

        if (doneLoading) {
            progressItem?.isVisible = false
        }

        checkCount()

        return true
    }

    open fun onLoadFinished() {
        doneLoading = true
        progressItem?.isVisible = false

        checkCount()
    }

    open fun checkCount() {
        if (isReadyForMenus() && searchItem?.isVisible == false) {
            searchItem?.isVisible = true
        }
    }

    fun scrollToTop() {
        recycler.apply {
            val lin = (layoutManager as LinearLayoutManager)
            val firstPos = lin.findFirstVisibleItemPosition()
            val smooth = firstPos < 50


            if (smooth) smoothScrollToPosition(0)
            else scrollToPosition(0)
        }
    }

    internal fun isReadyForMenus(): Boolean {
        return doneLoading && adapter.itemCount > 0
    }
}