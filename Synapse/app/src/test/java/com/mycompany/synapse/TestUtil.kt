package com.mycompany.synapse

import com.mycompany.synapse.game.logic.MINUS
import org.junit.Assert.assertEquals

/** Minimaler Auswerter für Aufgabentexte mit + − × ÷, Klammern, ² und „p % von x“. */
fun eval(expr: String): Long {
    val e = expr.replace(MINUS, "-").replace("×", "*").replace("÷", "/").replace(" ", "")
    if (e.contains("%von")) {
        val (p, b) = e.split("%von")
        return p.toLong() * b.toLong() / 100
    }
    var pos = 0
    fun parseExpr(): Long {
        fun factor(): Long {
            if (e[pos] == '(') {
                pos++
                val v = parseExpr()
                pos++ // ')'
                return v
            }
            val start = pos
            while (pos < e.length && e[pos].isDigit()) pos++
            var v = e.substring(start, pos).toLong()
            if (pos < e.length && e[pos] == '²') { pos++; v *= v }
            return v
        }
        fun term(): Long {
            var v = factor()
            while (pos < e.length && (e[pos] == '*' || e[pos] == '/')) {
                val op = e[pos++]
                val r = factor()
                v = if (op == '*') v * r else { assertEquals("Nicht glatt teilbar: $expr", 0, v % r); v / r }
            }
            return v
        }
        var v = term()
        while (pos < e.length && (e[pos] == '+' || e[pos] == '-')) {
            val op = e[pos++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }
    return parseExpr()
}
