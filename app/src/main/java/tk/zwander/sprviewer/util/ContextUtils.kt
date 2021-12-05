package tk.zwander.sprviewer.util

import android.annotation.SuppressLint
import android.app.Application
import android.app.ResourcesManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.util.TypedValue
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.ui.App
import java.io.File
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.roundToInt

suspend fun Context.getInstalledApps(listener: (data: AppData, size: Int, count: Int) -> Unit): Collection<AppData> {
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val ret = ConcurrentLinkedDeque<AppData>()

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

private val loadedResources = HashMap<String, Resources>()

fun Context.getAppRes(apk: File): Resources {
    val resMan = ResourcesManager.getInstance()
    val pkgInfo = (applicationContext as Application).mLoadedApk

    return loadedResources[apk.absolutePath] ?: resMan
        .getResourcesCompat(apk.absolutePath, pkgInfo)
        .apply {
            loadedResources[apk.absolutePath] = this
        }
}

fun destroyAppRes(apk: File) {
    val res = loadedResources[apk.absolutePath] ?: return

    res.assets.close()

    loadedResources.remove(apk.absolutePath)
}

/**
 * Convert a certain DP value to its equivalent in px
 * @param dpVal the chosen DP value
 * @return the DP value in terms of px
 */
fun Context.dpAsPx(dpVal: Number) =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dpVal.toFloat(),
        resources.displayMetrics
    ).roundToInt()

@SuppressLint("StaticFieldLeak")
private var _picassoInstance: Picasso? = null

val Context.picasso: Picasso
    get() {
        return _picassoInstance ?: Picasso.Builder(this@picasso)
            .build().apply { _picassoInstance = this }
    }

val Context.app: App
    get() = applicationContext as App