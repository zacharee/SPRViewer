package tk.zwander.sprviewer.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import net.dongliu.apk.parser.AbstractApkFile
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourceTable
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.data.DrawableData
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
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

fun AbstractApkFile.getResourceTable(): ResourceTable {
    val data = getFileData(AndroidConstants.RESOURCE_FILE) ?: return ResourceTable()
    val buffer = ByteBuffer.wrap(data)
    val parser = ResourceTableParser(buffer)

    parser.parse()

    return parser.resourceTable
}

fun Context.getAppDrawables(
    packageName: String,
    drawableFound: (data: DrawableData, size: Int, count: Int) -> Unit
): List<DrawableData> {
    val apk = ApkFile(packageManager.getApplicationInfo(packageName, 0).sourceDir)
    val table = apk.getResourceTable()

    val res = getAppRes(packageName)
    val list = ArrayList<DrawableData>()

    val drawableStart = findDrawableRangeStart(res, packageName)
    val mipmapStart = findMipmapRangeStart(res, packageName)
    val rawStart = findRawRangeStart(res, packageName)

    val drawableMax = drawableStart + 0xffff
    val drawableSize = drawableMax - drawableStart

    val mipmapMax = mipmapStart + 0xffff
    val mipmapSize = mipmapMax - mipmapStart

    val rawMax = rawStart + 0xffff
    val rawSize = rawMax - rawStart

    val totalSize = if (drawableStart != -1) drawableSize else 0 +
            if (mipmapStart != -1) mipmapSize else 0 +
                    if (rawStart != -1) rawSize else 0

    var count = 0

    val loopRange: (start: Int, end: Int) -> Unit = { start: Int, end: Int ->
        for (i in start until end) {
            try {
                val r = table.getResourcesById(i.toLong())
                if (r.isEmpty()) continue

                val paths = r.map { it.resourceEntry }

                paths.forEach {
                    val pathOrColor = it.toStringValue(table, Locale.getDefault())

                    val split = pathOrColor.split("/")
                    val fullName = split.last().split(".")

                    val typeName = res.getResourceTypeName(i)
                    val name = res.getResourceEntryName(i)
                    val ext = if (fullName.size > 1) fullName.subList(1, fullName.size).joinToString(".") else null

                    val data = DrawableData(
                        typeName,
                        name,
                        ext,
                        pathOrColor,
                        i
                    )

                    count++
                    list.add(data)
                    mainHandler.post { drawableFound.invoke(data, totalSize, count) }
                }
            } catch (e: Resources.NotFoundException) {}
        }
    }

    if (drawableStart != -1) {
        loopRange(drawableStart, drawableMax)
    }
    if (mipmapStart != -1) {
        loopRange(mipmapStart, mipmapMax)
    }
    if (rawStart != -1) {
        loopRange(rawStart, rawMax)
    }

    return list
}

fun Context.getAppRes(packageName: String): Resources =
    packageManager.getResourcesForApplication(packageName)

fun findRangeStart(res: Resources, pkg: String, which: String): Int {
    val base = if (pkg == "android") 0x0 else 0x7f000000
    val max = 0x7f200000

    val mult = 0x10000

    for (i in base until max step mult) {
        try {
            val type = res.getResourceTypeName(i)

            if (type == which) return i
        } catch (e: Exception) {
        }
    }

    return -1
}

fun findDrawableRangeStart(res: Resources, pkg: String): Int {
    return findRangeStart(res, pkg, "drawable")
}
fun findMipmapRangeStart(res: Resources, pkg: String): Int {
    return findRangeStart(res, pkg, "mipmap")
}
fun findRawRangeStart(res: Resources, pkg: String): Int {
    return findRangeStart(res, pkg, "raw")
}

val extensionsToRasterize = arrayOf(
    "spr",
    "xml",
    "astc"
)

fun <T> CoroutineScope.lazyDeferred(
    context: CoroutineContext = Dispatchers.Default,
    block: suspend CoroutineScope.() -> T
): Lazy<Deferred<T>> {
    return lazy {
        async(context = context, start = CoroutineStart.LAZY) {
            block.invoke(this)
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun <T> Deferred<T>.getOrAwaitResult() = if (isCompleted) getCompleted() else await()