package com.example.quranoffline

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.quranoffline.data.ExportTable
import java.io.OutputStream

/** PDF through the platform PdfDocument: no library. Arabic shaping and RTL come from the system text engine. Needs a phone to verify. */
object ReportPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 28

    fun write(title: String, tables: List<ExportTable>, out: OutputStream) {
        val doc = PdfDocument()
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF000000.toInt() }
        val bold = TextPaint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val head = TextPaint(bold).apply { textSize = 14f }
        val line = Paint().apply { color = 0xFF999999.toInt(); strokeWidth = 0.5f }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M.toFloat()
        }
        fun layout(s: String, p: TextPaint, width: Int) =
            StaticLayout.Builder.obtain(s, 0, s.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(android.text.TextDirectionHeuristics.RTL).build()
        fun block(s: String, p: TextPaint) {
            val l = layout(s, p, W - 2 * M)
            if (y + l.height > H - M) newPage()
            val c = page!!.canvas
            c.save(); c.translate(M.toFloat(), y); l.draw(c); c.restore(); y += l.height + 6
        }
        newPage()
        block(title, head)
        for (t in tables) {
            block(t.title, bold.also { it.textSize = 12f })
            bold.textSize = 9f
            val n = t.headers.size.coerceAtLeast(1)
            val cw = (W - 2 * M) / n
            fun row(cells: List<String>, p: TextPaint) {
                val ls = cells.map { layout(it, p, cw - 6) }
                val h = (ls.maxOfOrNull { it.height } ?: 10) + 4
                if (y + h > H - M) newPage()
                val c = page!!.canvas
                // RTL table: first column at the right edge.
                ls.forEachIndexed { i, l ->
                    c.save(); c.translate((W - M - (i + 1) * cw + 3).toFloat(), y + 2); l.draw(c); c.restore()
                }
                c.drawLine(M.toFloat(), y + h, (W - M).toFloat(), y + h, line)
                y += h
            }
            row(t.headers, bold)
            if (t.rows.isEmpty()) block("لا توجد بيانات", text)
            t.rows.forEach { r -> row(r.map { it.toString() }, text) }
            y += 12
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }
}
