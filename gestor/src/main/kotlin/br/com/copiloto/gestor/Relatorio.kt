package br.com.copiloto.gestor

import java.io.File
import kotlin.system.exitProcess

/**
 * Gera o relatório do gestor de frota em Markdown.
 *
 * Uso (na raiz do projeto):
 *   gradle :gestor:run                                   -> lê diario/perfil.txt e diario/lancamentos.csv
 *   gradle :gestor:run --args="diario/exemplo"           -> lê outra pasta
 *   gradle :gestor:run --args="diario relatorio.md"      -> também salva em arquivo
 */
fun main(args: Array<String>) {
    val pasta = File(args.getOrElse(0) { "diario" })
    try {
        val perfil = Leitor.lerPerfil(File(pasta, "perfil.txt").readText())
        val jornadas = Leitor.lerLancamentos(File(pasta, "lancamentos.csv").readText())
        if (jornadas.isEmpty()) {
            println("Nenhum lançamento em ${pasta.path}/lancamentos.csv ainda.")
            return
        }
        val resumo = GestorDeFrota(perfil).resumoDoPeriodo(jornadas)
        val texto = Relatorio.gerar(perfil, resumo)
        Exportar.paraPasta(perfil, resumo, File(pasta, "painel"))
        println(texto)
        args.getOrNull(1)?.let { File(it).writeText(texto); println("\nRelatório salvo em $it") }
    } catch (e: Exception) {
        System.err.println("Erro: ${e.message}")
        exitProcess(1)
    }
}

object Relatorio {
    fun gerar(perfil: PerfilVeiculo, p: ResumoPeriodo): String = buildString {
        val f = Formato
        appendLine("# Gestor de frota: ${perfil.nome}")
        appendLine()
        appendLine("Período: **${f.data(p.inicio)} a ${f.data(p.fim)}**, ${p.diasTrabalhados} dia(s) trabalhado(s)")
        appendLine()

        appendLine("## Resultado do período")
        appendLine()
        appendLine("| | Valor |")
        appendLine("|---|---:|")
        appendLine("| Receita bruta | ${f.reais(p.receitaBruta)} |")
        appendLine("| Custo real (combustível + operacionais + manutenção + fixos) | ${f.reais(p.custoTotal)} |")
        appendLine("| **Lucro real** | **${f.reais(p.lucroReal)}** |")
        appendLine("| Margem (lucro ÷ receita) | ${f.pct(p.margem)} |")
        appendLine("| Lucro de caixa (receita − tudo que foi pago) | ${f.reais(p.lucroCaixa)} |")
        appendLine()
        appendLine("| Indicador | Valor |")
        appendLine("|---|---:|")
        appendLine("| Km rodados | ${p.kmRodado} km (média ${f.num(p.kmMedioPorDia)} km/dia) |")
        appendLine("| Horas trabalhadas | " + (if (p.horasTrabalhadas > 0) "${f.horas(p.horasTrabalhadas)} (média ${f.horas(p.horasMediasPorDia)}/dia)" else "— (informe hora de início e fim)") + " |")
        appendLine("| Corridas | ${p.corridas} (ticket médio ${f.reais(p.ticketMedio)}) |")
        appendLine("| Ganho por km | ${f.reais(p.ganhoPorKm)} |")
        appendLine("| Custo por km | ${f.reais(p.custoPorKm)} |")
        appendLine("| Lucro por km | ${f.reais(p.lucroPorKm)} |")
        appendLine("| Ganho por hora | ${f.reais(p.ganhoPorHora)} |")
        appendLine("| Lucro por hora | ${f.reais(p.lucroPorHora)} |")
        appendLine("| Receita média por dia | ${f.reais(p.receitaMediaPorDia)} |")
        appendLine("| Lucro real médio por dia | ${f.reais(p.lucroRealMedioPorDia)} |")
        p.melhorDia?.let { appendLine("| Melhor dia | ${f.dataComDia(it.data)}: ${f.reais(it.lucroReal)} |") }
        p.piorDia?.let { appendLine("| Pior dia | ${f.dataComDia(it.data)}: ${f.reais(it.lucroReal)} |") }
        appendLine()

        appendLine("## Receita por app")
        appendLine()
        appendLine("| App | Receita | % |")
        appendLine("|---|---:|---:|")
        p.receitaPorApp.entries.sortedByDescending { it.value }.forEach { (app, v) ->
            appendLine("| $app | ${f.reais(v)} | ${f.pct(if (p.receitaBruta > 0) v / p.receitaBruta else null)} |")
        }
        appendLine()

        appendLine("## Para onde foi o dinheiro (custo real)")
        appendLine()
        appendLine("| Custo | Valor | Por km |")
        appendLine("|---|---:|---:|")
        fun linhaCusto(nome: String, v: Double) =
            appendLine("| $nome | ${f.reais(v)} | ${f.reais(if (p.kmRodado > 0) v / p.kmRodado else null)} |")
        linhaCusto("Combustível consumido (${f.reais(p.custoCombustivelPorKmUsado)}/km)", p.custoCombustivel)
        linhaCusto("Despesas do dia a dia", p.despesasOperacionais)
        if (perfil.manutencaoPorKm > 0) linhaCusto("Reserva de manutenção (${f.reais(perfil.manutencaoPorKm)}/km)", p.reservaManutencao)
        linhaCusto("Custos fixos rateados (${f.reais(perfil.custoFixoPorDia)}/dia)", p.custoFixoRateado)
        appendLine()
        if (p.despesasPorCategoria.isNotEmpty()) {
            appendLine("Pagamentos lançados (o que saiu do bolso):")
            appendLine()
            appendLine("| Categoria | Pago |")
            appendLine("|---|---:|")
            p.despesasPorCategoria.entries.sortedByDescending { it.value }.forEach { (c, v) ->
                appendLine("| ${c.rotulo} | ${f.reais(v)} |")
            }
            appendLine()
        }

        appendLine(if (perfil.manutencaoPorKm > 0) "## Combustível e manutenção" else "## Combustível")
        appendLine()
        val c = p.consumoReal
        if (c != null) {
            appendLine("Consumo medido entre tanques cheios (cada trecho paga o combustível que estava no tanque):")
            appendLine()
            appendLine("| Trecho (km) | Combustível | Km | Litros | Km/L | R$/L | Custo | R$/km |")
            appendLine("|---|---|---:|---:|---:|---:|---:|---:|")
            c.trechos.forEach { t ->
                appendLine("| ${f.num(t.kmInicio.toDouble())} → ${f.num(t.kmFim.toDouble())} | ${t.combustivel?.lowercase() ?: "—"} | ${t.km} | ${f.num(t.litros)} | ${f.num(t.kmPorLitro)} | ${f.reais(t.precoLitro)} | ${f.reais(t.custo)} | ${f.reais(t.custoPorKm)} |")
            }
            appendLine()
            appendLine("- Km depois do último tanque cheio usam ${f.num(p.consumoAtualKmL)} km/L (${if (p.consumoAtualMedido) "último consumo medido com ${p.combustivelAtual?.lowercase()}" else "estimativa do perfil"}) até a próxima medição")
        } else {
            appendLine("- Consumo real ainda não medido; usando o cadastrado: ${f.num(perfil.consumoKmPorLitro)} km/L a ${f.reais(perfil.precoCombustivel)}/L (${f.reais(perfil.custoCombustivelPorKm)}/km)")
        }
        val dif = p.diferencaCombustivel
        appendLine("- Combustível consumido ${f.reais(p.custoCombustivel)} × pago na bomba ${f.reais(p.despesasPorCategoria[Categoria.COMBUSTIVEL] ?: 0.0)}: " +
            if (dif >= 0) "o tanque gastou ${f.reais(dif)} a mais do que foi reposto." else "sobrou ${f.reais(-dif)} de combustível no tanque para os próximos dias.")
        if (perfil.manutencaoPorKm > 0) appendLine("- Reserva de manutenção: acumulou ${f.reais(p.reservaManutencao)}, saldo ${f.reais(p.saldoReservaManutencao)} (separe esse valor; ele paga óleo, pneus e freios).")
        if (p.kmForaDoTrabalho > 0) appendLine("- Km fora do expediente: ${p.kmForaDoTrabalho} km (custo ${f.reais(p.custoKmForaDoTrabalho)}), não entram no lucro do trabalho.")
        appendLine()

        appendLine("## Planejamento")
        appendLine()
        appendLine("- Custos fixos: ${f.reais(perfil.custoFixoMensal)}/mês = ${f.reais(perfil.custoFixoPorDia)} por dia trabalhado (${perfil.diasTrabalhadosPorMes} dias/mês)")
        appendLine("- **Ponto de equilíbrio:** faturar pelo menos **${f.reais(p.pontoEquilibrioDia)} por dia** (rodando ~${f.num(p.kmMedioPorDia)} km) para não ter prejuízo")
        appendLine("- **Valor mínimo por km:** ${f.reais(p.valorMinimoPorKm)}. Abaixo disso, na média, o dia dá prejuízo")
        p.receitaNecessariaParaMetaDia?.let {
            appendLine("- Meta de lucro ${f.reais(perfil.metaLucroMensal)}/mês: faturar **${f.reais(it)} por dia** (${f.reais(if (p.kmMedioPorDia > 0) it / p.kmMedioPorDia else null)}/km)")
        }
        if (perfil.metaReceitaDiaria > 0) {
            appendLine("- **Meta de receita ${f.reais(perfil.metaReceitaDiaria)}/dia:** média atual ${f.reais(p.receitaMediaPorDia)} (${f.pct(p.receitaMediaPorDia / perfil.metaReceitaDiaria)} da meta); ${p.diasNaMetaDeReceita} de ${p.diasTrabalhados} dia(s) bateram a meta")
            appendLine("- Batendo a meta todo dia, com o custo médio atual: lucro real de ${f.reais(p.lucroRealNaMetaDia)}/dia, **${f.reais(p.lucroRealNaMetaDia!! * perfil.diasTrabalhadosPorMes)}/mês**")
        }
        appendLine("- Projeção do mês no ritmo atual: receita ${f.reais(p.projecaoReceitaMensal)}, lucro real **${f.reais(p.projecaoLucroMensal)}**")
        appendLine()

        appendLine("## Dia a dia")
        appendLine()
        appendLine("| Dia | Km | Horas | Corridas | Receita | Custo real | Lucro real | R$/km | Lucro/hora | Caixa |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        p.dias.forEach { d ->
            appendLine("| ${f.dataComDia(d.data)}${if (d.parcial) " *(parcial)*" else ""} | ${d.kmRodado ?: "—"} | ${f.horas(d.horasTrabalhadas)} | ${d.corridas} | ${f.reais(d.receitaBruta)} | ${f.reais(d.custoTotal)} | ${f.reais(d.lucroReal)} | ${f.reais(d.ganhoPorKm)} | ${f.reais(d.lucroPorHora)} | ${f.reais(d.lucroCaixa)} |")
        }
        appendLine()

        if (p.alertas.isNotEmpty()) {
            appendLine("## Alertas")
            appendLine()
            p.alertas.forEach { appendLine("- $it") }
            appendLine()
        }
    }
}
