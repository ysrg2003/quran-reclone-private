package com.example.quranoffline

import com.example.quranoffline.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class LogExportTest {
    private val t = ExportTable("الأخطاء", listOf("أ", "ب"), listOf(listOf("x<y & \"z\"", 5), listOf("\u0001ok", 7)))
    private fun entries(b: ByteArray): Map<String, String> {
        val m = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(b)).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        return m
    }
    @Test fun xlsxHasPartsAndEscapes() {
        val o = ByteArrayOutputStream(); LogExport.writeXlsx(listOf(t, t.copy(title = "الأخطاء")), o)
        val e = entries(o.toByteArray())
        assertTrue(e.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        assertTrue(e["xl/worksheets/sheet1.xml"]!!.contains("x&lt;y &amp; &quot;z&quot;"))
        assertTrue(e["xl/worksheets/sheet1.xml"]!!.contains("<v>5</v>"))
        assertTrue(!e["xl/worksheets/sheet1.xml"]!!.contains("\u0001"))
        assertTrue(e["xl/workbook.xml"]!!.contains("الأخطاء_2"))
    }
    @Test fun docxHasRtlTable() {
        val o = ByteArrayOutputStream(); LogExport.writeDocx("عنوان", listOf(t), o)
        val d = entries(o.toByteArray())["word/document.xml"]!!
        assertTrue(d.contains("<w:bidiVisual/>")); assertTrue(d.contains("عنوان")); assertEquals(3, Regex("<w:tr>").findAll(d).count())
    }
    @Test fun emptyTableDocxStillHasRow() {
        val o = ByteArrayOutputStream(); LogExport.writeDocx("t", listOf(ExportTable("a", listOf("x"), emptyList())), o)
        assertTrue(entries(o.toByteArray())["word/document.xml"]!!.contains("لا توجد بيانات"))
    }
    @Test fun columnNames() { /* via xlsx of 27 columns */
        val o = ByteArrayOutputStream(); LogExport.writeXlsx(listOf(ExportTable("w", (1..28).map { "h$it" }, emptyList())), o)
        val s = entries(o.toByteArray())["xl/worksheets/sheet1.xml"]!!
        assertTrue(s.contains("r=\"Z1\"")); assertTrue(s.contains("r=\"AA1\"")); assertTrue(s.contains("r=\"AB1\""))
    }
}
