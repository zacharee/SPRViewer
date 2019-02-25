package tk.zwander.sprviewer.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.dimension_input.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.util.TextWatcherAdapter

class DimensionInputDialog(context: Context, private val drawable: Drawable) : AlertDialog.Builder(context) {
    private val defDimen = 1024

    private val intrinsicWidth = drawable.intrinsicWidth
    private val intrinsicHeight = drawable.intrinsicHeight

    private val hwRatio = intrinsicHeight.toFloat() / intrinsicWidth.toFloat()
    private val whRatio = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()

    private val view = LayoutInflater.from(context)
        .inflate(R.layout.dimension_input, null)

    private val widthListener: TextWatcherAdapter = object : TextWatcherAdapter() {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            view.height_input.apply {
                removeTextChangedListener(heightListener)
                setText(getScaledDimen(s, hwRatio, defDimen).toString())
                addTextChangedListener(heightListener)
            }
        }
    }

    private val heightListener: TextWatcherAdapter = object : TextWatcherAdapter() {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            view.width_input.apply {
                removeTextChangedListener(widthListener)
                setText(getScaledDimen(s, whRatio, defDimen).toString())
                addTextChangedListener(widthListener)
            }
        }
    }

    var saveListener: ((width: Int, height: Int) -> Unit)? = null

    init {
        view.width_input.addTextChangedListener(widthListener)
        view.height_input.addTextChangedListener(heightListener)

        setTitle(R.string.enter_dimensions)
        setView(view)

        setPositiveButton(android.R.string.ok) { _, _ ->
            val widthInput = view.width_input.text?.toString()
            val heightInput = view.height_input.text?.toString()

            val (width, height) = if (widthInput.isNullOrBlank() && heightInput.isNullOrBlank()) {
                (if (intrinsicWidth > intrinsicHeight) defDimen else (defDimen * whRatio).toInt()) to
                        (if (intrinsicWidth > intrinsicHeight) (defDimen * hwRatio).toInt() else defDimen)
            } else if (widthInput.isNullOrBlank()) {
                getScaledDimen(heightInput, whRatio, defDimen) to parseDimen(heightInput, defDimen)
            } else {
                parseDimen(widthInput, defDimen) to getScaledDimen(widthInput, hwRatio, defDimen)
            }

            GlobalScope.launch {
                saveListener?.invoke(width, height)
            }
        }
        setNegativeButton(android.R.string.cancel, null)
    }

    private fun getScaledDimen(input: CharSequence?, ratio: Float, def: Int): Int {
        return (parseDimen(input, def) * ratio).toInt()
    }

    private fun parseDimen(input: CharSequence?, def: Int): Int {
        if (input.isNullOrBlank()) return def

        return input.toString().toInt()
    }
}