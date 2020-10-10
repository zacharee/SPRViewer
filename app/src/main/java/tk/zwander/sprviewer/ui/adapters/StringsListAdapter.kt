package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.drawable_info_layout.view.ext_indicator
import kotlinx.android.synthetic.main.drawable_info_layout.view.img_preview
import kotlinx.android.synthetic.main.string_list_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.util.*

class StringsListAdapter(private val itemSelectedListener: (StringXmlData) -> Unit) : BaseListAdapter<StringXmlData, StringsListAdapter.StringListVH>(StringXmlData::class.java) {
    override val viewRes = R.layout.string_list_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StringListVH, position: Int, info: StringXmlData) {
        holder.onBind(info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): StringListVH {
        return StringListVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    override fun compare(o1: StringXmlData, o2: StringXmlData): Int {
        val names = o1.locale.toString().compareTo(o2.locale.toString(), true)
        val paths = o1.locale.toString().compareTo(o2.locale.toString(), true)
        return if (names == 0) paths else names
    }

    override fun areContentsTheSame(oldItem: StringXmlData, newItem: StringXmlData): Boolean {
        return oldItem.locale == newItem.locale
    }

    override fun matches(query: String, data: StringXmlData): Boolean {
        return data.constructLabel().contains(query, true)
    }

    fun loadItemsAsync(
        apk: ApkFile,
        listener: () -> Unit,
        progressListener: (Int, Int) -> Unit
    ) = launch {
        val values = withContext(Dispatchers.IO) {
            getAppStringXmls(apk) { d, size, count ->
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

    inner class StringListVH(view: View) : BaseVH(view) {
        @SuppressLint("SetTextI18n")
        fun onBind(info: StringXmlData) {
            itemView.apply {
                ext_indicator.isVisible = true
                img_preview.isVisible = true

                string_file_name.text = info.constructLabel()

                setOnClickListener {
                    itemSelectedListener(getInfo(adapterPosition))
                }
            }
        }
    }
}