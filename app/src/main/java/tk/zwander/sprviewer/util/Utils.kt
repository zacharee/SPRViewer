package tk.zwander.sprviewer.util

import android.app.Application
import android.app.LoadedApk
import android.app.ResourcesManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.CompatibilityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*
import net.dongliu.apk.parser.AbstractApkFile
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourcePackage
import net.dongliu.apk.parser.struct.resource.ResourceTable
import net.dongliu.apk.parser.utils.ResourceLoader
import tk.zwander.sprviewer.data.AppData
import tk.zwander.sprviewer.data.UDrawableData
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

private var _picassoInstance: Picasso? = null

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
        mainHandler.post { listener(data, installedApps.size, count) }
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

fun getAppDrawables(
    apk: ApkFile,
    drawableFound: (data: UDrawableData, size: Int, count: Int) -> Unit
): List<UDrawableData> {
    val table = apk.getResourceTable()
    val (pkgCode, resPkg) = table.packageMap.entries.toList()[0].run { key.toInt() to value }

    val list = ArrayList<UDrawableData>()

    val drawableIndex =
        resPkg.typeSpecMap.filter { it.value.name == "drawable" }.entries.elementAtOrNull(0)
    val mipmapIndex =
        resPkg.typeSpecMap.filter { it.value.name == "mipmap" }.entries.elementAtOrNull(0)
    val rawIndex =
        resPkg.typeSpecMap.filter { it.value.name == "raw" }.entries.elementAtOrNull(0)

    val drawableStart =
        if (drawableIndex != null) (drawableIndex.key.toInt() shl 16) or (pkgCode shl 24) else -1
    val mipmapStart =
        if (mipmapIndex != null) (mipmapIndex.key.toInt() shl 16) or (pkgCode shl 24) else -1
    val rawStart =
        if (rawIndex != null) (rawIndex.key.toInt() shl 16) or (pkgCode shl 24) else -1

    val drawableSize = drawableIndex?.value?.entryFlags?.size ?: 0
    val mipmapSize = mipmapIndex?.value?.entryFlags?.size ?: 0
    val rawSize = rawIndex?.value?.entryFlags?.size ?: 0
    val totalSize = drawableSize + mipmapSize + rawSize

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

                    val typeMask = 0x00ff0000
                    val typeSpec = resPkg.getTypeSpec(((typeMask and i) - 0xffff).toShort())
                    val typeName = typeSpec?.name!!
                    val name = it.key
                    val ext = if (fullName.size > 1) fullName.subList(1, fullName.size)
                        .joinToString(".") else null

                    val data = UDrawableData(
                        typeName,
                        name,
                        ext,
                        pathOrColor,
                        i,
                        apk
                    )

                    count++
                    list.add(data)
                    drawableFound(data, totalSize, count)
                }
            } catch (e: Resources.NotFoundException) {}
        }
    }

    if (drawableStart != -1) {
        loopRange(drawableStart, drawableStart + drawableSize)
    }
    if (mipmapStart != -1) {
        loopRange(mipmapStart, mipmapStart + mipmapSize)
    }
    if (rawStart != -1) {
        loopRange(rawStart, rawStart + rawSize)
    }

    return list
}

fun Context.getAppRes(apk: File): Resources {
    val resMan = ResourcesManager.getInstance()
    val pkgInfo = (applicationContext as Application).mLoadedApk

    return resMan.getResourcesCompat(apk.absolutePath, pkgInfo)
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
            block(this)
        }
    }
}

@ExperimentalCoroutinesApi
suspend fun <T> Deferred<T>.getOrAwaitResult() = if (isCompleted) getCompleted() else await()

fun ApkFile.getFile(): File {
    return ApkFile::class.java
        .getDeclaredField("apkFile")
        .apply { isAccessible = true }
        .get(this) as File
}

val Context.picasso: Picasso
    get() {
        return _picassoInstance ?: Picasso.Builder(this@picasso)
                .build().apply { _picassoInstance = this }
    }

@Suppress("UNCHECKED_CAST")
val ResourceTable.packageMap: Map<Short, ResourcePackage>
    get() = ResourceTable::class.java
        .getDeclaredField("packageMap")
        .apply { isAccessible = true }
        .get(this) as Map<Short, ResourcePackage>

fun ResourcesManager.getResourcesCompat(apkPath: String, pkgInfo: LoadedApk) : Resources {
    return when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> {
            ResourcesManager::class.java
                .getDeclaredMethod(
                    "getTopLevelResources",
                    String::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Int::class.java,
                    Configuration::class.java,
                    CompatibilityInfo::class.java
                )
                .apply { isAccessible = true }
                .invoke(
                    this,
                    apkPath,
                    null,
                    null,
                    null,
                    Display.DEFAULT_DISPLAY,
                    null,
                    pkgInfo.compatibilityInfo
                ) as Resources
        }
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> {
            ResourcesManager::class.java
                .getDeclaredMethod(
                    "getResources",
                    IBinder::class.java,
                    String::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Int::class.java,
                    Configuration::class.java,
                    CompatibilityInfo::class.java,
                    ClassLoader::class.java
                )
                .apply { isAccessible = true }
                .invoke(
                    this,
                    null,
                    apkPath,
                    null,
                    null,
                    null,
                    Display.DEFAULT_DISPLAY,
                    null,
                    pkgInfo.compatibilityInfo,
                    pkgInfo.classLoader
                ) as Resources
        }
        else -> {
            ResourcesManager::class.java
                .getDeclaredMethod(
                    "getResources",
                    IBinder::class.java,
                    String::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Int::class.java,
                    Configuration::class.java,
                    CompatibilityInfo::class.java,
                    ClassLoader::class.java,
                    List::class.java
                )
                .apply { isAccessible = true }
                .invoke(
                    this,
                    apkPath,
                    null,
                    null,
                    null,
                    Display.DEFAULT_DISPLAY,
                    null,
                    pkgInfo.compatibilityInfo,
                    pkgInfo.classLoader,
                    null
                ) as Resources
        }
    }
}