package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.squareup.picasso.Callback
import kotlinx.android.synthetic.main.drawable_info_layout.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.util.getAppDrawables
import tk.zwander.sprviewer.util.getAppRes
import tk.zwander.sprviewer.util.getFile
import tk.zwander.sprviewer.util.picasso
import java.lang.Exception

class DrawableListAdapter(private val itemSelectedListener: (UDrawableData) -> Unit) : BaseListAdapter<UDrawableData, DrawableListAdapter.ListVH>(UDrawableData::class.java) {
    override val viewRes = R.layout.drawable_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ListVH, position: Int, info: UDrawableData) {
        holder.onBind(info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ListVH {
        return ListVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    override fun compare(o1: UDrawableData, o2: UDrawableData): Int {
        val names = o1.name.compareTo(o2.name)
        val paths = o1.path.compareTo(o2.path)
        return if (names == 0) paths else names
    }

    override fun areContentsTheSame(oldItem: UDrawableData, newItem: UDrawableData): Boolean {
        return oldItem.path == newItem.path
    }

    override fun matches(query: String, data: UDrawableData): Boolean {
        return data.path.contains(query, true) || "${data.name}.${data.ext}".contains(query, true)
    }

    fun loadItemsAsync(
        apk: ApkFile,
        listener: () -> Unit,
        progressListener: (Int, Int) -> Unit
    ) = async(Dispatchers.IO) {
        getAppDrawables(apk) { data, size, count ->
            launch(Dispatchers.Main) {
                progressListener(size, count)
                add(data)
            }
        }

        launch(Dispatchers.Main) {
            listener()
        }
    }

    inner class ListVH(view: View) : BaseVH(view) {
        @SuppressLint("SetTextI18n")
        fun onBind(info: UDrawableData) {
            itemView.apply {
                ext_indicator.isVisible = true
                img_preview.isVisible = true

                try {
                    val remRes = context.getAppRes(info.file.getFile())

                    context.picasso.cancelRequest(img_preview)

                    context.picasso.load(
                        Uri.parse(
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${info.file.apkMeta.packageName}/" +
                                    "${remRes.getResourceTypeName(info.id)}/" +
                                    "${info.id}"
                        )
                    ).into(img_preview, object : Callback {
                        override fun onError(e: Exception?) {
                            img_preview.isVisible = false
                        }

                        override fun onSuccess() {
                            ext_indicator.isVisible = false
                        }
                    })

                } catch (e: Exception) {
                    Log.e("SPRViewer", "ERR", e)
                }

                drawable_name.text = "${info.name}.${info.ext}"
                ext_indicator.setText(info.ext)
                drawable_path.text = info.path

                setOnClickListener {
                    itemSelectedListener(getInfo(adapterPosition))
                }
            }
        }
    }
}