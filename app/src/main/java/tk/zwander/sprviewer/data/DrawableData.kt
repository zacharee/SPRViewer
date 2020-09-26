package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.util.getFile
import java.io.File

@Parcelize
data class DrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int,
    val file: File
) : Parcelable

data class UDrawableData(
    val type: String,
    val name: String,
    val ext: String?,
    val path: String,
    val id: Int,
    val file: ApkFile
) {
    fun toDrawableData(): DrawableData {
        return DrawableData(
            type, name, ext, path, id,
            file.getFile()
        )
    }
}