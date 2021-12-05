package tk.zwander.sprviewer.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.databinding.AppInfoLayoutBinding
import tk.zwander.sprviewer.util.getInstalledApps

class AppListAdapter(private val itemSelectedListener: (AppData) -> Unit, private val valuesSelectedListener: (AppData) -> Unit) : BaseListAdapter<AppData, AppListAdapter.AppVH>() {
    override val viewRes = R.layout.app_info_layout

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): AppVH {
        return AppVH(
            LayoutInflater.from(parent.context).inflate(
                viewRes, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: AppVH, position: Int, info: AppData) {
        holder.bind(info)
    }

    override fun matches(query: String, data: AppData): Boolean {
        return data.pkg.contains(query, true) || data.constructLabel().contains(query, true)
    }

    override fun compare(o1: AppData, o2: AppData): Int {
        val initialCompare = o1.constructLabel().compareTo(o2.constructLabel(), true)
        return if (initialCompare != 0) initialCompare else
            o1.pkg.compareTo(o2.pkg)
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

    inner class AppVH(view: View) : BaseVH(view) {
        private val binding = AppInfoLayoutBinding.bind(itemView)

        fun bind(info: AppData) {
            binding.apply {
                icon.setImageDrawable(info.icon)
                appName.text = info.constructLabel()
                appPkg.text = info.pkg

                extrasLayout.isVisible = info.expanded
                viewImages.setOnClickListener {
                    itemSelectedListener(getInfo(bindingAdapterPosition))
                }
                viewStrings.setOnClickListener {
                    valuesSelectedListener(getInfo(bindingAdapterPosition))
                }
            }

            itemView.setOnClickListener {
                getInfo(bindingAdapterPosition).apply {
                    expanded = !expanded
                    notifyItemChanged(bindingAdapterPosition)
                }
            }
        }
    }
}