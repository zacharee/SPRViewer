package tk.zwander.sprviewer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.*
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringData
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.databinding.DrawableInfoLayoutBinding

class StringsViewAdapter(private val itemSelectedCallback: (item: StringData) -> Unit) : BaseListAdapter<StringData, StringsViewAdapter.StringViewVH>() {
    override val viewRes = R.layout.drawable_info_layout

    override fun compare(o1: StringData, o2: StringData): Int {
        return o1.compareTo(o2)
    }

    override fun areContentsTheSame(oldItem: StringData, newItem: StringData): Boolean {
        return oldItem == newItem
    }

    override fun matches(query: String, data: StringData): Boolean {
        return data.key.contains(query, true) || data.value.contains(query, true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): StringViewVH {
        return StringViewVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    override fun onBindViewHolder(holder: StringViewVH, position: Int, info: StringData) {
        holder.onBind(info)
    }

    fun loadItemsAsync(
        info: StringXmlData,
        listener: () -> Unit
    ) = launch {
        withContext(Dispatchers.Main) {
            delay(100)

            addAll(info.values)

            listener()
        }
    }

    inner class StringViewVH(view: View) : BaseVH(view) {
        private val binding = DrawableInfoLayoutBinding.bind(itemView)

        fun onBind(info: StringData) {
            binding.apply {
                drawableName.text = info.key
                drawablePath.text = info.value

                extIndicator.setText("STR")
            }

            itemView.setOnClickListener {
                itemSelectedCallback(getInfo(bindingAdapterPosition))
            }
        }
    }
}