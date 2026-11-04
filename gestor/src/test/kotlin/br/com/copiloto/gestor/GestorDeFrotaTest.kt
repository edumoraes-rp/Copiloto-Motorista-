package br.com.copiloto.gestor

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestorDeFrotaTest {
    private val delta = 0.001

    // R$ 6,00/L a 12 km/L = R$ 0,50/km; manutenção R$ 0,10/km; fixos R$ 2.600/mês ÷ 26 dias = R$ 100/dia
    private val perfil = PerfilVeiculo(
        nome = "Teste",
        posse = Posse.ALUGADO,
        precoCombustivel = 6.0,
        consumoKmPorLitro = 12.0,
        custosFixos = listOf(CustoFixo("Aluguel", 2600.0)),
        manutencaoPorKm = 0.10,
        diasTrabalhadosPorMes = 26,
        metaLucroMensal = 5200.0,
    )
    private val gestor = GestorDeFrota(perfil)

    private val dia1 = Jornada(
        data = LocalDate.of(2026, 11, 2),
        kmInicial = 1000, kmFinal = 1200,
        inicio = LocalTime.of(6, 0), fim = LocalTime.of(16, 0), pausaMinutos = 60,
        receitas = listOf(Receita("Uber", 300.0, 15), Receita("99", 100.0, 5)),
        despesas = listOf(
            Despesa(Categoria.COMBUSTIVEL, 150.0, litros = 25.0),
            Despesa(Categoria.ALIMENTACAO, 30.0),
            Despesa(Categoria.ALUGUEL, 500.0),
        ),
    )

    @Test
    fun `resumo do dia separa lucro de caixa e lucro real`() {
        val r = gestor.resumoDoDia(dia1)
        assertEquals(200, r.kmRodado)
        assertEquals(9.0, r.horasTrabalhadas!!, delta)
        assertEquals(20, r.corridas)
        assertEquals(400.0, r.receitaBruta, delta)
        assertEquals(680.0, r.despesasPagas, delta)
        assertEquals(-280.0, r.lucroCaixa, delta) // pagou o aluguel da semana hoje

        assertEquals(100.0, r.custoCombustivel, delta) // 200 km × 0,50
        assertEquals(30.0, r.despesasOperacionais, delta) // só alimentação
        assertEquals(20.0, r.reservaManutencao, delta) // 200 km × 0,10
        assertEquals(100.0, r.custoFixoRateado, delta)
        assertEquals(250.0, r.custoTotal, delta)
        assertEquals(150.0, r.lucroReal, delta)

        assertEquals(2.0, r.ganhoPorKm!!, delta)
        assertEquals(1.25, r.custoPorKm!!, delta)
        assertEquals(0.75, r.lucroPorKm!!, delta)
        assertEquals(400.0 / 9, r.ganhoPorHora!!, delta)
        assertEquals(150.0 / 9, r.lucroPorHora!!, delta)
        assertEquals(20.0, r.ticketMedio!!, delta)
        assertEquals(0.375, r.margem!!, delta)
        assertEquals(mapOf("Uber" to 300.0, "99" to 100.0), r.receitaPorApp)
    }

    @Test
    fun `jornada que vira a meia-noite conta as horas certas`() {
        val j = Jornada(LocalDate.of(2026, 11, 7), 0, 100, LocalTime.of(20, 0), LocalTime.of(2, 0))
        assertEquals(6 * 60, j.minutosTrabalhados)
    }

    @Test
    fun `dia sem km final gera alerta e nao quebra`() {
        val r = gestor.resumoDoDia(dia1.copy(kmFinal = null, fim = null))
        assertNull(r.kmRodado)
        assertNull(r.ganhoPorKm)
        assertNull(r.ganhoPorHora)
        assertTrue(r.alertas.any { "km final" in it })
    }

    @Test
    fun `consumo real pelo metodo do tanque cheio`() {
        val abast = listOf(
            Despesa(Categoria.COMBUSTIVEL, 240.0, litros = 40.0, kmHodometro = 1000, tanqueCheio = true),
            Despesa(Categoria.COMBUSTIVEL, 60.0, litros = 10.0, kmHodometro = 1300),
            Despesa(Categoria.COMBUSTIVEL, 120.0, litros = 20.0, kmHodometro = 1600, tanqueCheio = true),
        )
        val c = assertNotNull(gestor.consumoReal(abast))
        assertEquals(600, c.kmMedidos)
        assertEquals(30.0, c.litrosMedidos, delta)
        assertEquals(20.0, c.kmPorLitro, delta)
        assertEquals(6.0, c.precoMedioLitro, delta)
        assertEquals(0.30, c.custoPorKm, delta)
    }

    @Test
    fun `sem dois tanques cheios nao ha consumo real`() {
        val abast = listOf(Despesa(Categoria.COMBUSTIVEL, 240.0, litros = 40.0, kmHodometro = 1000, tanqueCheio = true))
        assertNull(gestor.consumoReal(abast))
    }

    @Test
    fun `resumo do periodo soma dias, mede km fora do trabalho e calcula equilibrio`() {
        val dia2 = Jornada(
            data = LocalDate.of(2026, 11, 3),
            kmInicial = 1210, kmFinal = 1310, // 10 km fora do expediente, 100 km no dia
            inicio = LocalTime.of(7, 0), fim = LocalTime.of(12, 0),
            receitas = listOf(Receita("Uber", 200.0, 10)),
        )
        val p = gestor.resumoDoPeriodo(listOf(dia2, dia1)) // fora de ordem de propósito

        assertEquals(LocalDate.of(2026, 11, 2), p.inicio)
        assertEquals(2, p.diasTrabalhados)
        assertEquals(300, p.kmRodado)
        assertEquals(14.0, p.horasTrabalhadas, delta)
        assertEquals(600.0, p.receitaBruta, delta)
        // dia1 custo 250; dia2: 50 combustível + 10 manutenção + 100 fixo = 160
        assertEquals(410.0, p.custoTotal, delta)
        assertEquals(190.0, p.lucroReal, delta)
        assertEquals(dia1.data, p.melhorDia!!.data)
        assertEquals(10, p.kmForaDoTrabalho)
        assertEquals(6.0, p.custoKmForaDoTrabalho, delta) // 10 km × (0,50 + 0,10)
        assertEquals(30.0, p.saldoReservaManutencao, delta)

        // Equilíbrio: 100 fixo + 15 operacionais médios + 150 km médios × 0,60 = 205
        assertEquals(205.0, p.pontoEquilibrioDia, delta)
        assertEquals(205.0 / 150, p.valorMinimoPorKm!!, delta)
        // Meta 5.200 ÷ 26 = 200 a mais por dia
        assertEquals(405.0, p.receitaNecessariaParaMetaDia!!, delta)
        assertEquals(95.0 * 26, p.projecaoLucroMensal, delta)
        assertTrue(p.alertas.any { "fora do expediente" in it })
        assertNull(p.diasNaMetaDeReceita) // perfil sem meta de receita

        assertTrue(p.alertas.any { "meta" in it })
    }

    @Test
    fun `aluguel semanal vira custo mensal`() {
        assertEquals(700.0 * 52 / 12, CustoFixo.semanal("Aluguel", 700.0).valorMensal, delta)
        assertEquals(150.0, CustoFixo.anual("IPVA", 1800.0).valorMensal, delta)
    }

    @Test
    fun `meta de receita diaria conta dias na meta e lucro ao bater a meta`() {
        val comMeta = GestorDeFrota(perfil.copy(metaReceitaDiaria = 350.0))
        val dia2 = Jornada(LocalDate.of(2026, 11, 3), 1200, 1300, receitas = listOf(Receita("Uber", 200.0)))
        val p = comMeta.resumoDoPeriodo(listOf(dia1, dia2))
        assertEquals(1, p.diasNaMetaDeReceita) // só o dia 1 (R$ 400)
        assertEquals(350.0 - 410.0 / 2, p.lucroRealNaMetaDia!!, delta)
        assertTrue(p.alertas.any { "abaixo da meta" in it })
    }

    @Test
    fun `tanque cheio de referencia mede consumo sem entrar como despesa`() {
        val a = Jornada(LocalDate.of(2026, 11, 1), 1000, 1300, tanqueCheioNoKm = listOf(TanqueCheio(1000)))
        val b = Jornada(
            LocalDate.of(2026, 11, 2), 1300, 1400,
            despesas = listOf(Despesa(Categoria.COMBUSTIVEL, 120.0, litros = 20.0, kmHodometro = 1300, tanqueCheio = true)),
        )
        val p = gestor.resumoDoPeriodo(listOf(a, b))
        val c = assertNotNull(p.consumoReal)
        assertEquals(15.0, c.kmPorLitro, delta) // 300 km ÷ 20 L
        assertEquals(6.0, c.precoMedioLitro, delta) // só o abastecimento pago
        assertEquals(120.0, p.despesasPagas, delta)
    }

    @Test
    fun `cada trecho paga o combustivel que estava no tanque`() {
        // Gasolina a R$ 6,00 cheia no km 1000; etanol R$ 4,00 cheio no km 1300 (repôs 20 L); depois roda 100 km no etanol.
        val a = Jornada(LocalDate.of(2026, 11, 1), 1000, 1300, tanqueCheioNoKm = listOf(TanqueCheio(1000, 6.0, "GASOLINA")))
        val b = Jornada(
            LocalDate.of(2026, 11, 2), 1300, 1400,
            despesas = listOf(Despesa(Categoria.COMBUSTIVEL, 80.0, litros = 20.0, kmHodometro = 1300, tanqueCheio = true, combustivel = "ETANOL")),
        )
        val p = gestor.resumoDoPeriodo(listOf(a, b))
        val t = p.consumoReal!!.trechos.single()
        assertEquals(15.0, t.kmPorLitro, delta)
        assertEquals("GASOLINA", t.combustivel)
        assertEquals(120.0, p.dias[0].custoCombustivel, delta) // 20 L de gasolina × 6,00
        assertEquals(100 * 4.0 / 12.0, p.dias[1].custoCombustivel, delta) // etanol 4,00 ÷ consumo do perfil (12 km/L)
    }

    @Test
    fun `taxa da 99 entra proporcional a receita e o debito fica so no caixa`() {
        val g = GestorDeFrota(perfil.copy(taxasPorReceita = mapOf("99" to 52.99 / 600)))
        val j = Jornada(
            LocalDate.of(2026, 11, 4), 0, 100,
            receitas = listOf(Receita("99", 300.0), Receita("Uber", 100.0)),
            despesas = listOf(Despesa(Categoria.TAXA_APP, 52.99)),
        )
        val r = g.resumoDoDia(j)
        assertEquals(300 * 52.99 / 600, r.taxasApp, delta)
        assertEquals(0.0, r.despesasOperacionais, delta)
        assertEquals(400.0 - 52.99, r.lucroCaixa, delta)
        assertEquals(50.0 + 10.0 + 100.0 + 300 * 52.99 / 600, r.custoTotal, delta)
    }
}
