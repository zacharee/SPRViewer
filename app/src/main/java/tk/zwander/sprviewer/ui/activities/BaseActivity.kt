package tk.zwander.sprviewer.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.hmomeni.progresscircula.ProgressCircula
import kotlinx.android.synthetic.main.activity_main.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter

abstract class BaseActivity<T : BaseListAdapter<out Any>> : AppCompatActivity() {
    abstract val contentView: Int
    abstract val adapter: T

    internal var progressItem: MenuItem? = null
    internal var progress: ProgressCircula? = null

    internal var doneLoading = false

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)

        recycler.adapter = adapter

        window.decorView?.findViewById<View>(androidx.appcompat.R.id.action_bar)?.setOnClickListener {
            scrollToTop()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView?

        searchView?.setOnQueryTextListener(adapter)

        progressItem = menu.findItem(R.id.status_progress)
        progress = progressItem?.actionView as ProgressCircula?

        if (doneLoading) {
            progressItem?.isVisible = false
        }

        return true
    }

    fun onLoadFinished() {
        doneLoading = true
        progressItem?.isVisible = false
    }

    fun scrollToTop(smooth: Boolean = true) {
        recycler.apply {
            if (smooth) smoothScrollToPosition(0)
            else scrollToPosition(0)
        }
    }
}