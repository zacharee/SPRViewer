package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.StringXmlData
import tk.zwander.sprviewer.databinding.StringListInfoLayoutBinding
import tk.zwander.sprviewer.util.*

class StringsListAdapter(private val itemSelectedListener: (StringXmlData) -> Unit) : BaseListAdapter<StringXmlData, StringsListAdapter.StringListVH>() {
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
            getAppStringXmls(apk) { _, size, count ->
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
        private val binding = StringListInfoLayoutBinding.bind(itemView)

        @SuppressLint("SetTextI18n")
        fun onBind(info: StringXmlData) {
            binding.apply {
                extIndicator.isVisible = true
                imgPreview.isVisible = true

                extIndicator.setText(info.locale.toString().run {
                    if (isBlank()) {
                        "DEF"
                    } else {
                        substring(0, 4.coerceAtMost(length))
                    }
                })

                stringFileName.text = info.constructLabel()
                stringCount.text = info.values.size.toString()
            }

            itemView.setOnClickListener {
                itemSelectedListener(getInfo(bindingAdapterPosition))
            }
        }
    }
}