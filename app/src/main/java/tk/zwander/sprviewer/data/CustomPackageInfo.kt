package tk.zwander.sprviewer.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageParser
import android.content.pm.parsing.ParsingPackage

data class CustomPackageInfo(
    val packageName: String,
    val applicationInfo: ApplicationInfo
) {
    constructor(pkg: PackageParser.Package) : this(pkg.packageName, pkg.applicationInfo)
    constructor(result: ParsingPackage) : this(result.packageName, result.toAppInfoWithoutState())
}