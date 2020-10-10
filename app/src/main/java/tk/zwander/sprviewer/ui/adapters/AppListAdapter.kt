package tk.zwander.sprviewer.ui.adapters

import android.content.Context
import android.view.ViewGroup
import kotlinx.android.synthetic.main.app_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.util.getInstalledApps

class AppListAdapter(private val itemSelectedListener: (AppData) -> Unit, private val valuesSelectedListener: (AppData) -> Unit) : BaseListAdapter<AppData, BaseListAdapter.BaseVH>(AppData::class.java) {
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

            list_values.setOnClickListener {
                valuesSelectedListener(getInfo(holder.adapterPosition))
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

    fun loadItemsAsync(context: Context, listener: () -> Unit, progressListener: (Int, Int) -> Unit) = launch {
        val apps = withContext(Dispatchers.IO) {
            context.getInstalledApps { _, size, count ->
                progressListener(size, count)
            }
        }

        addAll(apps)
        listener()
    }
}