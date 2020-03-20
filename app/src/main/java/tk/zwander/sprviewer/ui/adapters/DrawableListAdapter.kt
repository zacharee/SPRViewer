package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.android.synthetic.main.drawable_info_layout.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.DrawableData
import tk.zwander.sprviewer.util.getAppDrawables
import tk.zwander.sprviewer.util.mainHandler

class DrawableListAdapter(private val itemSelectedListener: (DrawableData) -> Unit) : BaseListAdapter<DrawableData>(DrawableData::class.java) {
    override val viewRes = R.layout.drawable_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BaseVH, position: Int, info: DrawableData) {
        holder.itemView.apply {
            drawable_name.text = "${info.type}/${info.name}.${info.ext}"

            setOnClickListener {
                itemSelectedListener.invoke(getInfo(holder.adapterPosition))
            }
        }
    }

    override fun compare(o1: DrawableData, o2: DrawableData): Int {
        return o1.name.compareTo(o2.name)
    }

    override fun areContentsTheSame(oldItem: DrawableData, newItem: DrawableData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun matches(query: String, data: DrawableData): Boolean {
        return "${data.type}/${data.name}.${data.ext}".contains(query, true)
    }

    fun loadItems(context: Context, packageName: String, listener: () -> Unit, progressListener: (Int, Int) -> Unit) {
        GlobalScope.launch {
            context.getAppDrawables(packageName) { data, size, count ->
                mainHandler.post {
                    progressListener.invoke(size, count)
                }

                add(data)
            }

            mainHandler.post {
                listener.invoke()
            }
        }
    }
}