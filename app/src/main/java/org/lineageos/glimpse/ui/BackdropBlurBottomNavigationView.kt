/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.lineageos.glimpse.R
import kotlin.math.ceil

/**
 * A lightweight translucent navigation dock with an Android 12+ backdrop blur.
 *
 * Only the small area occupied by the dock is sampled, at a reduced resolution,
 * so the effect is considerably cheaper than blurring the complete gallery.
 * The snapshot is refreshed explicitly after page/scroll changes instead of
 * continuously on every frame.
 */
class BackdropBlurBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BottomNavigationView(context, attrs, defStyleAttr) {

    private val snapshotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipRect = RectF()
    private val clipPath = android.graphics.Path()
    private val blurNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        RenderNode("GlimpseDockBlur")
    } else {
        null
    }

    private var sourceView: View? = null
    private var snapshot: Bitmap? = null
    private var blurRadius = 30f
    private var snapshotScale = 0.35f

    init {
        // The blurred backdrop is rendered by this class; do not let the
        // Material component paint its normal opaque surface over it.
        background = null
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    fun setBackdropSource(view: View?) {
        sourceView = view
        refreshBackdrop()
    }

    fun refreshBackdrop() {
        if (!isLaidOut || width <= 0 || height <= 0 || sourceView == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            invalidate()
            return
        }

        val source = sourceView ?: return
        val scale = snapshotScale
        val bitmapWidth = ceil(width * scale).toInt().coerceAtLeast(1)
        val bitmapHeight = ceil(height * scale).toInt().coerceAtLeast(1)

        val bitmap = snapshot?.takeIf {
            !it.isRecycled && it.width == bitmapWidth && it.height == bitmapHeight
        } ?: Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888).also {
            snapshot?.recycle()
            snapshot = it
        }

        val sourceLocation = IntArray(2)
        val dockLocation = IntArray(2)
        source.getLocationInWindow(sourceLocation)
        getLocationInWindow(dockLocation)

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(
            -(dockLocation[0] - sourceLocation[0]).toFloat(),
            -(dockLocation[1] - sourceLocation[1]).toFloat(),
        )
        source.draw(canvas)
        canvas.restore()

        blurNode?.let { node ->
            node.setPosition(0, 0, width, height)
            val nodeCanvas = node.beginRecording(width, height)
            nodeCanvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), snapshotPaint)
            node.endRecording()
            node.setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurNode != null) {
            clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
            val radius = resources.getDimension(R.dimen.glimpse_dock_corner_radius)
            canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(clipRect, radius, radius, android.graphics.Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawRenderNode(blurNode)

            // A subtle translucent surface tint keeps icons/text readable while
            // retaining the colors and shapes of the media behind the dock.
            overlayPaint.color = resolveSurfaceTint()
            canvas.drawRect(clipRect, overlayPaint)
            canvas.restore()
        }

        super.onDraw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { refreshBackdrop() }
    }

    override fun onDetachedFromWindow() {
        snapshot?.recycle()
        snapshot = null
        super.onDetachedFromWindow()
    }

    private fun resolveSurfaceTint(): Int {
        val surface = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
        )
        return (surface and 0x00ffffff) or (0x58 shl 24)
    }
}
