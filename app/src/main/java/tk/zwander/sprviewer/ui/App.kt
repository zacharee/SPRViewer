package tk.zwander.sprviewer.ui

import android.app.Application
import android.os.Build
import com.bugsnag.android.Bugsnag
import com.squareup.picasso.Picasso
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.sprviewer.util.BatchExportListener

class App : Application() {
    val batchExportListeners = ArrayList<BatchExportListener>()

    override fun onCreate() {
        super.onCreate()

        Bugsnag.start(this)

        Picasso.setSingletonInstance(Picasso.Builder(this).build())

        Bugsnag.notify(RuntimeException("Test error"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }
    }
}