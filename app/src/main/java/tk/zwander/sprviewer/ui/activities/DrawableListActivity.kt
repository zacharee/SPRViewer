package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.os.Bundle
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.DrawableListAdapter

class DrawableListActivity : BaseActivity<DrawableListAdapter>() {
    override val contentView = R.layout.activity_main
    override val adapter = DrawableListAdapter {
        val viewIntent = Intent(this, DrawableViewActivity::class.java)
        viewIntent.putExtra(DrawableViewActivity.EXTRA_DRAWABLE_NAME, it.name)
        viewIntent.putExtras(intent)

        startActivity(viewIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)

        if (pkg == null)  {
            finish()
            return
        }

        adapter.loadItems(this, pkg, this::onLoadFinished)
    }
}
