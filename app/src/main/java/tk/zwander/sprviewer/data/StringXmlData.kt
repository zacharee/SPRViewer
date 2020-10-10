package tk.zwander.sprviewer.data

import android.os.Parcel
import android.os.Parcelable
import kotlinx.android.parcel.Parceler
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.TypeParceler
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap

@Parcelize
@TypeParceler<MutableMap<String, String>, MapParceler>
data class StringXmlData(
    val locale: Locale,
    val values: MutableSet<StringData> = TreeSet<StringData>()
) : Parcelable, BaseData() {
    override fun constructLabel(): String {
        return "${locale.toString().run { if (this.isBlank()) "DEFAULT" else this }}/strings.xml"
    }

    fun asXmlString(): String {
        val builder = StringBuilder()

        builder.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        builder.appendLine("<resources>")
        values.forEach { (t, u) ->
            builder.appendLine("    <string mame=\"$t\">$u</string>")
        }
        builder.appendLine("</resources>")

        return builder.toString()
    }
}

object MapParceler : Parceler<MutableMap<String, String>> {
    override fun MutableMap<String, String>.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(size)

        forEach { (k, v) ->
            parcel.writeString(k)
            parcel.writeString(v)
        }
    }

    override fun create(parcel: Parcel): MutableMap<String, String> {
        val size = parcel.readInt()

        return HashMap<String, String>().apply {
            for (i in 0 until size) {
                val key = parcel.readString()
                val value = parcel.readString()

                this[key] = value
            }
        }
    }
}