package com.alaska.dailytracker

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One drawable element of a glyph: a path drawn as stroke (default) or fill. */
private data class El(val d: String, val fill: Boolean = false, val width: Float? = null)

/**
 * Glyph definitions, recreated 1:1 from the prototype's inline SVGs.
 * Each glyph lives in a 24x24 viewport, white stroke ~2, round caps/joins.
 * SVG <circle> elements are expressed here as equivalent arc paths.
 */
private val GLYPHS: Map<String, Pair<Float, List<El>>> = mapOf(
    "moon" to (2f to listOf(
        El("M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
    )),
    "sunrise" to (2f to listOf(
        El("M12 2v3M4.9 7.9l1.4 1.4M2 15h2M20 15h2M17.7 9.3l1.4-1.4M22 18H2M16 18a4 4 0 0 0-8 0")
    )),
    "check" to (2.4f to listOf(
        El("M20 6 9 17l-5-5")
    )),
    "run" to (2f to listOf(
        // head: circle cx=14.5 cy=4.5 r=1.8  ->  filled arc path
        El("M12.7 4.5a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0z", fill = true),
        El("M8.5 21l2.2-4.5 2.3-2 .8 3.2 3.2 2.3M5.5 12.2l3-2.4 3.2-.8 2.4 2.6 2.6.9M9.5 15.5l-1 2.2")
    )),
    "dumbbell" to (2f to listOf(
        El("M2.5 9v6M5 7.5v9M19 7.5v9M21.5 9v6M5 12h14")
    )),
    "sun" to (2f to listOf(
        // circle cx=12 cy=12 r=4  ->  stroked arc path
        El("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z"),
        El("M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4")
    )),
    "snowflake" to (2f to listOf(
        El("M12 2v20M2 12h20M5 5l14 14M19 5 5 19M12 5l-2.5 2M12 5l2.5 2M12 19l-2.5-2M12 19l2.5-2M5 12l2-2.5M5 12l2 2.5M19 12l-2-2.5M19 12l-2 2.5")
    )),
    "fork" to (2f to listOf(
        El("M6 2v7a2.5 2.5 0 0 0 5 0V2M8.5 2v6M18 2c-1.5 0-2.5 1.6-2.5 4.5S16.5 11 18 11m0-9v20")
    )),
)

/** The "+" glyph used by the add-task row. */
private val PLUS = 2.2f to listOf(El("M12 5v14M5 12h14"))

/** Icon order in the picker == ICON_META key order in the prototype. */
val ICON_ORDER = listOf("moon", "sunrise", "check", "run", "dumbbell", "sun", "snowflake", "fork")

/** Circle background color per icon. dumbbell uses a gradient (see [iconBrush]). */
private val ICON_COLOR: Map<String, Color> = mapOf(
    "moon" to Color(0xFF3B82F6),
    "sunrise" to Color(0xFFF59E0B),
    "check" to Color(0xFF34C759),
    "run" to Color(0xFF14B8A6),
    "dumbbell" to Color(0xFF9AA0A6),
    "sun" to Color(0xFFFF3B30),
    "snowflake" to Color(0xFF5AC8FA),
    "fork" to Color(0xFFA3CD3A),
)
private val DUMBBELL_GRAD = listOf(Color(0xFFB9BCC2), Color(0xFF7E8288))

private val vectorCache = HashMap<String, ImageVector>()

fun glyphVector(key: String): ImageVector = vectorCache.getOrPut(key) {
    val (baseWidth, els) = GLYPHS[key] ?: GLYPHS["moon"]!!
    buildVector(baseWidth, els)
}

fun plusVector(): ImageVector = vectorCache.getOrPut("__plus") {
    buildVector(PLUS.first, PLUS.second)
}

private fun buildVector(baseWidth: Float, els: List<El>): ImageVector {
    val b = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    )
    els.forEach { e ->
        val nodes = PathParser().parsePathString(e.d).toNodes()
        if (e.fill) {
            b.addPath(pathData = nodes, fill = SolidColor(Color.White))
        } else {
            b.addPath(
                pathData = nodes,
                stroke = SolidColor(Color.White),
                strokeLineWidth = e.width ?: baseWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return b.build()
}

/** Background brush for an icon circle (handles the dumbbell gradient). */
fun iconBrush(key: String): Brush =
    if (key == "dumbbell") Brush.linearGradient(DUMBBELL_GRAD)
    else SolidColor(ICON_COLOR[key] ?: Color(0xFF7E8288))

/** Approximates CSS `filter: grayscale(1) brightness(0.55)` for the completed state. */
fun doneIconColor(key: String): Color {
    val c = ICON_COLOR[key] ?: Color(0xFF9AA0A6)
    val lum = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    val v = lum * 0.55f
    return Color(v, v, v, 1f)
}

/** White glyph dimmed by brightness 0.55 when completed. */
val DONE_GLYPH_TINT = Color(0xFF8C8C8C)
