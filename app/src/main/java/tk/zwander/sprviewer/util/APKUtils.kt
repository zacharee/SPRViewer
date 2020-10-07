package tk.zwander.sprviewer.util

import android.content.pm.PackageParser
import android.os.Build
import net.dongliu.apk.parser.AbstractApkFile
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourceTable
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

fun AbstractApkFile.getResourceTable(): ResourceTable {
    val data = getFileData(AndroidConstants.RESOURCE_FILE) ?: return ResourceTable()
    val buffer = ByteBuffer.wrap(data)
    val parser = ResourceTableParser(buffer)

    parser.parse()

    return parser.resourceTable
}

fun ApkFile.getFile(): File {
    return ApkFile::class.java
        .getDeclaredField("apkFile")
        .apply { isAccessible = true }
        .get(this) as File
}

fun ApkFile.getZipFile(): ZipFile {
    return ApkFile::class.java
        .getDeclaredField("zf")
        .apply { isAccessible = true }
        .get(this) as ZipFile
}

fun PackageParser.parsePackageCompat(
    packageFile: File,
    flags: Int,
    useCaches: Boolean
): PackageParser.Package {
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
        parsePackage(packageFile, flags, useCaches)
    } else {
        parsePackage(packageFile, flags)
    }
}
