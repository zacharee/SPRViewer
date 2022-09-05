package tk.zwander.sprviewer.util

import android.annotation.SuppressLint
import android.content.pm.PackageManager.*
import android.content.pm.PackageParser
import android.content.pm.parsing.result.ParseInput
import android.content.pm.parsing.result.ParseTypeImpl
import android.content.pm.pkg.FrameworkPackageUserState
import android.os.Build
import android.permission.PermissionManager
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.data.CustomPackageInfo
import java.io.File
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

@SuppressLint("PrivateApi")
@Suppress("UNCHECKED_CAST")
fun parsePackageCompat(
    packageFile: File,
    packageName: String,
    flags: Int,
    useCaches: Boolean
): CustomPackageInfo {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            try {
                @Suppress("DEPRECATION")
                val parser = PackageParser()
                var flagsBits = flags
                if (flagsBits and (MATCH_DIRECT_BOOT_UNAWARE or MATCH_DIRECT_BOOT_AWARE) != 0) {
                    // Caller expressed an explicit opinion about what encryption
                    // aware/unaware components they want to see, so fall through and
                    // give them what they want
                } else {
                    // Caller expressed no opinion, so match everything
                    flagsBits = flagsBits or (MATCH_DIRECT_BOOT_AWARE or MATCH_DIRECT_BOOT_UNAWARE)
                }
                val pkg = parser.parsePackage(packageFile, 0, false)
                @Suppress("DEPRECATION")
                if (flagsBits and GET_SIGNATURES != 0) {
                    PackageParser.collectCertificates(pkg, false /* skipVerify */)
                }
                @Suppress("DEPRECATION")
                val info = PackageParser.generatePackageInfo(
                    pkg, null, flagsBits, 0, 0, null,
                    FrameworkPackageUserState.DEFAULT
                )
                CustomPackageInfo(info)
            } catch (e: Exception) {
                CustomPackageInfo(packageName)
            }
        }
        Build.VERSION.SDK_INT > Build.VERSION_CODES.R && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
            val parser = ParseTypeImpl.forParsingWithoutPlatformCompat()

            val result = Class.forName("android.content.pm.parsing.ParsingPackageUtils")
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
                ).run {
                    this::class.java.getDeclaredMethod("getResult")
                        .invoke(this)
                }

            CustomPackageInfo(result!!)
        }
        Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 -> {
            @Suppress("DEPRECATION")
            CustomPackageInfo(PackageParser().parsePackage(packageFile, flags, useCaches))
        }
        else -> {
            @Suppress("DEPRECATION")
            CustomPackageInfo(PackageParser().parsePackage(packageFile, flags))
        }
    }
}
