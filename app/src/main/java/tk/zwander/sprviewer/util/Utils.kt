package tk.zwander.sprviewer.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.data.DrawableData

val mainHandler = Handler(Looper.getMainLooper())

fun Context.getInstalledApps(listener: (AppData) -> Unit): List<AppData> {
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val ret = ArrayList<AppData>()

    installedApps.forEach {
        val data = AppData(
            it.packageName,
            it.loadLabel(packageManager).toString(),
            it.loadIcon(packageManager)
        )

        ret.add(data)
        mainHandler.post { listener.invoke(data) }
    }

    return ret
}

fun Context.getAppDrawables(packageName: String, drawableFound: (DrawableData) -> Unit): List<DrawableData> {
    val res = getAppRes(packageName)
    val list = ArrayList<DrawableData>()

    val start = findDrawableRangeStart(res)

    for (i in start until start + 0xffff) {
        try {
            val data = DrawableData(
                res.getResourceEntryName(i),
                i
            )

            list.add(data)
            mainHandler.post { drawableFound.invoke(data) }
        } catch (e: Resources.NotFoundException) {}
    }

    return list
}

fun Context.getAppRes(packageName: String) =
        packageManager.getResourcesForApplication(packageName)

fun findDrawableRangeStart(res: Resources): Int {
    val base = 0x7f000000
    val max = 0x7f200000

    val mult = 0x10000

    for (i in base until max step mult) {

        try {
            val type = res.getResourceTypeName(i)

            if (type == "drawable") return i
        } catch (e: Exception) {}
    }

    return base
}

val extensionsToRasterize = arrayOf(
    "spr",
    "xml",
    "astc"
)