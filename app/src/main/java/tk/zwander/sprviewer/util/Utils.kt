package tk.zwander.sprviewer.util

import android.app.Activity
import android.app.LoadedApk
import android.app.ResourcesManager
import android.content.pm.PackageParser
import android.content.res.CompatibilityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.IBinder
import android.view.Display
import android.view.View
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.createBalloon
import kotlinx.coroutines.*
import net.dongliu.apk.parser.AbstractApkFile
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourcePackage
import net.dongliu.apk.parser.struct.resource.ResourceTable
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.UDrawableData
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.ZipFile
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

suspend fun getAppDrawables(
    apk: ApkFile,
    packageInfo: PackageParser.Package,
    drawableFound: (data: UDrawableData, size: Int, count: Int) -> Unit
): List<UDrawableData> = coroutineScope {
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

    val loopRange: suspend CoroutineScope.(start: Int, end: Int) -> Unit = { start: Int, end: Int ->
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
                        apk,
                        packageInfo
                    )

                    count++
                    list.add(data)
                    drawableFound(data, totalSize, count)
                }
            } catch (e: Resources.NotFoundException) {
            }
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

    return@coroutineScope list
}

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

fun Activity.showTitleSnackBar(anchor: View) {
    createBalloon(this) {
        text = title.toString()
        setPadding(16)
        setCornerRadius(12f)
        autoDismissDuration = 2500L
        setTextSize(20f)
        widthRatio = 1.0f
        setBackgroundColorResource(R.color.colorSecondaryLight)
        setArrowOrientation(ArrowOrientation.TOP)
        arrowPosition = 0.2f
    }.apply {
        showAlignBottom(anchor)
        setOnBalloonOutsideTouchListener { _, _ ->
            dismiss()
        }
    }
}
