package com.aintradutor.app

object Csv {

    fun parse(textIn: String): List<List<String>> {
        val text = textIn.removePrefix("\uFEFF")
        val firstLine = text.substringBefore('\n')
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    sb.append(c)
                }
            } else if (c == '"') {
                inQuotes = true
            } else if (c == delim) {
                row.add(sb.toString())
                sb.setLength(0)
            } else if (c == '\n') {
                row.add(sb.toString())
                sb.setLength(0)
                rows.add(row)
                row = ArrayList()
            } else if (c != '\r') {
                sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString())
            rows.add(row)
        }
        return rows
    }

    fun escape(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
