package tk.zwander.sprviewer.ui.adapters

import android.content.Context
import android.view.ViewGroup
import kotlinx.android.synthetic.main.app_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.util.getInstalledApps

class AppListAdapter(private val itemSelectedListener: (AppData) -> Unit) : BaseListAdapter<AppData, BaseListAdapter.BaseVH>(AppData::class.java) {
    override val viewRes = R.layout.app_info_layout

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): BaseVH {
        return createBaseViewHolder(parent, position)
    }

    override fun onBindViewHolder(holder: BaseVH, position: Int, info: AppData) {
        holder.itemView.apply {
            icon.setImageDrawable(info.icon)
            app_name.text = info.constructLabel()
            app_pkg.text = info.pkg

            setOnClickListener {
                itemSelectedListener(getInfo(holder.adapterPosition))
            }
        }
    }

    override fun matches(query: String, data: AppData): Boolean {
        return data.pkg.contains(query, true) || data.constructLabel().contains(query, true)
    }

    override fun compare(o1: AppData, o2: AppData): Int {
        return o1.constructLabel().compareTo(o2.constructLabel(), true)
    }

    override fun areContentsTheSame(oldItem: AppData, newItem: AppData): Boolean {
        return oldItem.pkg == newItem.pkg
    }

    fun loadItemsAsync(context: Context, listener: () -> Unit, progressListener: (Int, Int) -> Unit) = async(Dispatchers.IO) {
        val apps = context.getInstalledApps { _, size, count ->
            launch(Dispatchers.Main) {
                progressListener(size, count)
            }
        }

        launch(Dispatchers.Main) {
            addAll(apps)
            listener()
        }
    }
}