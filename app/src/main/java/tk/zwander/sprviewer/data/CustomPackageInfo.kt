@file:Suppress("DEPRECATION")

package tk.zwander.sprviewer.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageParser

data class CustomPackageInfo(
    val packageName: String,
    val appInfo: ApplicationInfo? = null
) {
    constructor(pkg: PackageParser.Package) : this(pkg.packageName, pkg.applicationInfo)
    constructor(info: PackageInfo) : this(info.packageName, info.applicationInfo)
    constructor(result: Any) : this(
        result::class.java.getMethod("getPackageName").invoke(result)!!.toString(),
        result::class.java.getMethod("toAppInfoWithoutState").invoke(result) as ApplicationInfo
    )
}