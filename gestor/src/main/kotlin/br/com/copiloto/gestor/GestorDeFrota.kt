package br.com.copiloto.gestor

import java.time.LocalDate

// ---------------------------------------------------------------------------
// Resultados
// ---------------------------------------------------------------------------

data class ResumoDia(
    val data: LocalDate,
    val parcial: Boolean = false,
    val kmRodado: Int?,
    val horasTrabalhadas: Double?,
    val corridas: Int,
    val receitaBruta: Double,
    val receitaPorApp: Map<String, Double>,
    val despesasPorCategoria: Map<Categoria, Double>,
    /** Tudo o que saiu do bolso no dia, inclusive abastecimento e pagamento de aluguel. */
    val despesasPagas: Double,
    /** Receita menos tudo o que foi pago no dia: o dinheiro que sobrou na mão. */
    val lucroCaixa: Double,
    // --- visão de gestor (custo real do dia) ---
    val custoCombustivel: Double,
    val despesasOperacionais: Double,
    val reservaManutencao: Double,
    val custoFixoRateado: Double,
    val custoTotal: Double,
    /** Receita menos o custo real do dia: o que de fato se ganhou trabalhando. */
    val lucroReal: Double,
    val ganhoPorKm: Double?,
    val ganhoPorHora: Double?,
    val custoPorKm: Double?,
    val lucroPorKm: Double?,
    val lucroPorHora: Double?,
    val ticketMedio: Double?,
    /** Lucro real ÷ receita bruta (0,35 = 35% da receita vira lucro). */
    val margem: Double?,
    val alertas: List<String>,
)

/**
 * Trecho entre dois tanques cheios: os litros colocados no fim repõem o que foi queimado,
 * e o que foi queimado é o combustível colocado no início (preço e tipo do tanque de partida).
 */
data class TrechoConsumo(
    val kmInicio: Int,
    val kmFim: Int,
    val litros: Double,
    val precoLitro: Double,
    val combustivel: String?,
) {
    val km: Int get() = kmFim - kmInicio
    val kmPorLitro: Double get() = km / litros
    val custo: Double get() = litros * precoLitro
    val custoPorKm: Double get() = custo / km
}

data class ConsumoReal(val trechos: List<TrechoConsumo>) {
    val kmMedidos: Int get() = trechos.sumOf { it.km }
    val litrosMedidos: Double get() = trechos.sumOf { it.litros }
    val kmPorLitro: Double get() = kmMedidos / litrosMedidos
    val precoMedioLitro: Double get() = trechos.sumOf { it.custo } / litrosMedidos
    val custoPorKm: Double get() = trechos.sumOf { it.custo } / kmMedidos
}

/** Um tanque cheio conhecido (pago no período ou de referência). */
data class PontoTanqueCheio(val km: Int, val precoLitro: Double?, val combustivel: String?)

data class ResumoPeriodo(
    val inicio: LocalDate,
    val fim: LocalDate,
    val dias: List<ResumoDia>,
    val diasTrabalhados: Int,
    val kmRodado: Int,
    val horasTrabalhadas: Double,
    val corridas: Int,
    val receitaBruta: Double,
    val receitaPorApp: Map<String, Double>,
    val despesasPorCategoria: Map<Categoria, Double>,
    val despesasPagas: Double,
    val lucroCaixa: Double,
    val custoCombustivel: Double,
    val despesasOperacionais: Double,
    val reservaManutencao: Double,
    val custoFixoRateado: Double,
    val custoTotal: Double,
    val lucroReal: Double,
    // médias
    val receitaMediaPorDia: Double,
    val lucroRealMedioPorDia: Double,
    val kmMedioPorDia: Double,
    val horasMediasPorDia: Double,
    val ganhoPorKm: Double?,
    val ganhoPorHora: Double?,
    val custoPorKm: Double?,
    val lucroPorKm: Double?,
    val lucroPorHora: Double?,
    val ticketMedio: Double?,
    val margem: Double?,
    val melhorDia: ResumoDia?,
    val piorDia: ResumoDia?,
    // combustível
    val consumoReal: ConsumoReal?,
    /** Custo de combustível por km usado nas contas (real, se medido; senão o do perfil). */
    val custoCombustivelPorKmUsado: Double,
    // conciliações
    /** Reserva de manutenção acumulada menos o que foi gasto em manutenção. */
    val saldoReservaManutencao: Double,
    /** Combustível consumido (estimado) menos o pago na bomba: positivo = tanque está "devendo". */
    val diferencaCombustivel: Double,
    /** Km entre o fim de um dia e o início do seguinte (uso pessoal ou esquecimento). */
    val kmForaDoTrabalho: Int,
    val custoKmForaDoTrabalho: Double,
    // planejamento
    /** Receita por dia necessária para não ter prejuízo. */
    val pontoEquilibrioDia: Double,
    /** Receita por dia necessária para bater a meta de lucro mensal. */
    val receitaNecessariaParaMetaDia: Double?,
    /** Custo total por km: abaixo disso a corrida dá prejuízo. */
    val valorMinimoPorKm: Double?,
    val projecaoLucroMensal: Double,
    /** Dias em que a receita bruta bateu a meta diária (null = sem meta). */
    val diasNaMetaDeReceita: Int?,
    /** Lucro real por dia se a meta de receita for batida, com o custo médio dos dias do período. */
    val lucroRealNaMetaDia: Double?,
    val projecaoReceitaMensal: Double,
    val alertas: List<String>,
)

// ---------------------------------------------------------------------------
// Motor de cálculo
// ---------------------------------------------------------------------------

class GestorDeFrota(private val perfil: PerfilVeiculo) {

    fun resumoDoDia(
        jornada: Jornada,
        custoCombustivelPorKm: Double = perfil.custoCombustivelPorKm,
        /** Custo de combustível já calculado trecho a trecho (usado pelo resumo do período). */
        combustivelCalculado: Double? = null,
    ): ResumoDia {
        val km = jornada.kmRodado
        val horas = jornada.minutosTrabalhados?.let { it / 60.0 }
        val receita = jornada.receitas.sumOf { it.valor }
        val corridas = jornada.receitas.sumOf { it.corridas }
        val porApp = jornada.receitas.groupBy { it.app }.mapValues { (_, r) -> r.sumOf { it.valor } }
        val porCategoria = jornada.despesas.groupBy { it.categoria }.mapValues { (_, d) -> d.sumOf { it.valor } }
        val pagas = jornada.despesas.sumOf { it.valor }

        val kmConta = (km ?: 0).coerceAtLeast(0)
        val combustivel = combustivelCalculado ?: (kmConta * custoCombustivelPorKm)
        val operacionais = jornada.despesas.filter { it.categoria.natureza == Natureza.OPERACIONAL }.sumOf { it.valor }
        val manutencao = kmConta * perfil.manutencaoPorKm
        val fixo = perfil.custoFixoPorDia
        val custoTotal = combustivel + operacionais + manutencao + fixo
        val lucroReal = receita - custoTotal

        val alertas = buildList {
            if (jornada.kmFinal == null) add("Falta o km final do dia.")
            if (km != null && km < 0) add("Km final (${jornada.kmFinal}) menor que o inicial (${jornada.kmInicial}).")
            if (km != null && km > 700) add("Km rodado muito alto ($km km): confira o hodômetro.")
            if (jornada.minutosTrabalhados == null) add("Falta a hora de início ou de fim: sem ganho por hora.")
            if (receita == 0.0) add("Nenhuma receita lançada.")
            if (receita > 0 && lucroReal < 0) add("Dia com prejuízo real: a receita não cobriu os custos.")
            jornada.abastecimentos.filter { it.litros == null || it.kmHodometro == null }.takeIf { it.isNotEmpty() }?.let {
                add("Abastecimento sem litros ou sem km do hodômetro: não entra na medição do consumo real.")
            }
        }

        return ResumoDia(
            data = jornada.data,
            parcial = jornada.parcial,
            kmRodado = km,
            horasTrabalhadas = horas,
            corridas = corridas,
            receitaBruta = receita,
            receitaPorApp = porApp,
            despesasPorCategoria = porCategoria,
            despesasPagas = pagas,
            lucroCaixa = receita - pagas,
            custoCombustivel = combustivel,
            despesasOperacionais = operacionais,
            reservaManutencao = manutencao,
            custoFixoRateado = fixo,
            custoTotal = custoTotal,
            lucroReal = lucroReal,
            ganhoPorKm = divide(receita, km),
            ganhoPorHora = divide(receita, horas),
            custoPorKm = divide(custoTotal, km),
            lucroPorKm = divide(lucroReal, km),
            lucroPorHora = divide(lucroReal, horas),
            ticketMedio = if (corridas > 0) receita / corridas else null,
            margem = if (receita > 0) lucroReal / receita else null,
            alertas = alertas,
        )
    }

    private class Ponto(val km: Int, val litros: Double, val cheio: Boolean, val preco: Double?, val combustivel: String?)

    private fun pontos(abastecimentos: List<Despesa>, referencias: List<TanqueCheio>): List<Ponto> =
        (abastecimentos
            .filter { it.categoria == Categoria.COMBUSTIVEL && it.litros != null && it.litros > 0 && it.kmHodometro != null }
            .map { Ponto(it.kmHodometro!!, it.litros!!, it.tanqueCheio, it.valor / it.litros, it.combustivel) } +
            referencias.map { Ponto(it.km, 0.0, true, it.precoLitro, it.combustivel) })
            .sortedBy { it.km }

    /**
     * Consumo real pelo método do tanque cheio, trecho a trecho: entre dois tanques cheios,
     * km rodados ÷ litros colocados depois do primeiro (incluindo o segundo).
     * O custo do trecho usa o preço do combustível do tanque de partida (o que foi queimado).
     */
    fun consumoReal(abastecimentos: List<Despesa>, referencias: List<TanqueCheio> = emptyList()): ConsumoReal? {
        val trechos = mutableListOf<TrechoConsumo>()
        var ultimo: Ponto? = null
        var litros = 0.0
        for (p in pontos(abastecimentos, referencias)) {
            if (ultimo != null) litros += p.litros
            if (p.cheio) {
                if (ultimo != null && litros > 0 && p.km > ultimo.km) {
                    val preco = ultimo.preco ?: p.preco ?: perfil.precoCombustivel
                    trechos += TrechoConsumo(ultimo.km, p.km, litros, preco, ultimo.combustivel ?: p.combustivel)
                }
                ultimo = p
                litros = 0.0
            }
        }
        return if (trechos.isEmpty()) null else ConsumoReal(trechos)
    }

    /** Último tanque cheio conhecido: define o combustível que está no tanque agora. */
    fun ultimoTanqueCheio(abastecimentos: List<Despesa>, referencias: List<TanqueCheio> = emptyList()): PontoTanqueCheio? =
        pontos(abastecimentos, referencias).lastOrNull { it.cheio }?.let { PontoTanqueCheio(it.km, it.preco, it.combustivel) }

    /**
     * Custo do combustível de um intervalo de km: km dentro de trechos medidos pagam o custo real do trecho;
     * km depois do último tanque cheio usam o preço desse tanque e o consumo do perfil; o resto usa o perfil.
     */
    fun custoCombustivel(kmInicio: Int, kmFim: Int, consumo: ConsumoReal?, ultimo: PontoTanqueCheio?): Double {
        if (kmFim <= kmInicio) return 0.0
        var custo = 0.0
        var coberto = 0
        consumo?.trechos?.forEach { t ->
            val sobreposto = (minOf(kmFim, t.kmFim) - maxOf(kmInicio, t.kmInicio)).coerceAtLeast(0)
            custo += sobreposto * t.custoPorKm
            coberto += sobreposto
        }
        val fimMedido = consumo?.trechos?.maxOf { it.kmFim }
        val depois = if (ultimo?.precoLitro != null) (kmFim - maxOf(kmInicio, ultimo.km, fimMedido ?: ultimo.km)).coerceAtLeast(0) else 0
        custo += depois * (ultimo?.precoLitro ?: 0.0) / perfil.consumoKmPorLitro
        custo += (kmFim - kmInicio - coberto - depois).coerceAtLeast(0) * perfil.custoCombustivelPorKm
        return custo
    }

    fun resumoDoPeriodo(jornadas: List<Jornada>): ResumoPeriodo {
        require(jornadas.isNotEmpty()) { "Nenhuma jornada no período" }
        val ordenadas = jornadas.sortedBy { it.data }
        val referencias = ordenadas.flatMap { it.tanqueCheioNoKm }
        val abastecimentos = ordenadas.flatMap { it.abastecimentos }
        val consumo = consumoReal(abastecimentos, referencias)
        val ultimoCheio = ultimoTanqueCheio(abastecimentos, referencias)
        val dias = ordenadas.map { j ->
            val fim = j.kmFinal
            resumoDoDia(j, combustivelCalculado = if (fim != null) custoCombustivel(j.kmInicial, fim, consumo, ultimoCheio) else null)
        }
        val kmComFim = dias.sumOf { (it.kmRodado ?: 0).coerceAtLeast(0) }
        val custoCombKm = if (kmComFim > 0) dias.sumOf { it.custoCombustivel } / kmComFim else perfil.custoCombustivelPorKm

        val n = dias.size
        val km = dias.sumOf { (it.kmRodado ?: 0).coerceAtLeast(0) }
        val diasComHora = dias.filter { it.horasTrabalhadas != null }
        val horas = diasComHora.sumOf { it.horasTrabalhadas!! }
        val receita = dias.sumOf { it.receitaBruta }
        val corridas = dias.sumOf { it.corridas }
        val pagas = dias.sumOf { it.despesasPagas }
        val combustivel = dias.sumOf { it.custoCombustivel }
        val operacionais = dias.sumOf { it.despesasOperacionais }
        val manutencao = dias.sumOf { it.reservaManutencao }
        val fixo = dias.sumOf { it.custoFixoRateado }
        val custoTotal = dias.sumOf { it.custoTotal }
        val lucroReal = receita - custoTotal

        val porApp = somaMapas(dias.map { it.receitaPorApp })
        val porCategoria = somaMapas(dias.map { it.despesasPorCategoria })
        val manutencaoPaga = porCategoria[Categoria.MANUTENCAO] ?: 0.0
        val combustivelPago = porCategoria[Categoria.COMBUSTIVEL] ?: 0.0

        // Km entre o fim de um dia e o início do próximo.
        val kmFora = ordenadas.zipWithNext().sumOf { (a, b) ->
            val fimAnterior = a.kmFinal ?: return@sumOf 0
            (b.kmInicial - fimAnterior).coerceAtLeast(0)
        }
        val custoKmVariavel = custoCombKm + perfil.manutencaoPorKm

        // Ponto de equilíbrio com o dia médio do período.
        val kmMedio = km.toDouble() / n
        val operacionaisMedias = operacionais / n
        val equilibrio = perfil.custoFixoPorDia + operacionaisMedias + kmMedio * custoKmVariavel
        val paraMeta = if (perfil.metaLucroMensal > 0) equilibrio + perfil.metaLucroMensal / perfil.diasTrabalhadosPorMes else null
        val lucroMedio = lucroReal / n
        val metaReceita = perfil.metaReceitaDiaria

        val alertas = buildList {
            dias.filter { it.alertas.isNotEmpty() }.forEach { d ->
                d.alertas.forEach { add("${Formato.data(d.data)}: $it") }
            }
            if (kmFora > 0) add("$kmFora km rodados fora do expediente (entre um dia e outro): custo de ${Formato.reais(kmFora * custoKmVariavel)} que não é do trabalho.")
            if (consumo == null) add("Consumo real ainda não medido: anote litros, km do hodômetro e 'CHEIO' em pelo menos dois abastecimentos de tanque cheio.")
            if (metaReceita > 0 && receita / n < metaReceita)
                add("Receita média de ${Formato.reais(receita / n)}/dia está abaixo da meta de ${Formato.reais(metaReceita)}/dia (faltam ${Formato.reais(metaReceita - receita / n)}).")
            if (paraMeta != null && receita / n < paraMeta)
                add("No ritmo atual a meta de ${Formato.reais(perfil.metaLucroMensal)}/mês não é atingida: faltam ${Formato.reais(paraMeta - receita / n)} de receita por dia.")
        }

        return ResumoPeriodo(
            inicio = ordenadas.first().data,
            fim = ordenadas.last().data,
            dias = dias,
            diasTrabalhados = n,
            kmRodado = km,
            horasTrabalhadas = horas,
            corridas = corridas,
            receitaBruta = receita,
            receitaPorApp = porApp,
            despesasPorCategoria = porCategoria,
            despesasPagas = pagas,
            lucroCaixa = receita - pagas,
            custoCombustivel = combustivel,
            despesasOperacionais = operacionais,
            reservaManutencao = manutencao,
            custoFixoRateado = fixo,
            custoTotal = custoTotal,
            lucroReal = lucroReal,
            receitaMediaPorDia = receita / n,
            lucroRealMedioPorDia = lucroMedio,
            kmMedioPorDia = kmMedio,
            horasMediasPorDia = if (diasComHora.isNotEmpty()) horas / diasComHora.size else 0.0,
            ganhoPorKm = divide(receita, km),
            ganhoPorHora = divide(diasComHora.sumOf { it.receitaBruta }, horas),
            custoPorKm = divide(custoTotal, km),
            lucroPorKm = divide(lucroReal, km),
            lucroPorHora = divide(diasComHora.sumOf { it.lucroReal }, horas),
            ticketMedio = if (corridas > 0) dias.filter { it.corridas > 0 }.sumOf { it.receitaBruta } / corridas else null,
            margem = if (receita > 0) lucroReal / receita else null,
            melhorDia = dias.maxByOrNull { it.lucroReal },
            piorDia = dias.minByOrNull { it.lucroReal },
            consumoReal = consumo,
            custoCombustivelPorKmUsado = custoCombKm,
            saldoReservaManutencao = manutencao - manutencaoPaga,
            diferencaCombustivel = combustivel - combustivelPago,
            kmForaDoTrabalho = kmFora,
            custoKmForaDoTrabalho = kmFora * custoKmVariavel,
            pontoEquilibrioDia = equilibrio,
            receitaNecessariaParaMetaDia = paraMeta,
            valorMinimoPorKm = if (kmMedio > 0) equilibrio / kmMedio else null,
            projecaoLucroMensal = lucroMedio * perfil.diasTrabalhadosPorMes,
            diasNaMetaDeReceita = if (metaReceita > 0) dias.count { it.receitaBruta >= metaReceita } else null,
            lucroRealNaMetaDia = if (metaReceita > 0) metaReceita - custoTotal / n else null,
            projecaoReceitaMensal = receita / n * perfil.diasTrabalhadosPorMes,
            alertas = alertas,
        )
    }

    private fun divide(valor: Double, por: Number?): Double? {
        val d = por?.toDouble() ?: return null
        return if (d > 0) valor / d else null
    }

    private fun <K> somaMapas(mapas: List<Map<K, Double>>): Map<K, Double> =
        mapas.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.sum() }
}
