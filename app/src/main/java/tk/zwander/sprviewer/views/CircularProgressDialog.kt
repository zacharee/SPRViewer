package tk.zwander.sprviewer.views

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import kotlinx.android.synthetic.main.determinate_progress.view.*
import tk.zwander.sprviewer.R

class CircularProgressDialog(context: Context, var maxProgress: Int) : AlertDialog.Builder(context) {
    private val view = LayoutInflater.from(context).inflate(R.layout.determinate_progress, null)
    private val progress = view.progress

    var onCancelListener: (() -> Unit)? = null

    var currentPercent = 0
    var currentSubPercent = 0

    init {
        setView(view)
        setCancelable(false)

        setNegativeButton(android.R.string.cancel) { _, _ ->
            onCancelListener?.invoke()
        }
    }

    fun setCurrentFileName(name: String) {
        view.filename.text = name
    }

    fun updateProgress(progress: Int) {
        val new = (progress.toFloat() / maxProgress.toFloat() * 100f).toInt()

        if (new != currentPercent) {
            currentPercent = new
            this.progress.progress = currentPercent
        }
    }

    fun updateSubProgress(current: Int, max: Int = 100) {
        val new = (current.toFloat() / max.toFloat() * 100f).toInt()

        if (new != currentSubPercent) {
            currentSubPercent = new
            view.sub_progress.progress = currentSubPercent
        }
    }
}