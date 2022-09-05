package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import net.dongliu.apk.parser.ApkFile

@Parcelize
data class DrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int
) : Parcelable {
//    fun toUDrawableData(): UDrawableData {
//        return UDrawableData(
//            type, name, ext, path, id,
//            ApkFile(path),
//            parsePackageCompat(File(path), 0, true)
//        )
//    }
}

data class UDrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int,
    val file: ApkFile,
    val packageInfo: CustomPackageInfo
) : BaseData() {
    fun toDrawableData(): DrawableData {
        return DrawableData(
            type, name, ext, path, id
        )
    }

    override fun constructLabel(): String {
        return "$name.$ext"
    }
}