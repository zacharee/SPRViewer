package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class StringData(
    val key: String,
    val value: String
) : Parcelable, Comparable<StringData>, BaseData() {
    override fun constructLabel(): String {
        return key
    }

    override fun compareTo(other: StringData): Int {
        val k = key.compareTo(other.key)
        val v = value.compareTo(other.value)

        return if (k == 0) v else k
    }
}