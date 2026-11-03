package br.com.copiloto.gestor

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exporta os números do período em JSON para o painel visual (dashboard):
 * dias.json (um dia por linha), apps.json (receita por dia e app) e perfil.json (dados do carro).
 */
object Exportar {
    private val rotulo = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.of("pt", "BR"))

    fun paraPasta(perfil: PerfilVeiculo, p: ResumoPeriodo, pasta: File) {
        pasta.mkdirs()
        File(pasta, "dias.json").writeText(dias(p))
        File(pasta, "apps.json").writeText(apps(p))
        File(pasta, "perfil.json").writeText(perfil(perfil, p))
        File(pasta, "trechos.json").writeText(trechos(p))
    }

    fun dias(p: ResumoPeriodo): String = lista(p.dias.map { d ->
        linkedMapOf(
            "data" to d.data.toString(),
            "dia" to rotulo.format(d.data).replace(".", ""),
            "parcial" to d.parcial,
            "km" to d.kmRodado,
            "horas" to d.horasTrabalhadas,
            "corridas" to d.corridas,
            "receita" to d.receitaBruta,
            "combustivel" to d.custoCombustivel,
            "despesas_dia" to d.despesasOperacionais,
            "aluguel" to d.custoFixoRateado,
            "custo_real" to d.custoTotal,
            "lucro_real" to d.lucroReal,
            "lucro_caixa" to d.lucroCaixa,
            "pago_combustivel" to (d.despesasPorCategoria[Categoria.COMBUSTIVEL] ?: 0.0),
            "ganho_km" to d.ganhoPorKm,
            "ganho_hora" to d.ganhoPorHora,
            "lucro_hora" to d.lucroPorHora,
        )
    })

    fun apps(p: ResumoPeriodo): String = lista(p.dias.flatMap { d ->
        d.receitaPorApp.map { (app, valor) -> linkedMapOf<String, Any?>("data" to d.data.toString(), "app" to app, "receita" to valor) }
    })

    fun trechos(p: ResumoPeriodo): String = lista(p.consumoReal?.trechos.orEmpty().map { t ->
        linkedMapOf<String, Any?>(
            "trecho" to "${t.kmInicio}-${t.kmFim}",
            "km_inicio" to t.kmInicio, "km_fim" to t.kmFim,
            "combustivel" to (t.combustivel?.lowercase() ?: "—"),
            "km" to t.km, "litros" to t.litros, "km_por_litro" to t.kmPorLitro,
            "preco_litro" to t.precoLitro, "custo" to t.custo, "custo_km" to t.custoPorKm,
        )
    })

    fun perfil(perfil: PerfilVeiculo, p: ResumoPeriodo): String = lista(listOf(linkedMapOf(
        "carro" to perfil.nome,
        "aluguel_mensal" to perfil.custoFixoMensal,
        "aluguel_dia" to perfil.custoFixoPorDia,
        "dias_mes" to perfil.diasTrabalhadosPorMes,
        "meta_receita_dia" to perfil.metaReceitaDiaria,
        // Combustível que está no tanque agora (o consumo medido de cada trecho vai em trechos.json)
        "combustivel_atual" to (p.combustivelAtual?.lowercase() ?: "—"),
        "preco_litro" to perfil.precoCombustivel,
        "consumo_km_l" to perfil.consumoKmPorLitro,
        "consumo_medido" to false,
        "custo_comb_km" to p.custoCombustivelPorKmUsado,
        "atualizado_ate" to p.fim.toString(),
    )))

    private fun lista(linhas: List<Map<String, Any?>>): String =
        linhas.joinToString(",\n  ", "[\n  ", "\n]\n") { l ->
            l.entries.joinToString(", ", "{", "}") { (k, v) -> "\"$k\": ${valor(v)}" }
        }

    private fun valor(v: Any?): String = when (v) {
        null -> "null"
        is Boolean, is Int -> v.toString()
        is Double -> if (v.isNaN() || v.isInfinite()) "null" else "%.2f".format(Locale.ROOT, v)
        is LocalDate -> "\"$v\""
        else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
