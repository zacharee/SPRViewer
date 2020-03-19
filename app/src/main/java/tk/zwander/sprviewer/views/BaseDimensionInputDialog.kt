package tk.zwander.sprviewer.views

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.pixel_input.view.*
import tk.zwander.sprviewer.R

class BaseDimensionInputDialog(
    context: Context,
    okListener: (info: ExportInfo) -> Unit
) : AlertDialog.Builder(context) {
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.pixel_input, null)
        setView(view)
        setTitle(R.string.base_raster_width)

        setPositiveButton(android.R.string.ok) { _, _ ->
            okListener(
                ExportInfo(
                    view.pixel_text?.text?.toString().run {
                        if (this.isNullOrBlank()) 512 else this.toInt()
                    },
                    view.rasterize_xmls.isChecked,
                    view.rasterize_astc.isChecked,
                    view.rasterize_spr.isChecked,
                    view.export_rasters.isChecked,
                    view.export_xmls.isChecked,
                    view.export_astcs.isChecked,
                    view.export_sprs.isChecked
                )
            )
        }

        setNegativeButton(android.R.string.cancel, null)
    }
}

data class ExportInfo(
    val dimen: Int,
    val rasterizeXmls: Boolean,
    val rasterizeAstcs: Boolean,
    val rasterizeSprs: Boolean,
    val exportRasters: Boolean,
    val exportXmls: Boolean,
    val exportAstcs: Boolean,
    val exportSprs: Boolean
)