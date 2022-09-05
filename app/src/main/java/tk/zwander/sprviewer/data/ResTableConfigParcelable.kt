package tk.zwander.sprviewer.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import net.dongliu.apk.parser.struct.resource.ResTableConfig

@Parcelize
data class ResTableConfigParcelable(
    val size: Int,
    val mcc: Short,
    val mnc: Short,
    val language: String,
    val country: String,
    val orientation: Short,
    val touchscreen: Short,
    val density: Int,
    val navigation: Short,
    val inputFlags: Short,
    val screenWidth: Int,
    val screenHeight: Int,
    val sdkVersion: Int,
    val minorVersion: Int,
    val screenLayout: Short,
    val uiMode: Short,
    val screenConfigPad1: Short,
    val screenConfigPad2: Short
) : Parcelable {
    constructor(config: ResTableConfig) : this(
        config.size,
        config.mcc,
        config.mnc,
        config.language,
        config.country,
        config.orientation,
        config.touchscreen,
        config.density,
        config.navigation,
        config.inputFlags,
        config.screenWidth,
        config.screenHeight,
        config.sdkVersion,
        config.minorVersion,
        config.screenLayout,
        config.uiMode,
        config.screenConfigPad1,
        config.screenConfigPad2
    )

    fun constructPrefix(): String {
        val builder = ArrayList<String>()

        if (mcc != 0.toShort()) {
            builder.add("mcc$mcc")

            if (mnc != 0.toShort()) {
                builder.add("mnc$mnc")
            }
        }

        if (language.isNotBlank() && language != "0") {
            builder.add(language)

            if (country.isNotBlank() && country != "0") {
                builder.add("r$country")
            }
        }

        if (screenWidth != 0) {
            builder.add("w${screenWidth}dp")
        }

        if (screenHeight != 0) {
            builder.add("h${screenHeight}dp")
        }

        if (orientation != 0.toShort()) {
            builder.add(if (orientation == 1.toShort()) "port" else "land")
        }

        if (uiMode != 0.toShort()) {
            builder.add(
                when (uiMode.toInt()) {
                    1 -> "normal"
                    2 -> "desk"
                    3 -> "car"
                    4 -> "television"
                    5 -> "appliance"
                    6 -> "watch"
                    else -> "vrheadset"
                }
            )
        }

        if (density != 0) {
            builder.add(
                when (density) {
                    120 -> "ldpi"
                    160 -> "mdpi"
                    213 -> "tvdpi"
                    240 -> "hdpi"
                    320 -> "xhdpi"
                    480 -> "xxhdpi"
                    640 -> "xxxhdpi"
                    else -> "${density}dpi"
                }
            )
        }

        if (touchscreen != 0.toShort()) {
            builder.add(
                when (touchscreen.toInt()) {
                    1 -> "notouch"
                    2 -> "stylus"
                    else -> "finger"
                }
            )
        }

        if (sdkVersion != 0) {
            builder.add("v$sdkVersion")
        }

        return builder.joinToString("-")
    }
}