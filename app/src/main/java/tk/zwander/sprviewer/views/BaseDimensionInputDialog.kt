package tk.zwander.sprviewer.views

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.pixel_input.view.*
import tk.zwander.sprviewer.R

class BaseDimensionInputDialog(
    context: Context,
    okListener: (dimen: Int, rasterizeXmls: Boolean, exportRasters: Boolean) -> Unit
) : AlertDialog.Builder(context) {
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.pixel_input, null)
        setView(view)
        setTitle(R.string.base_raster_width)

        setPositiveButton(android.R.string.ok) { _, _ ->
            okListener(
                view.pixel_text.text?.toString()?.toInt() ?: 512,
                view.rasterize_xmls.isChecked,
                view.export_rasters.isChecked
            )
        }

        setNegativeButton(android.R.string.cancel, null)
    }
}