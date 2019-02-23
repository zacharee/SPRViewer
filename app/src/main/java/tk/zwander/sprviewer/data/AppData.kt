package tk.zwander.sprviewer.data

import android.graphics.drawable.Drawable

data class AppData(
    val pkg: String,
    val label: String,
    val icon: Drawable
) : Comparable<AppData> {
    override fun compareTo(other: AppData): Int {
        return pkg.compareTo(other.pkg)
    }
}