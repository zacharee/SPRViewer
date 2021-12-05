package tk.zwander.sprviewer.util

import android.app.LoadedApk
import android.app.ResourcesManager
import android.content.res.CompatibilityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Display
import net.dongliu.apk.parser.struct.resource.ResourcePackage
import net.dongliu.apk.parser.struct.resource.ResourceTable

@Suppress("UNCHECKED_CAST")
val ResourceTable.packageMap: Map<Short, ResourcePackage>
    get() = ResourceTable::class.java
        .getDeclaredField("packageMap")
        .apply { isAccessible = true }
        .get(this) as Map<Short, ResourcePackage>

fun ResourcesManager.getResourcesCompat(apkPath: String, pkgInfo: LoadedApk): Resources {
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
        Build.VERSION.SDK_INT < 31 -> {
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
                    null,
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
        else -> {
            ResourcesManager::class.java
                .getDeclaredMethod(
                    "getResources",
                    IBinder::class.java,
                    String::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    java.lang.Integer::class.java,
                    Configuration::class.java,
                    CompatibilityInfo::class.java,
                    ClassLoader::class.java,
                    List::class.java
                )
                .apply { isAccessible = true }
                .invoke(
                    this,
                    null,
                    apkPath,
                    null,
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