package tk.zwander.sprviewer.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.Resources
import android.view.View
import android.widget.PopupWindow
import androidx.collection.ArraySet
import com.skydoves.balloon.createBalloon
import kotlinx.coroutines.*
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.struct.resource.ResourceTable
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.coroutines.CoroutineContext

suspend fun getAppStringXmls(
    apk: ApkFile,
    stringXmlFound: (data: StringXmlData, size: Int, count: Int) -> Unit
): Collection<StringXmlData> = coroutineScope {
    val table = apk.resourceTable
    val map = ConcurrentHashMap<Locale, StringXmlData>()

    var count = 0
    var totalSize = table.packageMap.size

    table.packageMap.forEach { (k, v) ->
        val (pkgCode, resPkg) = k.toInt() to v

        val stringsIndex =
            resPkg.typeSpecMap.filter { it.value.name == "string" }.entries.elementAtOrNull(0)

        val stringsStart =
            if (stringsIndex != null) (stringsIndex.key.toInt() shl 16) or (pkgCode shl 24) else -1

        val stringsSize = stringsIndex?.value?.entryFlags?.size ?: 0

        val resInfos = LinkedList<List<ResourceTable.Resource>>()

        val loopRange: suspend CoroutineScope.(start: Int, end: Int) -> Unit = { start: Int, end: Int ->
            for (i in start until end) {
                try {
                    val r = table.getResourcesById(i.toLong())
                    if (r.isEmpty()) continue

                    resInfos.add(r)
                    totalSize += r.size
                } catch (ignored: Resources.NotFoundException) {}
            }
        }

        if (stringsStart != -1) {
            loopRange(stringsStart, stringsStart + stringsSize)

            resInfos.forEach {
                it.forEach { res ->
                    val locale = res.type.locale

                    if (map.containsKey(locale)) {
                        map[locale]!!.apply {
                            values.add(StringData(
                                res.resourceEntry.key,
                                res.resourceEntry.toStringValue(table, locale)
                            ))
                        }
                    } else {
                        map[locale] = StringXmlData(
                            locale
                        ).apply {
                            values.add(StringData(
                                res.resourceEntry.key,
                                res.resourceEntry.toStringValue(table, locale)
                            ))
                        }
                    }

                    count++
                    stringXmlFound(map[locale]!!, totalSize, count)
                }
            }
        }
    }

    return@coroutineScope map.values
}

suspend fun getAppValues(
    apk: ApkFile,
    packageInfo: CustomPackageInfo,
    valueFound: (data: UValueData, size: Int, count: Int) -> Unit
): Collection<UValueData> = coroutineScope {
    val table = apk.resourceTable
    var totalSize = table.packageMap.size
    var count = 0

    val list = ArraySet<UValueData>()

    table.packageMap.forEach { (k, v) ->
        val (pkgCode, resPkg) = k.toInt() to v

        val stringsIndex =
            resPkg.typeSpecMap.filter { it.value.name == "string" }.entries.elementAtOrNull(0)

        val stringsStart =
            if (stringsIndex != null) (stringsIndex.key.toInt() shl 16) or (pkgCode shl 24) else -1

        val stringsSize = stringsIndex?.value?.entryFlags?.size ?: 0

        totalSize += stringsSize

        val loopRange: suspend CoroutineScope.(start: Int, end: Int) -> Unit = { start: Int, end: Int ->
            for (i in start until end) {
                try {
                    val r = table.getResourcesById(i.toLong())
                    if (r.isEmpty()) continue

                    val data = UValueData(
                        "string",
                        r[0].resourceEntry.key,
                        apk.getFile().absolutePath,
                        i,
                        apk,
                        packageInfo,
                        r[0].resourceEntry.toStringValue(table, Locale.getDefault()),
                        mutableListOf()
                    )

                    r.forEach {
                        data.values.add(
                            LocalizedValueData(
                                it.type.locale,
                                it.resourceEntry.toStringValue(table, it.type.locale)
                            )
                        )

                        count++
                        list.add(data)
                        valueFound(data, totalSize, count)
                    }
                } catch (ignored: Resources.NotFoundException) {}
            }
        }

        if (stringsStart != -1) {
            loopRange(stringsStart, stringsStart + stringsSize)
        }
    }

    return@coroutineScope list
}

suspend fun getAppDrawables(
    apk: ApkFile,
    packageInfo: CustomPackageInfo,
    drawableFound: (data: UDrawableData, size: Int, count: Int) -> Unit
): List<UDrawableData> = coroutineScope {
    val table = apk.resourceTable
    var totalSize = table.packageMap.size
    var count = 0

    val list = ArrayList<UDrawableData>()

    table.packageMap.forEach { (k, v) ->
        val (pkgCode, resPkg) = k.toInt() to v

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
        totalSize += drawableSize + mipmapSize + rawSize

        val loopRange: suspend CoroutineScope.(start: Int, end: Int) -> Unit = { start: Int, end: Int ->
            for (i in start until end) {
                try {
                    val r = table.getResourcesById(i.toLong())
                    if (r.isEmpty()) continue

                    r.forEach { resource ->
                        val entry = resource.resourceEntry

                        val pathOrColor = entry.toStringValue(table, Locale.getDefault())

                        val split = pathOrColor.split("/")
                        val fullName = split.last().split(".")

                        val typeMask = 0x00ff0000
                        val typeSpec = resPkg.getTypeSpec(((typeMask and i) - 0xffff).toShort())
                        val typeName = typeSpec?.name!!
                        val name = entry.key
                        val ext = if (fullName.size > 1) fullName.subList(1, fullName.size)
                            .joinToString(".") else null

                        val data = UDrawableData(
                            typeName,
                            name,
                            ext,
                            pathOrColor,
                            i,
                            apk,
                            packageInfo,
                            resource.type.config
                        )

                        count++
                        list.add(data)
                        drawableFound(data, totalSize, count)
                    }
                } catch (ignored: Resources.NotFoundException) {}
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

@SuppressLint("Range")
fun Activity.showTitleSnackBar(anchor: View) {
    createBalloon(this) {
        setPadding(8)
        setPaddingTop(16)
        setCornerRadius(12f)
        setBackgroundDrawableResource(R.drawable.snackbar_background)
        setTextSize(20f)
        setArrowSize(0)
        setMargin(0)
        setElevation(0)
        setBalloonAnimationStyle(R.style.SnackbarAnimStyle)

        text = title.toString()
        autoDismissDuration = -1L
        widthRatio = 1.0f
        isVisibleArrow = false
    }.apply {
        val popupWindow = this::class.java.getDeclaredField("bodyWindow")
            .apply { isAccessible = true }
            .get(this) as PopupWindow

        popupWindow.overlapAnchor = true

        showAsDropDown(anchor, 0,
            window.decorView
                .findViewById<View>(R.id.action_bar)
                .height - dpAsPx(12))

        setOnBalloonOutsideTouchListener { _, _ ->
            dismiss()
        }
    }
}
