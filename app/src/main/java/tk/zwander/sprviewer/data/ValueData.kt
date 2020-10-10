package tk.zwander.sprviewer.data

import android.content.pm.PackageParser
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.util.parsePackageCompat
import java.io.File
import java.util.*

@Parcelize
data class LocalizedValueData(
    val locale: Locale,
    val value: String?
) : Parcelable

@Parcelize
data class ValueData(
    val type: String,
    val name: String,
    val path: String,
    val id: Int,
    val defaultValue: String?,
    val values: MutableList<LocalizedValueData>
) : Parcelable {
    fun toUValueData(): UValueData {
        return UValueData(
            type, name, path, id,
            ApkFile(path),
            PackageParser().run {
                parsePackageCompat(File(path), 0, true)
            },
            defaultValue,
            values
        )
    }
}

data class UValueData(
    val type: String,
    val name: String,
    val path: String,
    val id: Int,
    val file: ApkFile,
    val packageInfo: PackageParser.Package,
    val defaultValue: String?,
    val values: MutableList<LocalizedValueData>
) : BaseData() {
    fun toDrawableData(): ValueData {
        return ValueData(
            type, name, path, id,
            defaultValue,
            values
        )
    }

    override fun constructLabel(): String {
        return name
    }

    override fun equals(other: Any?): Boolean {
        return other is UValueData
                && name == other.name
                && defaultValue == other.defaultValue
    }

    override fun hashCode(): Int {
        return Objects.hash(name, defaultValue)
    }
}