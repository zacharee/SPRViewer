package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageParser
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.drawable_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.UValueData
import tk.zwander.sprviewer.util.*

class ValueListAdapter(private val itemSelectedListener: (UValueData) -> Unit) : BaseListAdapter<UValueData, ValueListAdapter.ListVH>(UValueData::class.java) {
    override val viewRes = R.layout.drawable_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ListVH, position: Int, info: UValueData) {
        holder.onBind(info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ListVH {
        return ListVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    override fun compare(o1: UValueData, o2: UValueData): Int {
        val names = o1.name.compareTo(o2.name, true)
        val paths = o1.defaultValue?.compareTo(o2.defaultValue ?: "", true) ?: 0
        return if (names == 0) if (paths == 0) 1 else paths else names
    }

    override fun areContentsTheSame(oldItem: UValueData, newItem: UValueData): Boolean {
        return oldItem.path == newItem.path
    }

    override fun matches(query: String, data: UValueData): Boolean {
        return "${data.type}/${data.name}".contains(query, true) || data.defaultValue?.contains(query, true) == true
    }

    fun loadItemsAsync(
        apk: ApkFile,
        packageInfo: PackageParser.Package,
        listener: () -> Unit,
        progressListener: (Int, Int) -> Unit
    ) = launch {
        val values = withContext(Dispatchers.IO) {
            getAppValues(apk, packageInfo) { d, size, count ->
                launch(Dispatchers.Main) {
                    progressListener(size, count)
                }
            }
        }

        withContext(Dispatchers.Main) {
            addAll(values)

            listener()
        }
    }

    inner class ListVH(view: View) : BaseVH(view) {
        @SuppressLint("SetTextI18n")
        fun onBind(info: UValueData) {
            itemView.apply {
                ext_indicator.isVisible = true
                img_preview.isVisible = true

                drawable_name.text = "${info.type}/${info.name}"
                drawable_path.text = info.defaultValue

                setOnClickListener {
                    itemSelectedListener(getInfo(adapterPosition))
                }
            }
        }
    }
}