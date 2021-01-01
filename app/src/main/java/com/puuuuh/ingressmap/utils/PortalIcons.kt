package com.puuuuh.ingressmap.utils

import android.graphics.*
import androidx.core.graphics.createBitmap
import com.puuuuh.ingressmap.MainApplication

class PortalIcons {
    companion object {
        fun createIcon(color: Int, centerColor: Int, markColor: Int): Bitmap {
            val len = (MainApplication.applicationContext().resources.displayMetrics.density * 20);
            val elSize = (len / 6)
            val bmp = createBitmap(len.toInt(),len.toInt());
            val canvas = Canvas(bmp)

            val strokePaint = Paint()
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = elSize
            strokePaint.color = color

            val markPaint = Paint()
            markPaint.style = Paint.Style.STROKE
            markPaint.strokeWidth = elSize
            markPaint.color = markColor

            val centerPaint = Paint()
            centerPaint.style = Paint.Style.FILL
            centerPaint.color = centerColor

            val oval = RectF(elSize, elSize, (len-elSize),(len-elSize))

            canvas.drawOval(oval, strokePaint)
            for (i in 0..3) {
                canvas.drawArc(oval, i * 120f, 60f, false, markPaint)
            }

            canvas.drawOval((len - elSize) / 2, (len - elSize) / 2, (len + elSize) / 2, (len + elSize) / 2, centerPaint)
            return bmp
        }
    }
}
