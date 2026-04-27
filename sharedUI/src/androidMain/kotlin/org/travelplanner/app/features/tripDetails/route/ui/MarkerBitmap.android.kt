package org.travelplanner.app.features.tripDetails.route.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.max

internal data class MarkerBitmap(
    val bitmap: Bitmap,
    val anchorX: Float,
    val anchorY: Float,
)

internal fun buildLabeledMarkerBitmap(
    context: Context,
    title: String,
    fillColor: Int,
    radiusDp: Int,
    strokeDp: Int,
): MarkerBitmap {
    val density = context.resources.displayMetrics.density
    val radiusPx = radiusDp * density
    val strokePx = strokeDp * density
    val shadowOffsetPx = 3f * density
    val shadowBlurPx = 2f * density
    val labelGapPx = 4f * density
    val horizontalPaddingPx = 8f * density
    val bottomPaddingPx = 4f * density

    val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF212121.toInt()
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = false
        }
    val haloPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = textPaint.textSize
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

    val safeTitle = title.ifBlank { " " }
    val titleBounds = Rect()
    textPaint.getTextBounds(safeTitle, 0, safeTitle.length, titleBounds)

    val markerDiameterPx = (radiusPx + strokePx) * 2
    val width =
        ceil(
            max(
                markerDiameterPx,
                titleBounds.width().toFloat() + horizontalPaddingPx * 2,
            ),
        ).toInt()
    val height =
        ceil(
            markerDiameterPx + shadowOffsetPx + shadowBlurPx +
                labelGapPx + titleBounds.height() + bottomPaddingPx,
        ).toInt()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = width / 2f
    val cy = radiusPx + strokePx

    val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    canvas.drawCircle(cx, cy + shadowOffsetPx, radiusPx, shadowPaint)

    val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        }
    canvas.drawCircle(cx, cy, radiusPx + strokePx, strokePaint)

    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
        }
    canvas.drawCircle(cx, cy, radiusPx, fillPaint)

    val baselineY = cy + radiusPx + strokePx + labelGapPx + titleBounds.height()
    canvas.drawText(safeTitle, cx, baselineY, haloPaint)
    canvas.drawText(safeTitle, cx, baselineY, textPaint)

    val anchorX = 0.5f
    val anchorY = cy / height.toFloat()
    return MarkerBitmap(bmp, anchorX, anchorY)
}

internal fun buildSimpleCircleBitmap(
    context: Context,
    fillColor: Int,
    radiusDp: Int,
    strokeDp: Int,
): MarkerBitmap {
    val density = context.resources.displayMetrics.density
    val radiusPx = radiusDp * density
    val strokePx = strokeDp * density
    val shadowOffsetPx = 3f * density
    val shadowBlurPx = 2f * density

    val markerDiameterPx = (radiusPx + strokePx) * 2
    val width = ceil(markerDiameterPx).toInt()
    val height = ceil(markerDiameterPx + shadowOffsetPx + shadowBlurPx).toInt()

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val cx = width / 2f
    val cy = radiusPx + strokePx

    val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55000000
            maskFilter = BlurMaskFilter(shadowBlurPx, BlurMaskFilter.Blur.NORMAL)
        }
    canvas.drawCircle(cx, cy + shadowOffsetPx, radiusPx, shadowPaint)

    val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
        }
    canvas.drawCircle(cx, cy, radiusPx + strokePx, strokePaint)

    val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
        }
    canvas.drawCircle(cx, cy, radiusPx, fillPaint)

    return MarkerBitmap(bmp, 0.5f, cy / height.toFloat())
}
