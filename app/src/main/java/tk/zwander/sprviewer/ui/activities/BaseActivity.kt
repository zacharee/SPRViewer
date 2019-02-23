package tk.zwander.sprviewer.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import kotlinx.android.synthetic.main.activity_main.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.BaseListAdapter

abstract class BaseActivity<T : BaseListAdapter<out Any>> : AppCompatActivity() {
    abstract val contentView: Int
    abstract val adapter: T

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentView)

        recycler.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as SearchView?

        searchView?.setOnQueryTextListener(adapter)

        return true
    }

    fun onLoadFinished() {
        recycler.visibility = View.VISIBLE
        progress.visibility = View.GONE
    }
}