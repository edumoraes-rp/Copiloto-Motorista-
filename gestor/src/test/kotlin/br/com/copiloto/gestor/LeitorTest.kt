package br.com.copiloto.gestor

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LeitorTest {
    @Test
    fun `le perfil com custos fixos mensais, semanais e anuais`() {
        val p = Leitor.lerPerfil(
            """
            nome = Onix 2022
            posse = alugado
            preco_combustivel = 6,19
            consumo_km_l = 11,5
            manutencao_por_km = 0,12   # óleo, pneus, freios
            dias_trabalhados_mes = 26
            meta_lucro_mensal = 4.000,00
            fixo.semanal = Aluguel ; 750
            fixo.anual = IPVA ; 1.800,00
            fixo.mensal = Celular ; 60
            """.trimIndent()
        )
        assertEquals(Posse.ALUGADO, p.posse)
        assertEquals(6.19, p.precoCombustivel, 1e-9)
        assertEquals(0.12, p.manutencaoPorKm, 1e-9)
        assertEquals(4000.0, p.metaLucroMensal, 1e-9)
        assertEquals(750.0 * 52 / 12 + 150 + 60, p.custoFixoMensal, 1e-6)
    }

    @Test
    fun `le lancamentos agrupando por dia`() {
        val dias = Leitor.lerLancamentos(
            """
            data;tipo;campo1;campo2;campo3;campo4
            02/11/2026;INICIO;45200;06:30
            02/11/2026;RECEITA;Uber;185,40;12
            02/11/2026;RECEITA;99;96,20;7
            02/11/2026;ABASTECIMENTO;150,00;24,2;45300;CHEIO
            02/11/2026;DESPESA;alimentação;28,00;Almoço
            02/11/2026;FIM;45410;15:10;40
            03/11/2026;INICIO;45415;07:00
            03/11/2026;HORAS;2:00
            """.trimIndent()
        )
        assertEquals(2, dias.size)
        val d = dias[0]
        assertEquals(LocalDate.of(2026, 11, 2), d.data)
        assertEquals(210, d.kmRodado)
        assertEquals(LocalTime.of(6, 30), d.inicio)
        assertEquals(8 * 60 + 40 - 40, d.minutosTrabalhados)
        assertEquals(281.60, d.receitas.sumOf { it.valor }, 1e-9)
        val abast = d.abastecimentos.single()
        assertEquals(24.2, abast.litros)
        assertEquals(45300, abast.kmHodometro)
        assertTrue(abast.tanqueCheio)
        assertEquals(Categoria.ALIMENTACAO, d.despesas.last().categoria)
        assertEquals(null, dias[1].kmFinal)
        assertEquals(120, dias[1].minutosTrabalhados)
    }

    @Test
    fun `erro indica a linha`() {
        val e = assertFailsWith<IllegalArgumentException> {
            Leitor.lerLancamentos("02/11/2026;INICIO;abc;06:30")
        }
        assertTrue(e.message!!.contains("linha 1"), e.message)
    }
}
