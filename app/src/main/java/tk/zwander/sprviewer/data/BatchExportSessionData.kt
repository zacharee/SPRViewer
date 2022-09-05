package tk.zwander.sprviewer.data

import android.content.res.Resources
import android.net.Uri
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.struct.resource.ResourceTable
import tk.zwander.sprviewer.views.ExportInfo
import java.io.File

data class BatchExportSessionData(
    val uri: Uri,
    val drawables: List<DrawableData>,
    val exportInfo: ExportInfo,
    val appName: String,
    val appFile: File,
    val apkFile: ApkFile,
    val appPkg: String,
    val remRes: Resources,
    val apkResourceTable: ResourceTable = apkFile.resourceTable
)