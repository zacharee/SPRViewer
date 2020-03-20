package tk.zwander.sprviewer.views

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.marginStart
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import kotlinx.android.synthetic.main.drawable_info_layout.view.*
import tk.zwander.sprviewer.R

class ExtensionIndicator(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    val text: TextView
        get() = findViewById(R.id.text)

    init {
        background = resources.getDrawable(R.drawable.solid_circle, context.theme)
        gravity = Gravity.CENTER
    }

    fun setText(text: CharSequence?) {
        this.text.text = text

        if (!isInEditMode) {
            val backgroundColor = when (text) {
                "png" -> {
                    R.color.png
                }
                "jpg" -> {
                    R.color.jpg
                }
                "xml" -> {
                    R.color.xml
                }
                "spr" -> {
                    R.color.spr
                }
                "astc" -> {
                    R.color.astc
                }
                "webp" -> {
                    R.color.webp
                }
                "9.png" -> {
                    R.color.ninepng
                }
                else -> {
                    R.color.other
                }
            }

            background?.setTint(resources.getColor(backgroundColor, context.theme))
        }
    }

    class ExtensionIndicatorText(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {
        init {
            setTextColor(resources.getColor(android.R.color.white, context.theme))
            gravity = Gravity.CENTER
            setSingleLine(true)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                5,
                18,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            isAllCaps = true
        }
    }
}