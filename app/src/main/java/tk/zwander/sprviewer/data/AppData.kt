package tk.zwander.sprviewer.data

import android.graphics.drawable.Drawable
import java.util.*

data class AppData(
    val pkg: String,
    val label: String,
    val icon: Drawable,
    var expanded: Boolean = false
) : BaseData(), Comparable<AppData> {
    override fun compareTo(other: AppData): Int {
        return pkg.compareTo(other.pkg)
    }

    override fun constructLabel(): String {
        return label
    }

    override fun equals(other: Any?): Boolean {
        return other is AppData
                && pkg == other.pkg
                && label == other.label
    }

    override fun hashCode(): Int {
        return Objects.hash(pkg, label)
    }
}