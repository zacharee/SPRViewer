package tk.zwander.sprviewer.ui

import android.app.Application
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.sprviewer.util.BatchExportListener

class App : Application() {
    val batchExportListeners = ArrayList<BatchExportListener>()

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }
    }
}