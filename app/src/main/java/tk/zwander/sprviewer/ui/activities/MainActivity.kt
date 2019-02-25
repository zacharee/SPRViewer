package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.ui.adapters.AppListAdapter

class MainActivity : BaseActivity<AppListAdapter>() {
    override val contentView = R.layout.activity_main
    override val adapter = AppListAdapter(
        {
            val drawableIntent = Intent(this, DrawableListActivity::class.java)
            drawableIntent.putExtra(Intent.EXTRA_PACKAGE_NAME, it.pkg)

            startActivity(drawableIntent)
        },
        { _, size, count ->
            progress.apply {
                progress += (count.toFloat() / size.toFloat() * 100f).toInt()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter.loadItems(this, this::onLoadFinished)
    }
}
