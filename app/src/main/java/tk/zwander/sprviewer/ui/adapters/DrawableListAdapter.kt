package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.android.synthetic.main.drawable_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.util.getAppDrawables

class DrawableListAdapter(private val itemSelectedListener: (DrawableData) -> Unit) : BaseListAdapter<DrawableData>(DrawableData::class.java) {
    override val viewRes = R.layout.drawable_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BaseVH, position: Int, info: DrawableData) {
        holder.itemView.apply {
            drawable_name.text = "${info.name}.${info.ext}"
            ext_indicator.setText(info.ext)
            drawable_path.text = info.path

            setOnClickListener {
                itemSelectedListener(getInfo(holder.adapterPosition))
            }
        }
    }

    override fun compare(o1: DrawableData, o2: DrawableData): Int {
        val names = o1.name.compareTo(o2.name)
        val paths = o1.path.compareTo(o2.path)
        return if (names == 0) paths else names
    }

    override fun areContentsTheSame(oldItem: DrawableData, newItem: DrawableData): Boolean {
        return oldItem.path == newItem.path
    }

    override fun matches(query: String, data: DrawableData): Boolean {
        return data.path.contains(query, true) || "${data.name}.${data.ext}".contains(query, true)
    }

    fun loadItemsAsync(context: Context, packageName: String, listener: () -> Unit, progressListener: (Int, Int) -> Unit) = async(Dispatchers.IO) {
        context.getAppDrawables(packageName) { data, size, count ->
            launch(Dispatchers.Main) {
                progressListener(size, count)
                add(data)
            }
        }

        launch(Dispatchers.Main) {
            listener()
        }
    }
}