package tk.zwander.sprviewer.views

import android.content.Context
import android.os.Parcelable
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.databinding.PixelInputBinding

class BaseDimensionInputDialog(
    context: Context,
    okListener: (info: ExportInfo) -> Unit
) : MaterialAlertDialogBuilder(context) {
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.pixel_input, null)
        val binding = PixelInputBinding.bind(view)
        setView(view)
        setTitle(R.string.base_raster_width)

        setPositiveButton(android.R.string.ok) { _, _ ->
            okListener(
                ExportInfo(
                    binding.pixelText.text?.toString().run {
                        if (this.isNullOrBlank()) 512 else this.toInt()
                    },
                    binding.rasterizeXmls.isChecked,
                    binding.rasterizeAstc.isChecked,
                    binding.rasterizeSpr.isChecked,
                    binding.exportRasters.isChecked,
                    binding.exportXmls.isChecked,
                    binding.exportAstcs.isChecked,
                    binding.exportSprs.isChecked,
                    binding.deobNames.isChecked
                )
            )
        }

        setNegativeButton(android.R.string.cancel, null)
    }
}

@Parcelize
data class ExportInfo(
    val dimen: Int,
    val rasterizeXmls: Boolean,
    val rasterizeAstcs: Boolean,
    val rasterizeSprs: Boolean,
    val exportRasters: Boolean,
    val exportXmls: Boolean,
    val exportAstcs: Boolean,
    val exportSprs: Boolean,
    val deobNames: Boolean
) : Parcelable