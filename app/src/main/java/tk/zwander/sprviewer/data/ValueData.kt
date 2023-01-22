package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.dongliu.apk.parser.ApkFile
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
) : Parcelable

data class UValueData(
    val type: String,
    val name: String,
    val path: String,
    val id: Int,
    val file: ApkFile,
    val packageInfo: CustomPackageInfo,
    val defaultValue: String?,
    val values: MutableList<LocalizedValueData>
) : BaseData() {
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