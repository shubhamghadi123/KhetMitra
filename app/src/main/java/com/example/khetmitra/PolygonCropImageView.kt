package com.example.khetmitra

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class PolygonCropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val clipPath = Path()
    private var isMaskReady = false

    fun setFarmMask(
        expandedBox: List<List<Double>>,
        exactFarmPoints: List<List<Double>>
    ) {
        post {
            clipPath.reset()
            val minLon = expandedBox.minOf { it[0] }
            val maxLon = expandedBox.maxOf { it[0] }
            val minLat = expandedBox.minOf { it[1] }
            val maxLat = expandedBox.maxOf { it[1] }

            val imgWidth = width.toFloat()
            val imgHeight = height.toFloat()

            for ((index, point) in exactFarmPoints.withIndex()) {
                val lon = point[0]
                val lat = point[1]

                val xPercent = ((lon - minLon) / (maxLon - minLon)).toFloat()
                val yPercent = (1f - ((lat - minLat) / (maxLat - minLat))).toFloat()

                val pixelX = xPercent * imgWidth
                val pixelY = yPercent * imgHeight

                if (index == 0) {
                    clipPath.moveTo(pixelX, pixelY)
                } else {
                    clipPath.lineTo(pixelX, pixelY)
                }
            }
            clipPath.close()
            isMaskReady = true
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (isMaskReady) {
            canvas.clipPath(clipPath)
        }
        super.onDraw(canvas)
    }
}