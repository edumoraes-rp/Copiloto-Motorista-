# Copiloto do Motorista de Aplicativo (diário de bordo + calculadora de corridas)

Contexto de projeto para o Claude Code. Responda em português do Brasil.
Dono do projeto: Eduardo Moraes (motorista de aplicativo em Ribeirão Preto, SP; usa Uber e 99). Ele conhece bem o uso real do app, mas está começando no desenvolvimento Android: explique decisões e prefira passos pequenos e testáveis.

## Ideia do produto

App Android que é, antes de tudo, um **diário de bordo do motorista**:

1. **Abertura do dia:** informa o km de saída; a calculadora de ganho é ligada.
2. **Durante o turno:** a calculadora lê cada oferta de corrida na tela (Uber, 99, inDrive) e mostra um **semáforo** (verde, amarelo, vermelho) com base nas faixas e nos custos que o motorista configurou. Todas as ofertas, aceitas ou recusadas, vão para o histórico.
3. **Despesas:** combustível, alimentação etc., digitadas ou por **foto do comprovante** (texto lido no aparelho; o motorista confere antes de salvar).
4. **Fechamento do dia:** informa o km final. O app mostra tempo trabalhado, km rodado, receita, despesas e lucro real.
5. **Histórico** de dias e de ofertas.

Diferencial: GigU e Rota Pro (referências) fazem bem a calculadora, mas, pelo que pesquisamos, nenhum registra quilometragem e despesas do dia como um diário.

## Referências analisadas

- **GigU:** Android; semáforo, lucro líquido (desde fev/2026), histórico de ofertas, aviso por voz, atalho para Maps/Street View, comunidade grande; Uber já contestou na Justiça e a decisão foi favorável à GigU.
- **Rota Pro** (FRM Tech): Android; Uber, 99 e inDrive; semáforo, custo real por km, filtros, print automático, menu flutuante, cobrança anual (menos de 10 reais por mês). Estrutura vista nas telas:
  - Início: Assinatura, Copiloto (sempre visível), Semáforo de Valores (por app), Câmera Secreta, Jornada (cronômetro por situação: offline, aguardando, buscando, embarque, em viagem).
  - Semáforo de Valores: estilo do cartão; apps monitorados; classificação por cor (faixas ruim/média/boa) para ganho por km, ganho por hora e nota do passageiro; ganhos mínimos (valor da viagem, lucro líquido mínimo, % de lucro); custo operacional (custo por km); limites de tempo e distância (embarque e total).
  - Custo operacional (assistente em 3 passos): custos fixos (carro/moto, passageiros/entregas, nome do veículo, diária e dias de aluguel, outros custos mensais) → combustível (tipo, preço por litro, consumo médio) → metas (km por dia e por mês, meta de lucro mensal, dias e horas trabalhados). Veículo financiado, alugado ou quitado.
  - Histórico: por período, filtro por app, abas todas/aceitas/recusadas, média por corrida/hora/km, cartão de cada corrida com lucro.
  - Melhores Horários: faixas de horário por R$/km.
  - Ferramentas: estilo do cartão, melhores horários, custo operacional, app de navegação, alertas da viagem, notificação por voz, mensagem personalizada, print automático, endereços de risco.

## Decisões de arquitetura

- **Android nativo, Kotlin**, UI em **Jetpack Compose**. Processamento **no aparelho**, sem depender de internet (decidir em menos de 1 segundo).
- **Leitura da tela:** `AccessibilityService`, somente leitura. **Overlay** com `WindowManager` (permissão de sobreposição) para o cartão do semáforo. `Foreground Service` com notificação.
- **Leitor por app:** um módulo Kotlin separado para Uber, 99 e inDrive (as telas mudam sem aviso; isolar e testar).
- **Motor de cálculo:** Kotlin puro, com testes automáticos (ganho por km e por hora, lucro líquido, cor do semáforo).
- **Dados:** Room (SQLite) para dias, despesas, ofertas, perfil do veículo, metas e contas; DataStore para configurações.
- **Comprovantes por foto:** CameraX + reconhecimento de texto no aparelho (por exemplo, ML Kit). Fotos ficam só no aparelho e podem ser apagadas.
- **Nuvem (opcional, depois do MVP):** conta, backup e assinatura; ferramenta a definir.

Componentes (12): cadastro de custos, diário do dia, despesas e comprovantes, serviço de acessibilidade, leitor por app, motor de cálculo, overlay, serviço em primeiro plano, banco local, configurações, telas do app, nuvem (opcional).

## Regras de produto (não negociáveis)

- O app **só lê a tela**. Nunca aceita corrida, nunca clica pelo motorista, não pede senha dos apps de corrida.
- Não guardar o texto bruto da tela; salvar só os campos extraídos da oferta.
- O app não sabe sozinho se a corrida foi aceita: reconhecer a tela de corrida em andamento e permitir que o motorista corrija o histórico à mão.
- Leitura de comprovante pode errar: sempre mostrar os campos para conferência e permitir digitação.

## Fases do produto

- **Fase 1 (MVP):** cadastro do veículo e dos custos (assistente em 3 passos); calculadora de ganho com semáforo e faixas configuráveis; abertura do dia; despesas; fechamento do dia com resumo; histórico.
- **Fase 2:** alertas (paradas, distância e tempo máximos, usuário iniciante), áreas de risco e endereços a evitar, atalho para Maps/Street View, aviso por voz, menu flutuante.
- **Fase 3:** meta diária e semanal, contas a pagar (aluguel, parcelas, IPVA, seguro), reserva de manutenção por km, melhores horários e regiões, km sem corrida (odômetro menos km das corridas).

## Ordem de desenvolvimento sugerida

1. Esqueleto do app (Compose + Room) e cadastro do veículo e dos custos.
2. **Protótipo do serviço de Acessibilidade lendo a oferta da Uber** (ponto mais arriscado; prova a ideia).
3. Diário do dia: abertura e fechamento com km, e resumo.
4. Despesas: primeiro digitadas, depois por foto.
5. Motor de cálculo, semáforo, overlay e configuração das faixas.
6. Leitores da 99 e da inDrive.
7. Histórico de dias e de ofertas (aceitas e recusadas).
8. Testes em aparelhos reais, aviso de Acessibilidade e declaração para a Play Store.

Sugestão: o motor de cálculo e a lógica do diário podem ser desenvolvidos e testados antes (e sem depender) da leitura de tela.

## Permissões, Play Store e jurídico

- Acessibilidade: o motorista ativa manualmente nas configurações do Android, depois de um aviso destacado dentro do app. A Play Store exige o formulário de declaração de uso da API de Acessibilidade para apps que não são ferramentas de acessibilidade; a aprovação não é garantida.
- Sobreposição de tela e câmera (esta só quando o motorista usar o recurso de foto).
- Não é necessária autorização da Uber, 99 ou inDrive para desenvolver, mas há risco de reação das plataformas (termos de uso). Não sou advogado: consultar um antes de lançar. LGPD: política de privacidade (localização, despesas, dados financeiros) e opção de apagar os dados.
- Android apenas: o iPhone não permite que um app leia a tela de outro.

## Pendências em aberto

- **Preço e modelo de cobrança:** decisão adiada pelo Eduardo; não implementar cobrança ainda.
- Ferramenta da nuvem (conta, backup, assinatura).
- Protótipo visual das telas do diário (abertura, despesas, fechamento).
- Juntar prints reais das ofertas da Uber, 99 e inDrive (ocultando nome e dados pessoais) para desenhar os leitores.

## Documentos de referência (claude.ai)

- Referências e MVP: https://claude.ai/code/artifact/ac9e34ad-aa17-437d-9943-edc460ff1229
- Arquitetura técnica: https://claude.ai/code/artifact/4a48c2cf-5d50-4798-acea-e15ffd3e2efd

## Gestor de frota (módulo `gestor`, já implementado)

- Motor de cálculo do diário em Kotlin puro: `gestor/src/main/kotlin/br/com/copiloto/gestor/` (testes: `gradle :gestor:test`).
- Fase de testes: o Eduardo manda os dados do dia pelo chat; lançar em `diario/lancamentos.csv` (formato em `diario/LEIA-ME.md`) e rodar `gradle :gestor:run --args="diario diario/relatorio.md"`.
- Contas explicadas em `docs/gestor-de-frota.md` (lucro de caixa × lucro real, consumo real por tanque cheio, ponto de equilíbrio, valor mínimo por km).
- Painel visual (dashboard): https://claude.ai/artifact/3QRUbw2Lrit1NExaz997WE. `gradle :gestor:run` gera `diario/painel/{dias,apps,perfil}.json`; para atualizar o painel, subir esses JSON como arquivos do painel e trocar a `url` de cada fonte. A página fica em `diario/painel/index.html`.
- Pedido do Eduardo: ao concluir cada atualização do diário/painel, sempre terminar a resposta com o atalho do painel: https://claude.ai/artifact/3QRUbw2Lrit1NExaz997WE
