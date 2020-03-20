package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class DrawableData(
    val name: String,
    val ext: String?,
    val id: Int
) : Parcelable