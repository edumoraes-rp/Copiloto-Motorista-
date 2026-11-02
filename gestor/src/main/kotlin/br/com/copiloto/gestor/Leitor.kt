package br.com.copiloto.gestor

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Lê os arquivos de texto do diário (enquanto o app não tem telas):
 * - perfil.txt: dados do carro e dos custos fixos;
 * - lancamentos.csv: o dia a dia (início, fim, receitas, despesas, abastecimentos).
 * O formato está explicado em diario/LEIA-ME.md.
 */
object Leitor {
    private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun lerPerfil(texto: String): PerfilVeiculo {
        val valores = mutableMapOf<String, String>()
        val fixos = mutableListOf<CustoFixo>()
        texto.lineSequence().forEachIndexed { i, bruta ->
            val linha = bruta.substringBefore('#').trim()
            if (linha.isEmpty()) return@forEachIndexed
            val chave = linha.substringBefore('=').trim().lowercase()
            val valor = linha.substringAfter('=', "").trim()
            require(linha.contains('=')) { "perfil.txt linha ${i + 1}: falta '=' em '$bruta'" }
            if (chave.startsWith("fixo.")) {
                val descricao = valor.substringBefore(';').trim()
                val quantia = Formato.lerNumero(valor.substringAfter(';', ""))
                fixos += when (chave.removePrefix("fixo.")) {
                    "mensal" -> CustoFixo(descricao, quantia)
                    "semanal" -> CustoFixo.semanal(descricao, quantia)
                    "anual" -> CustoFixo.anual(descricao, quantia)
                    else -> throw IllegalArgumentException("perfil.txt linha ${i + 1}: use fixo.mensal, fixo.semanal ou fixo.anual")
                }
            } else {
                valores[chave] = valor
            }
        }
        fun obrigatorio(c: String) = valores[c] ?: throw IllegalArgumentException("perfil.txt: falta '$c'")
        return PerfilVeiculo(
            nome = valores["nome"] ?: "Meu carro",
            posse = valores["posse"]?.let { Posse.valueOf(it.uppercase()) } ?: Posse.QUITADO,
            precoCombustivel = Formato.lerNumero(obrigatorio("preco_combustivel")),
            consumoKmPorLitro = Formato.lerNumero(obrigatorio("consumo_km_l")),
            custosFixos = fixos,
            manutencaoPorKm = valores["manutencao_por_km"]?.let(Formato::lerNumero) ?: 0.0,
            diasTrabalhadosPorMes = valores["dias_trabalhados_mes"]?.toInt() ?: 26,
            metaLucroMensal = valores["meta_lucro_mensal"]?.let(Formato::lerNumero) ?: 0.0,
            metaReceitaDiaria = valores["meta_receita_dia"]?.let(Formato::lerNumero) ?: 0.0,
        )
    }

    fun lerLancamentos(texto: String): List<Jornada> {
        class Rascunho(val data: LocalDate) {
            var kmInicial: Int? = null
            var kmFinal: Int? = null
            var inicio: LocalTime? = null
            var fim: LocalTime? = null
            var pausa = 0
            var minutos: Int? = null
            var parcial = false
            val receitas = mutableListOf<Receita>()
            val despesas = mutableListOf<Despesa>()
        }
        val dias = linkedMapOf<LocalDate, Rascunho>()

        texto.lineSequence().forEachIndexed { i, bruta ->
            val linha = bruta.trim()
            if (linha.isEmpty() || linha.startsWith("#") || linha.lowercase().startsWith("data;")) return@forEachIndexed
            val c = linha.split(';').map { it.trim() }
            fun campo(n: Int) = c.getOrNull(n)?.takeIf { it.isNotEmpty() }
            fun erro(msg: String): Nothing = throw IllegalArgumentException("lancamentos.csv linha ${i + 1} ($linha): $msg")
            try {
                val data = LocalDate.parse(c[0], formatoData)
                val dia = dias.getOrPut(data) { Rascunho(data) }
                when (c.getOrNull(1)?.uppercase()) {
                    "INICIO" -> {
                        dia.kmInicial = campo(2)?.toInt() ?: erro("falta o km inicial")
                        dia.inicio = campo(3)?.let(LocalTime::parse)
                    }
                    "FIM" -> {
                        dia.kmFinal = campo(2)?.toInt() ?: erro("falta o km final")
                        dia.fim = campo(3)?.let(LocalTime::parse)
                        dia.pausa = campo(4)?.toInt() ?: 0
                    }
                    "PARCIAL" -> dia.parcial = true
                    "HORAS" -> dia.minutos = LocalTime.parse(campo(2)?.padStart(5, '0') ?: erro("falta o tempo (HH:MM)")).let { it.hour * 60 + it.minute }
                    "RECEITA" -> dia.receitas += Receita(
                        app = campo(2) ?: erro("falta o app"),
                        valor = Formato.lerNumero(campo(3) ?: erro("falta o valor")),
                        corridas = campo(4)?.toInt() ?: 0,
                    )
                    "DESPESA" -> dia.despesas += Despesa(
                        categoria = Categoria.deTexto(campo(2) ?: erro("falta a categoria")),
                        valor = Formato.lerNumero(campo(3) ?: erro("falta o valor")),
                        descricao = campo(4) ?: "",
                    )
                    "ABASTECIMENTO" -> dia.despesas += Despesa(
                        categoria = Categoria.COMBUSTIVEL,
                        valor = Formato.lerNumero(campo(2) ?: erro("falta o valor")),
                        litros = campo(3)?.let(Formato::lerNumero),
                        kmHodometro = campo(4)?.toInt(),
                        tanqueCheio = campo(5)?.uppercase() == "CHEIO",
                    )
                    else -> erro("tipo deve ser INICIO, FIM, HORAS, RECEITA, DESPESA ou ABASTECIMENTO")
                }
            } catch (e: Exception) {
                if (e.message?.startsWith("lancamentos.csv") == true) throw e
                erro(e.message ?: e.toString())
            }
        }

        return dias.values.map { r ->
            Jornada(
                data = r.data,
                kmInicial = r.kmInicial ?: throw IllegalArgumentException("Dia ${Formato.data(r.data)} sem linha INICIO"),
                kmFinal = r.kmFinal,
                inicio = r.inicio,
                fim = r.fim,
                pausaMinutos = r.pausa,
                minutosInformados = r.minutos,
                parcial = r.parcial,
                receitas = r.receitas,
                despesas = r.despesas,
            )
        }
    }
}
