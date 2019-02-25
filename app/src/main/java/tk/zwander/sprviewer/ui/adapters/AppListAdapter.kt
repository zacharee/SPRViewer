package tk.zwander.sprviewer.ui.adapters

import android.content.Context
import kotlinx.android.synthetic.main.app_info_layout.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.util.getInstalledApps
import tk.zwander.sprviewer.util.mainHandler

class AppListAdapter(private val itemSelectedListener: (AppData) -> Unit, private val progressListener: (AppData?, Int, Int) -> Unit) : BaseListAdapter<AppData>(AppData::class.java) {
    override val viewRes = R.layout.app_info_layout

    override fun onBindViewHolder(holder: BaseVH, position: Int, info: AppData) {
        holder.itemView.apply {
            icon.setImageDrawable(info.icon)
            app_name.text = info.label
            app_pkg.text = info.pkg

            setOnClickListener {
                itemSelectedListener.invoke(getInfo(holder.adapterPosition))
            }
        }
    }

    override fun matches(query: String, data: AppData): Boolean {
        return data.pkg.toLowerCase().contains(query) || data.label.toLowerCase().contains(query)
    }

    override fun compare(o1: AppData, o2: AppData): Int {
        return o1.label.compareTo(o2.label)
    }

    override fun areContentsTheSame(oldItem: AppData, newItem: AppData): Boolean {
        return oldItem.pkg == newItem.pkg
    }

    fun loadItems(context: Context, listener: () -> Unit) {


        GlobalScope.launch {
            val apps = context.getInstalledApps(progressListener)

            mainHandler.post {
                apps.forEach {
                    add(it)
                }

                listener.invoke()
            }
        }
    }
}