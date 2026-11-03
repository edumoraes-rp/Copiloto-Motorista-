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

data class ConsumoReal(
    val kmPorLitro: Double,
    val precoMedioLitro: Double,
    val kmMedidos: Int,
    val litrosMedidos: Double,
) {
    val custoPorKm: Double get() = precoMedioLitro / kmPorLitro
}

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
    ): ResumoDia {
        val km = jornada.kmRodado
        val horas = jornada.minutosTrabalhados?.let { it / 60.0 }
        val receita = jornada.receitas.sumOf { it.valor }
        val corridas = jornada.receitas.sumOf { it.corridas }
        val porApp = jornada.receitas.groupBy { it.app }.mapValues { (_, r) -> r.sumOf { it.valor } }
        val porCategoria = jornada.despesas.groupBy { it.categoria }.mapValues { (_, d) -> d.sumOf { it.valor } }
        val pagas = jornada.despesas.sumOf { it.valor }

        val kmConta = (km ?: 0).coerceAtLeast(0)
        val combustivel = kmConta * custoCombustivelPorKm
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

    /**
     * Consumo real pelo método do tanque cheio: entre dois abastecimentos de tanque cheio,
     * km rodados ÷ litros colocados depois do primeiro (incluindo o segundo).
     */
    fun consumoReal(abastecimentos: List<Despesa>): ConsumoReal? {
        val validos = abastecimentos
            .filter { it.categoria == Categoria.COMBUSTIVEL && it.litros != null && (it.litros > 0 || it.tanqueCheio) && it.kmHodometro != null }
            .sortedBy { it.kmHodometro }
        var ultimoCheio: Despesa? = null
        var litrosDesdeCheio = 0.0
        var kmTotal = 0
        var litrosTotal = 0.0
        for (a in validos) {
            if (ultimoCheio != null) litrosDesdeCheio += a.litros!!
            if (a.tanqueCheio) {
                if (ultimoCheio != null) {
                    kmTotal += a.kmHodometro!! - ultimoCheio.kmHodometro!!
                    litrosTotal += litrosDesdeCheio
                }
                ultimoCheio = a
                litrosDesdeCheio = 0.0
            }
        }
        if (litrosTotal <= 0 || kmTotal <= 0) return null
        val precoMedio = validos.sumOf { it.valor } / validos.sumOf { it.litros!! }
        return ConsumoReal(kmTotal / litrosTotal, precoMedio, kmTotal, litrosTotal)
    }

    fun resumoDoPeriodo(jornadas: List<Jornada>): ResumoPeriodo {
        require(jornadas.isNotEmpty()) { "Nenhuma jornada no período" }
        val ordenadas = jornadas.sortedBy { it.data }
        val referencias = ordenadas.flatMap { j ->
            j.tanqueCheioNoKm.map { km -> Despesa(Categoria.COMBUSTIVEL, 0.0, "tanque cheio de referência", litros = 0.0, kmHodometro = km, tanqueCheio = true) }
        }
        val consumo = consumoReal(ordenadas.flatMap { it.abastecimentos } + referencias)
        val custoCombKm = consumo?.custoPorKm ?: perfil.custoCombustivelPorKm
        val dias = ordenadas.map { resumoDoDia(it, custoCombKm) }

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
            else if (kotlin.math.abs(consumo.kmPorLitro - perfil.consumoKmPorLitro) / perfil.consumoKmPorLitro > 0.10)
                add("Consumo real (${Formato.num(consumo.kmPorLitro)} km/L) está mais de 10% diferente do cadastrado (${Formato.num(perfil.consumoKmPorLitro)} km/L): atualize o perfil.")
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
