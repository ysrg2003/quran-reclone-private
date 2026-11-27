package com.example.quranoffline.data

/** v24.2: lightweight on-device timing of the per-recognizer-update path (main thread). In memory; exported in the Log report. */
object TasmeePerf {
    private class Stat { var n = 0L; var totalNs = 0L; var maxNs = 0L; var over50 = 0L; var over100 = 0L }
    private val partial = Stat()
    private val stable = Stat()
    private var droppedPartials = 0L
    private val slow = ArrayDeque<Triple<Long, Boolean, Long>>() // wall ms, stable, ms

    @Synchronized fun record(isStable: Boolean, ns: Long) {
        val s = if (isStable) stable else partial
        s.n++; s.totalNs += ns; if (ns > s.maxNs) s.maxNs = ns
        val ms = ns / 1_000_000
        if (ms >= 50) s.over50++
        if (ms >= 100) s.over100++
        if (ms >= 50) { slow.addLast(Triple(System.currentTimeMillis(), isStable, ms)); while (slow.size > 30) slow.removeFirst() }
    }

    @Synchronized fun droppedPartial() { droppedPartials++ }

    @Synchronized fun table(): ExportTable {
        fun row(name: String, s: Stat): List<Any> = listOf(name, s.n, if (s.n == 0L) 0 else (s.totalNs / s.n / 1000), s.maxNs / 1000, s.over50, s.over100)
        val rows = mutableListOf<List<Any>>(row("جزئي", partial), row("نهائي", stable), listOf("جزئيات أُسقطت بالتجميع", droppedPartials, 0, 0, 0, 0))
        slow.forEach { rows += listOf("بطيء (" + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(it.first)) + ")", 1, it.third * 1000, it.third * 1000, 0, 0) }
        return ExportTable("أداء التسميع (ميكروثانية)", listOf("النوع", "العدد", "المتوسط", "الأقصى", "فوق 50ms", "فوق 100ms"), rows)
    }
}
