# Gestor de frota: como as contas são feitas

Código: `gestor/src/main/kotlin/br/com/copiloto/gestor/` (Kotlin puro, sem Android, com testes em `gestor/src/test`).
O app Android vai usar este mesmo módulo; as telas só alimentam `Jornada` e `PerfilVeiculo` e mostram os resultados.

## Dois lucros, duas perguntas

| | Pergunta | Conta |
|---|---|---|
| **Lucro de caixa** | Quanto dinheiro sobrou na mão hoje? | receita − tudo o que foi pago no dia |
| **Lucro real** | Quanto eu de fato ganhei trabalhando hoje? | receita − custo real do dia |

Eles diferem porque o que se paga num dia não é o que se gasta nele: enche-se o tanque hoje e roda-se com ele três dias;
paga-se o aluguel da semana na segunda. O lucro de caixa oscila; o lucro real é o que serve para decidir.

## Custo real do dia

```
custo real = combustível consumido + despesas do dia + reserva de manutenção + custo fixo rateado
```

| Parte | Conta | Por quê |
|---|---|---|
| Combustível consumido | km rodado × (preço do litro ÷ km/L) | Usa o consumo **real** quando houver 2 tanques cheios; senão, o do perfil |
| Despesas do dia | alimentação, lavagem, estacionamento, pedágio, multa, outros | Gastos que só existem porque você trabalhou hoje |
| Reserva de manutenção | km rodado × R$/km de manutenção | Óleo, pneus e freios se gastam por km; o pagamento sai da reserva |
| Custo fixo rateado | soma dos fixos mensais ÷ dias trabalhados no mês | Aluguel, parcela, seguro, IPVA, celular |

Conversões dos fixos: semanal × 52 ÷ 12; anual ÷ 12.

Pagamentos de combustível, manutenção e custos fixos aparecem no lucro de caixa, mas **não** entram de novo no
lucro real (já estão representados pelas linhas acima). Assim nada é contado duas vezes.

## Indicadores

- Ganho por km = receita ÷ km; custo por km = custo real ÷ km; lucro por km = lucro real ÷ km
- Ganho por hora e lucro por hora: horas = fim − início − pausa (jornada que passa da meia-noite é tratada)
- Ticket médio = receita ÷ corridas; margem = lucro real ÷ receita
- Receita por app e despesas por categoria; melhor e pior dia (por lucro real)

## Combustível

**Consumo real (método do tanque cheio):** entre dois abastecimentos de tanque cheio, km rodados ÷ litros
colocados depois do primeiro (inclusive os parciais e o segundo cheio). Preço médio = total pago ÷ litros.
Se o consumo real diferir mais de 10% do cadastrado, o relatório avisa.

**Consumido × pago:** mostra quanto combustível ficou no tanque (ou foi gasto além do que se repôs) no período.

## Conciliações

- **Saldo da reserva de manutenção:** reserva acumulada − manutenção paga. É o dinheiro a separar.
- **Km fora do expediente:** km inicial de um dia − km final do dia anterior. Uso pessoal (ou esquecimento);
  é mostrado com seu custo, mas não entra no lucro do trabalho.

## Planejamento

- **Ponto de equilíbrio por dia** = fixo por dia + despesas do dia (média) + km médio × (combustível/km + manutenção/km)
- **Valor mínimo por km** = ponto de equilíbrio ÷ km médio por dia. Esse número vai alimentar o semáforo da calculadora.
- **Receita por dia para a meta** = ponto de equilíbrio + meta mensal ÷ dias trabalhados
- **Projeção do mês** = lucro real médio por dia × dias trabalhados no mês
- **Meta de receita por dia** (`meta_receita_dia`): receita média comparada à meta, quantos dias bateram a meta e o lucro real por dia e por mês se a meta for batida (meta − custo médio por dia)

## Alertas

Km final faltando ou menor que o inicial, km do dia acima de 700, hora faltando, dia sem receita, dia com prejuízo,
abastecimento sem litros/hodômetro, km fora do expediente, consumo real diferente do cadastrado e meta fora de alcance.

## Próximos passos

- Ajustar as contas com os dados reais dos dias de teste.
- Ligar `valorMinimoPorKm` e `custoPorKm` ao semáforo da calculadora de corridas.
- Levar `Jornada`, `Despesa` e `Receita` para tabelas do Room no app.
