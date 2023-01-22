package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.struct.resource.ResTableConfig

@Parcelize
data class DrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int,
    val resTableConfig: ResTableConfigParcelable
) : Parcelable

data class UDrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int,
    val file: ApkFile,
    val packageInfo: CustomPackageInfo,
    val resTableConfig: ResTableConfig
) : BaseData() {
    fun toDrawableData(): DrawableData {
        return DrawableData(
            type, name, ext, path, id,
            ResTableConfigParcelable(resTableConfig)
        )
    }

    override fun constructLabel(): String {
        return "$name.$ext"
    }
}