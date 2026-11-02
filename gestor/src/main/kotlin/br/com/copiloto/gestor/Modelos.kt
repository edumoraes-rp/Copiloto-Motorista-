package br.com.copiloto.gestor

import java.time.LocalDate
import java.time.LocalTime

// ---------------------------------------------------------------------------
// Perfil do veículo e custos (o "cadastro de custos" do app)
// ---------------------------------------------------------------------------

enum class Posse { QUITADO, FINANCIADO, ALUGADO }

/** Um custo que existe todo mês, trabalhando ou não (aluguel, parcela, seguro, IPVA...). */
data class CustoFixo(val descricao: String, val valorMensal: Double) {
    companion object {
        /** Aluguel semanal (comum em locadoras para motorista): 52 semanas / 12 meses. */
        fun semanal(descricao: String, valorSemanal: Double) =
            CustoFixo(descricao, valorSemanal * 52.0 / 12.0)

        /** Custo anual (IPVA, licenciamento, seguro pago à vista) dividido em 12. */
        fun anual(descricao: String, valorAnual: Double) =
            CustoFixo(descricao, valorAnual / 12.0)

        /** Diária de aluguel vezes os dias de aluguel no mês. */
        fun diaria(descricao: String, valorDiaria: Double, diasNoMes: Int) =
            CustoFixo(descricao, valorDiaria * diasNoMes)
    }
}

data class PerfilVeiculo(
    val nome: String,
    val posse: Posse,
    /** Preço médio do litro (ou m³ de GNV) que o motorista costuma pagar. */
    val precoCombustivel: Double,
    /** Consumo médio em km por litro. Será substituído pelo consumo real quando houver abastecimentos de tanque cheio. */
    val consumoKmPorLitro: Double,
    val custosFixos: List<CustoFixo> = emptyList(),
    /** Reserva de manutenção por km rodado (óleo, pneus, freios, suspensão, revisões). */
    val manutencaoPorKm: Double = 0.0,
    /** Dias trabalhados por mês, usado para ratear os custos fixos. */
    val diasTrabalhadosPorMes: Int = 26,
    /** Quanto o motorista quer de lucro real no mês (0 = sem meta). */
    val metaLucroMensal: Double = 0.0,
    /** Meta de receita bruta por dia trabalhado (0 = sem meta). */
    val metaReceitaDiaria: Double = 0.0,
) {
    init {
        require(precoCombustivel >= 0) { "Preço do combustível não pode ser negativo" }
        require(consumoKmPorLitro > 0) { "Consumo (km/L) precisa ser maior que zero" }
        require(diasTrabalhadosPorMes in 1..31) { "Dias trabalhados por mês deve estar entre 1 e 31" }
    }

    val custoFixoMensal: Double get() = custosFixos.sumOf { it.valorMensal }
    val custoFixoPorDia: Double get() = custoFixoMensal / diasTrabalhadosPorMes
    val custoCombustivelPorKm: Double get() = precoCombustivel / consumoKmPorLitro
}

// ---------------------------------------------------------------------------
// Diário de bordo: um dia de trabalho
// ---------------------------------------------------------------------------

/**
 * Como cada despesa entra no "lucro real":
 * - COMBUSTIVEL: o que se paga na bomba não é o que se gasta no dia (o tanque sobra ou falta);
 *   no lucro real usamos km rodado × custo do combustível por km.
 * - OPERACIONAL: gasto do próprio dia (alimentação, lavagem, estacionamento, pedágio, multa).
 * - MANUTENCAO: sai da reserva de manutenção por km; no lucro real entra a reserva, não o pagamento.
 * - FIXA: aluguel, parcela, seguro... já entram rateados por dia; o pagamento não é custo do dia.
 */
enum class Natureza { COMBUSTIVEL, OPERACIONAL, MANUTENCAO, FIXA }

enum class Categoria(val rotulo: String, val natureza: Natureza) {
    COMBUSTIVEL("Combustível", Natureza.COMBUSTIVEL),
    ALIMENTACAO("Alimentação", Natureza.OPERACIONAL),
    LAVAGEM("Lavagem", Natureza.OPERACIONAL),
    ESTACIONAMENTO("Estacionamento", Natureza.OPERACIONAL),
    PEDAGIO("Pedágio", Natureza.OPERACIONAL),
    MULTA("Multa", Natureza.OPERACIONAL),
    OUTROS("Outros", Natureza.OPERACIONAL),
    MANUTENCAO("Manutenção", Natureza.MANUTENCAO),
    ALUGUEL("Aluguel do carro", Natureza.FIXA),
    PARCELA("Parcela do carro", Natureza.FIXA),
    SEGURO("Seguro", Natureza.FIXA),
    IPVA("IPVA / licenciamento", Natureza.FIXA),
    CELULAR("Celular / internet", Natureza.FIXA);

    companion object {
        fun deTexto(texto: String): Categoria {
            val chave = texto.trim().uppercase()
                .replace("Ç", "C").replace("Ã", "A").replace("Á", "A").replace("É", "E").replace("Ê", "E")
            return entries.firstOrNull { it.name == chave }
                ?: throw IllegalArgumentException(
                    "Categoria desconhecida: '$texto'. Use: ${entries.joinToString { it.name }}"
                )
        }
    }
}

data class Receita(
    val app: String,
    val valor: Double,
    val corridas: Int = 0,
)

data class Despesa(
    val categoria: Categoria,
    val valor: Double,
    val descricao: String = "",
    /** Só para combustível: litros abastecidos. */
    val litros: Double? = null,
    /** Só para combustível: hodômetro no momento do abastecimento. */
    val kmHodometro: Int? = null,
    /** Só para combustível: completou o tanque? (necessário para medir consumo real) */
    val tanqueCheio: Boolean = false,
)

data class Jornada(
    val data: LocalDate,
    val kmInicial: Int,
    val kmFinal: Int? = null,
    val inicio: LocalTime? = null,
    val fim: LocalTime? = null,
    /** Minutos parados que não contam como trabalho (almoço, descanso). */
    val pausaMinutos: Int = 0,
    val receitas: List<Receita> = emptyList(),
    val despesas: List<Despesa> = emptyList(),
) {
    val kmRodado: Int? get() = kmFinal?.let { it - kmInicial }

    /** Minutos trabalhados; se o fim for menor que o início, a jornada virou a meia-noite. */
    val minutosTrabalhados: Int?
        get() {
            if (inicio == null || fim == null) return null
            var minutos = fim.toSecondOfDay() / 60 - inicio.toSecondOfDay() / 60
            if (minutos < 0) minutos += 24 * 60
            return (minutos - pausaMinutos).coerceAtLeast(0)
        }

    val abastecimentos: List<Despesa> get() = despesas.filter { it.categoria == Categoria.COMBUSTIVEL }
}
