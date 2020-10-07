package tk.zwander.sprviewer.util

import android.app.Application
import android.app.ResourcesManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.ui.App
import java.io.File

suspend fun Context.getInstalledApps(listener: (data: AppData, size: Int, count: Int) -> Unit): List<AppData> {
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val ret = ArrayList<AppData>()

    var count = 0

    installedApps.forEachParallel(Dispatchers.IO) {
        count++

        val data = AppData(
            it.packageName,
            it.loadLabel(packageManager).toString(),
            it.loadIcon(packageManager)
        )

        ret.add(data)
        launch(Dispatchers.Main) {
            listener(data, installedApps.size, count)
        }
    }

    return ret
}

fun Context.getAppRes(apk: File): Resources {
    val resMan = ResourcesManager.getInstance()
    val pkgInfo = (applicationContext as Application).mLoadedApk

    return resMan.getResourcesCompat(apk.absolutePath, pkgInfo)
}

private var _picassoInstance: Picasso? = null

val Context.picasso: Picasso
    get() {
        return _picassoInstance ?: Picasso.Builder(this@picasso)
            .build().apply { _picassoInstance = this }
    }

val Context.app: App
    get() = applicationContext as App