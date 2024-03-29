package tk.zwander.sprviewer.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.databinding.DimensionInputBinding
import tk.zwander.sprviewer.util.TextWatcherAdapter

class DimensionInputDialog(context: Context, drawable: Drawable) : MaterialAlertDialogBuilder(context) {
    private val defDimen = 1024

    private val intrinsicWidth = drawable.intrinsicWidth
    private val intrinsicHeight = drawable.intrinsicHeight

    private val hwRatio = intrinsicHeight.toFloat() / intrinsicWidth.toFloat()
    private val whRatio = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()

    private val binding = DimensionInputBinding.inflate(LayoutInflater.from(context))

    private val tintDrawable = ContextCompat.getDrawable(context, R.drawable.outlined_circle)!!.mutate() as GradientDrawable
    private var tintColor = Color.TRANSPARENT

    private val widthListener: TextWatcherAdapter = object : TextWatcherAdapter() {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            binding.heightInput.apply {
                removeTextChangedListener(heightListener)
                setText(getScaledDimen(s, hwRatio, defDimen).toString())
                addTextChangedListener(heightListener)
            }
        }
    }

    private val heightListener: TextWatcherAdapter = object : TextWatcherAdapter() {
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            binding.widthInput.apply {
                removeTextChangedListener(widthListener)
                setText(getScaledDimen(s, whRatio, defDimen).toString())
                addTextChangedListener(widthListener)
            }
        }
    }

    var saveListener: ((width: Int, height: Int, tint: Int) -> Unit)? = null

    init {
        binding.widthInput.addTextChangedListener(widthListener)
        binding.heightInput.addTextChangedListener(heightListener)

        binding.drawableTint
            .setCompoundDrawablesRelativeWithIntrinsicBounds(
                tintDrawable,
                null,
                null,
                null
            )

        binding.drawableTintCard.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(tintColor)
                .setShowAlphaSlider(true)
                .setAllowCustom(true)
                .create().apply {
                    setColorPickerDialogListener(object : ColorPickerDialogListener {
                        override fun onColorSelected(dialogId: Int, color: Int) {
                            tintColor = color
                            tintDrawable.setColor(color)
                        }

                        override fun onDialogDismissed(dialogId: Int) {}
                    })

                    val activity = context as FragmentActivity
                    show(activity.supportFragmentManager, "color-picker-dialog")
                }
        }

        setTitle(R.string.enter_dimensions)
        setView(binding.root)

        setPositiveButton(android.R.string.ok) { _, _ ->
            val widthInput = binding.widthInput.text?.toString()
            val heightInput = binding.heightInput.text?.toString()

            val (width, height) = if (widthInput.isNullOrBlank() && heightInput.isNullOrBlank()) {
                (if (intrinsicWidth > intrinsicHeight) defDimen else (defDimen * whRatio).toInt()) to
                        (if (intrinsicWidth > intrinsicHeight) (defDimen * hwRatio).toInt() else defDimen)
            } else if (widthInput.isNullOrBlank()) {
                getScaledDimen(heightInput, whRatio, defDimen) to parseDimen(heightInput, defDimen)
            } else {
                parseDimen(widthInput, defDimen) to getScaledDimen(widthInput, hwRatio, defDimen)
            }

            saveListener?.invoke(width, height, tintColor)
        }
        setNegativeButton(android.R.string.cancel, null)
    }

    private fun getScaledDimen(input: CharSequence?, ratio: Float, @Suppress("SameParameterValue") def: Int): Int {
        return (parseDimen(input, def) * ratio).toInt()
    }

    private fun parseDimen(input: CharSequence?, def: Int): Int {
        if (input.isNullOrBlank()) return def

        return input.toString().toInt()
    }
}