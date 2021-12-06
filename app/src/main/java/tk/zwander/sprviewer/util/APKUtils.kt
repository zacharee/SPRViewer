package tk.zwander.sprviewer.util

import android.content.pm.PackageParser
import android.content.pm.parsing.ParsingPackage
import android.content.pm.parsing.ParsingPackageUtils
import android.content.pm.parsing.result.ParseInput
import android.content.pm.parsing.result.ParseResult
import android.content.pm.parsing.result.ParseTypeImpl
import android.os.Build
import android.permission.PermissionManager
import net.dongliu.apk.parser.AbstractApkFile
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.parser.ResourceTableParser
import net.dongliu.apk.parser.struct.AndroidConstants
import net.dongliu.apk.parser.struct.resource.ResourceTable
import tk.zwander.sprviewer.data.CustomPackageInfo
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

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

@Suppress("UNCHECKED_CAST")
fun parsePackageCompat(
    packageFile: File,
    flags: Int,
    useCaches: Boolean
): CustomPackageInfo {
    return when {
        Build.VERSION.SDK_INT > Build.VERSION_CODES.R -> {
            val parser = ParseTypeImpl.forParsingWithoutPlatformCompat()
            val result = (ParsingPackageUtils::class.java
                .getDeclaredMethod(
                    "parseDefault",
                    ParseInput::class.java,
                    File::class.java,
                    Int::class.java,
                    List::class.java,
                    Boolean::class.java
                )
                .invoke(
                    null,
                    parser,
                    packageFile,
                    flags,
                    listOf<PermissionManager.SplitPermissionInfo>(),
                    true
                ) as ParseResult<ParsingPackage>).result

            CustomPackageInfo(result)
        }
        Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 -> {
            CustomPackageInfo(PackageParser().parsePackage(packageFile, flags, useCaches))
        }
        else -> {
            CustomPackageInfo(PackageParser().parsePackage(packageFile, flags))
        }
    }
}
