package tk.zwander.sprviewer.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.databinding.DeterminateProgressBinding

class CircularProgressDialog(context: Context, private var maxProgress: Int = 0) : MaterialAlertDialogBuilder(context) {
    private val view = LayoutInflater.from(context).inflate(R.layout.determinate_progress, null)
    private val binding = DeterminateProgressBinding.bind(view)
    private val progress = binding.progress

    var onCancelListener: (() -> Unit)? = null

    private var currentPercent = 0
    private var currentSubPercent = 0

    init {
        setView(view)

        setNegativeButton(android.R.string.cancel) { _, _ ->
            onCancelListener?.invoke()
        }
    }

    fun setBaseFileName(name: String?) {
        binding.baseName.text = name
    }

    fun setCurrentFileName(name: String?) {
        binding.filename.text = name
    }

    @SuppressLint("SetTextI18n")
    fun updateProgress(progress: Int, maxProgress: Int = this.maxProgress) {
        val new = (progress.toFloat() / maxProgress.toFloat() * 100f).toInt()

        if (new != currentPercent) {
            currentPercent = new
            this.progress.progress = currentPercent
        }

        binding.fileFraction.text = "$progress/$maxProgress"
    }

    fun updateSubProgress(current: Int, max: Int = 100) {
        val new = (current.toFloat() / max.toFloat() * 100f).toInt()

        if (new != currentSubPercent) {
            currentSubPercent = new
            binding.subProgress.progress = currentSubPercent
        }
    }
}