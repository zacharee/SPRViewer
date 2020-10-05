package tk.zwander.sprviewer.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import tk.zwander.sprviewer.ui.services.BatchExportService
import tk.zwander.sprviewer.util.BatchExportListener
import tk.zwander.sprviewer.util.app
import tk.zwander.sprviewer.views.CircularProgressDialog

class BatchExportDialogActivity : AppCompatActivity() {
    private val exportListener = object : BatchExportListener {
        override fun onBaseFilenameUpdate(filename: String) {
            dialogBuilder.setBaseFileName(filename)
        }

        override fun onCurrentFilenameUpdate(filename: String) {
            dialogBuilder.setCurrentFileName(filename)
        }

        override fun onProgressUpdate(current: Int, max: Int) {
            dialogBuilder.updateProgress(current, max)
        }

        override fun onSubProgressUpdate(current: Int, max: Int) {
            dialogBuilder.updateSubProgress(current, max)
        }

        override fun onExportComplete() {
            finish()
        }
    }

    private val dialogBuilder by lazy { CircularProgressDialog(this) }
    private val dialog by lazy { dialogBuilder.show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app.batchExportListeners.add(exportListener)

        dialogBuilder.onCancelListener = {
            ContextCompat.startForegroundService(
                this,
                Intent(this, BatchExportService::class.java)
                    .setAction(BatchExportService.ACTION_CANCEL_CURRENT_EXPORT)
            )
            finish()
        }

        dialog.setOnDismissListener {
            if (!isDestroyed) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        app.batchExportListeners.remove(exportListener)
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    }
}