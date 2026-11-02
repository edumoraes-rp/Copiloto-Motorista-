package br.com.copiloto.gestor

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação no padrão brasileiro (R$ 1.234,56; 02/11/2026). */
object Formato {
    private val simbolos = DecimalFormatSymbols(Locale.of("pt", "BR"))
    private val dinheiro = DecimalFormat("#,##0.00", simbolos)
    private val numero = DecimalFormat("#,##0.##", simbolos)
    private val dataBr = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val diaSemana = DateTimeFormatter.ofPattern("EEE", Locale.of("pt", "BR"))

    fun reais(v: Double?): String = if (v == null) "—" else "R$ " + dinheiro.format(v)
    fun num(v: Double?): String = if (v == null) "—" else numero.format(v)
    fun pct(v: Double?): String = if (v == null) "—" else numero.format(v * 100) + "%"
    fun data(d: LocalDate): String = dataBr.format(d)
    fun dataComDia(d: LocalDate): String = "${diaSemana.format(d).removeSuffix(".")} ${dataBr.format(d)}"

    fun horas(h: Double?): String {
        if (h == null) return "—"
        val minutos = Math.round(h * 60)
        return "%dh%02d".format(minutos / 60, minutos % 60)
    }

    /** Lê número no padrão brasileiro ("1.234,56" ou "6,19") ou com ponto ("6.19"). */
    fun lerNumero(texto: String): Double {
        val t = texto.trim().removePrefix("R$").trim()
        val normalizado = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalizado.toDoubleOrNull()
            ?: throw IllegalArgumentException("Número inválido: '$texto'")
    }
}
