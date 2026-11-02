# Diário de bordo (fase de testes, antes das telas do app)

Durante estes dias de teste, você me manda os dados de cada dia pelo chat. Eu os lanço em
`lancamentos.csv`, rodo o motor de cálculo (o mesmo que vai dentro do app) e devolvo o relatório.

## O que me mandar

### Uma vez só: o carro (`perfil.txt`)

- Carro: quitado, financiado ou alugado
- Combustível: tipo, preço que você costuma pagar por litro, consumo médio (km/L), se souber
- Custos fixos com o valor e a frequência: aluguel semanal, parcela, seguro, IPVA, rastreador, plano de celular...
- Quantos dias você trabalha por mês, mais ou menos
- Meta de lucro no mês ou de receita por dia (opcional)
- Reserva de manutenção por km, se tiver ideia. Se não tiver, eu calculo com você (troca de óleo, pneus, freios ÷ km de cada um)

### Todo dia (pode ser mensagem curta)

```
Dia 05/11
Saí 06:40 com 45.905 km
Uber 312,40 (16 corridas), 99 88,00 (5)
Abasteci 200,00, 32,3 L, hodômetro 46.010, tanque cheio
Almoço 25,00
Parei 40 min
Cheguei 16:15 com 46.150 km
```

Dicas:

- **Abastecimento:** anote valor, litros, km do hodômetro e se encheu o tanque. Com dois tanques cheios eu meço o seu consumo real.
- **Pagou aluguel ou parcela no dia?** Mande também. Isso entra no "lucro de caixa", mas no lucro real o custo já está dividido por dia.
- **Manutenção** (óleo, pneu): mande também. Ela sai da reserva de manutenção.
- Se esquecer algo, mande depois; eu corrijo.

## Formato do arquivo (para quem quiser editar à mão)

`lancamentos.csv`, separado por `;`, valores com vírgula:

| tipo | campos |
|---|---|
| `INICIO` | km inicial ; hora (HH:MM) |
| `FIM` | km final ; hora ; minutos de pausa (opcional) |
| `HORAS` | tempo trabalhado (HH:MM), quando não houver hora de início e fim |
| `RECEITA` | app ; valor ; nº de corridas (opcional) |
| `DESPESA` | categoria ; valor ; descrição (opcional) |
| `ABASTECIMENTO` | valor ; litros ; km do hodômetro ; `CHEIO` ou `PARCIAL` |

Categorias: `ALIMENTACAO`, `LAVAGEM`, `ESTACIONAMENTO`, `PEDAGIO`, `MULTA`, `OUTROS`,
`MANUTENCAO`, `ALUGUEL`, `PARCELA`, `SEGURO`, `IPVA`, `CELULAR`.

Gerar o relatório (na raiz do projeto):

```
gradle :gestor:run                                    # seus dados (pasta diario)
gradle :gestor:run --args="diario/exemplo"            # exemplo com dados fictícios
gradle :gestor:run --args="diario diario/relatorio.md"  # também salva em arquivo
```

Veja `exemplo/relatorio.md` para o resultado com dados fictícios, e `../docs/gestor-de-frota.md` para as contas.
