package tk.zwander.sprviewer.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import kotlinx.coroutines.*
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.data.DrawableData
import kotlin.coroutines.CoroutineContext

val mainHandler = Handler(Looper.getMainLooper())

fun Context.getInstalledApps(listener: (data: AppData, size: Int, count: Int) -> Unit): List<AppData> {
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
    val ret = ArrayList<AppData>()

    var count = 0

    installedApps.forEach {
        count++

        val data = AppData(
            it.packageName,
            it.loadLabel(packageManager).toString(),
            it.loadIcon(packageManager)
        )

        ret.add(data)
        mainHandler.post { listener.invoke(data, installedApps.size, count) }
    }

    return ret
}

fun Context.getAppDrawables(packageName: String, drawableFound: (data: DrawableData, size: Int, count: Int) -> Unit): List<DrawableData> {
    val res = getAppRes(packageName)
    val list = ArrayList<DrawableData>()

    val start = findDrawableRangeStart(res, packageName)
    val max = start + 0xffff
    val size = max - start

    var count = 0

    for (i in start until start + 0xffff) {
        try {
            val data = DrawableData(
                res.getResourceEntryName(i),
                res.getExtension(i),
                i
            )

            count++
            list.add(data)
            mainHandler.post { drawableFound.invoke(data, size, count) }
        } catch (e: Resources.NotFoundException) {}
    }

    return list
}

fun Context.getAppRes(packageName: String) =
    packageManager.getResourcesForApplication(packageName)

fun findDrawableRangeStart(res: Resources, pkg: String): Int {
    val base = if (pkg == "android") 0x0 else 0x7f000000
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

fun Resources.getExtension(id: Int): String? {
    val v = TypedValue()
    getValue(id, v, false)

    val string = v.coerceToString()

    return try {
        string.split(".").run { subList(1, size) }.joinToString(".")
    } catch (e: Exception) {
        null
    }
}

fun <T> CoroutineScope.lazyDeferred(context: CoroutineContext = Dispatchers.Default, block: suspend CoroutineScope.() -> T): Lazy<Deferred<T>> {
    return lazy {
        async(context = context, start = CoroutineStart.LAZY) {
            block.invoke(this)
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun <T> Deferred<T>.getOrAwaitResult() = if (isCompleted) getCompleted() else await()