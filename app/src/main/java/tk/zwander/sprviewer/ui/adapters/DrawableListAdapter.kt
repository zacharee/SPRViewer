package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.pm.PackageParser
import android.content.res.Resources
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.squareup.picasso.Callback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.CustomPackageInfo
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.databinding.DrawableInfoLayoutBinding
import tk.zwander.sprviewer.util.getAppDrawables
import tk.zwander.sprviewer.util.picasso
import kotlin.Exception

class DrawableListAdapter(private val remRes: Resources, private val itemSelectedListener: (UDrawableData) -> Unit) : BaseListAdapter<UDrawableData, DrawableListAdapter.ListVH>() {
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
        val names = o1.name.compareTo(o2.name, true)
        val paths = o1.path.compareTo(o2.path, true)
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
        packageInfo: CustomPackageInfo,
        listener: () -> Unit,
        progressListener: (Int, Int) -> Unit
    ) = launch {
        val drawables = withContext(Dispatchers.IO) {
            getAppDrawables(apk, packageInfo) { _, size, count ->
                launch(Dispatchers.Main) {
                    progressListener(size, count)
                }
            }
        }

        withContext(Dispatchers.Main) {
            addAll(drawables)
            listener()
        }
    }

    inner class ListVH(view: View) : BaseVH(view) {
        private val binding = DrawableInfoLayoutBinding.bind(itemView)

        @SuppressLint("SetTextI18n")
        fun onBind(info: UDrawableData) {
            itemView.apply {
                binding.extIndicator.isVisible = true
                binding.imgPreview.isVisible = true

                try {
                    context.picasso.cancelRequest(binding.imgPreview)

                    context.picasso.load(
                        Uri.parse(
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${info.packageInfo.packageName}/" +
                                    "${remRes.getResourceTypeName(info.id)}/" +
                                    "${info.id}"
                        )
                    ).into(binding.imgPreview, object : Callback {
                        override fun onError(e: Exception?) {
                            launch {
                                withContext(Dispatchers.Main) {
                                    try {
                                        binding.imgPreview.setImageDrawable(
                                            ResourcesCompat.getDrawable(remRes, info.id, remRes.newTheme()))
                                        binding.extIndicator.isVisible = false
                                    } catch (e: Exception) {
                                        binding.imgPreview.isVisible = false
                                    }
                                }
                            }
                        }

                        override fun onSuccess() {
                            binding.extIndicator.isVisible = false
                        }
                    })

                } catch (e: Exception) {
                    Log.e("SPRViewer", "ERR", e)
                }

                binding.drawableName.text = "${info.name}.${info.ext}"
                binding.extIndicator.setText(info.ext)
                binding.drawablePath.text = info.path

                setOnClickListener {
                    itemSelectedListener(getInfo(bindingAdapterPosition))
                }
            }
        }
    }
}