package tk.zwander.sprviewer.ui

import android.app.Application
import tk.zwander.sprviewer.util.BatchExportListener

class App : Application() {
    val batchExportListeners = ArrayList<BatchExportListener>()

    override fun onCreate() {
        super.onCreate()

        Class.forName("dalvik.system.CloseGuard")
            .getMethod("setEnabled", Boolean::class.java)
            .invoke(null, true)
    }
}