# Guia de Estudo: Campo Minado Completo (Níveis, Marcação e Persistência)

Este material foi estruturado especificamente como fonte de conhecimento para upload e indexação no **Google Gemini NotebookLM**. Ele aborda os conceitos teóricos, decisões arquiteturais, padrões de código e funcionamento interno da implementação do jogo em Android nativo com Jetpack Compose.

---

## 1. Visão Geral do Sistema e Requisitos Atendidos

O projeto evolui o Campo Minado clássico para uma versão moderna em arquitetura declarativa reativa. Os três pilares desta etapa são:

1. **Sistema de Marcação Estratégica**:
   - Estados por célula não revelada: `NORMAL` $\to$ `BANDEIRA (🚩)` $\to$ `DUVIDA (?)` $\to$ `NORMAL`.
   - Modos de interação complementares:
     - Toque longo (`onLongClick` via `combinedClickable`) direto em qualquer célula fechada.
     - Botão de alternância na barra superior (`Modo: 👆 REVELAR` vs `Modo: 🚩 MARCAR`), permitindo controle com toque simples para acessibilidade.
   - Células sinalizadas com bandeira ficam protegidas contra revelação acidental.

2. **Dificuldades e Tabuleiros Configuráveis**:
   - **Fácil**: $10 \times 10$, $15\%$ de bombas ($\lfloor 100 \times 0.15 \rceil = 15$ minas).
   - **Médio**: $16 \times 16$, $18\%$ de bombas ($\lfloor 256 \times 0.18 \rceil = 46$ minas).
   - **Difícil**: $30 \times 16$, $21\%$ de bombas ($\lfloor 480 \times 0.21 \rceil = 101$ minas).
   - **Personalizado**: Parâmetros customizados de largura ($5$ a $40$), altura ($5$ a $30$) e quantidade ou porcentagem de bombas ($5\%$ a $80\%$).

3. **Métricas e Concorrência**:
   - Cronômetro ativo via Coroutines estruturadas (`LaunchedEffect` com `delay(1000L)`).
   - Contador estrito de jogadas (cliques de revelação).
   - Contagem dinâmica de bombas restantes ($TotalMinas - Bandeiras$).
   - Relógio de tempo real do sistema sincronizado via loop assíncrono.
   - Fórmula de pontuação calibrada por desempenho:
     $$\text{Pontuação} = \max(10, 10000 - (\text{segundos} \times 10 + \text{jogadas} \times 20))$$
   - Ranking persistente dos Top 10 com identificação de nível, data/hora e pontuação.

4. **Persistência Local**:
   - Serialização de estado completo em JSON armazenado em `SharedPreferences`.
   - Recuperação automática de partidas interrompidas e retenção das preferências do usuário no nível personalizado.

---

## 2. Arquitetura de Estado e Imutabilidade

### 2.1 Modelo de Dados Imutável

O jogo adota um estado unidirecional onde toda mutação gera uma nova cópia do estado, garantindo que o ciclo de recomposição do Jetpack Compose detecte as alterações com confiabilidade.

```kotlin
enum class Marca { NORMAL, BANDEIRA, DUVIDA }

data class EstadoJogo(
    val nomeNivel: String,
    val largura: Int,
    val altura: Int,
    val totalMinas: Int,
    val celulas: IntArray,
    val reveladas: Set<Int>,
    val marcas: Map<Int, Marca>,
    val jogadas: Int,
    val segundos: Int,
    val gameOver: Boolean,
    val vitoria: Boolean,
    val partidaSalvaNoRanking: Boolean
)
```

- **Mapeamento 1D de Matriz 2D**: A matriz do tabuleiro de dimensões $L \times A$ é achatada em um vetor linear de tamanho $L \times A$. O índice é calculado por:
  $$\text{índice} = \text{linha} \times L + \text{coluna}$$
  $$\text{linha} = \lfloor \text{índice} / L \rfloor, \quad \text{coluna} = \text{índice} \pmod L$$
  Isso simplifica a serialização JSON e reduz a sobrecarga de alocação de objetos `Array<IntArray>`.

- **Convenção de Valores das Células**:
  - `-1`: Mina/Bomba.
  - `0`: Célula vazia (sem minas adjacentes).
  - `1` a `8`: Quantidade de minas no octógono de vizinhança imediata.

---

## 3. Algoritmos e Lógica Central

### 3.1 Distribuição Uniforme de Minas

Para garantir que a geração seja justa e sem repetições de coordenadas, utiliza-se o algoritmo de embaralhamento sobre os índices possíveis:

1. Gera-se o intervalo `(0 until total)`.
2. Aplica-se `.shuffled(Random.Default)`.
3. Os primeiros `minasEfetivas` índices recebem `-1`.
4. Uma passagem calcula as vizinhanças para as células restantes através do delta $[-1, 0, 1] \times [-1, 0, 1]$ checando limites de grade.

### 3.2 Revelação em Cascata (BFS - Breadth-First Search)

Quando uma célula com valor `0` é revelada, todas as células vazias adjacentes e suas fronteiras numéricas devem ser abertas automaticamente. Implementa-se uma busca em largura:

```kotlin
fun expandirZeros(inicial: Int, celulas: IntArray, largura: Int, altura: Int): Set<Int> {
    val descobertos = mutableSetOf<Int>()
    val fila = ArrayDeque<Int>()
    fila.add(inicial)

    while (fila.isNotEmpty()) {
        val atual = fila.removeFirst()
        if (atual in descobertos) continue
        descobertos.add(atual)

        if (celulas[atual] != 0) continue

        val lin = atual / largura
        val col = atual % largura

        for (dl in -1..1) {
            for (dc in -1..1) {
                if (dl == 0 && dc == 0) continue
                val nl = lin + dl
                val nc = col + dc
                if (nl in 0 until altura && nc in 0 until largura) {
                    val vizinho = nl * largura + nc
                    if (vizinho !in descobertos && celulas[vizinho] != -1) {
                        fila.add(vizinho)
                    }
                }
            }
        }
    }
    return descobertos
}
```

### 3.3 Máquina de Estados da Marcação

A transição de marcação é pura e cíclica:
$$\text{Marcação}(c) = \begin{cases}
\text{BANDEIRA}, & \text{se atual} = \text{NORMAL} \\
\text{DUVIDA}, & \text{se atual} = \text{BANDEIRA} \\
\text{NORMAL}, & \text{se atual} = \text{DUVIDA}
\end{cases}$$

Regra de consistência: Se uma célula for expandida via abertura em cascata, qualquer marcação prévia nela é removida automaticamente (`novasMarcas = estado.marcas.filterKeys { it !in expandidas }`).

---

## 4. Concorrência Estruturada e Ciclo de Vida no Jetpack Compose

### 4.1 Cronômetro com `LaunchedEffect`

O cronômetro utiliza o mecanismo de efeito colateral consciente de ciclo de vida do Compose:

```kotlin
LaunchedEffect(estado.gameOver, estado.vitoria) {
    if (!estado.gameOver && !estado.vitoria) {
        while (true) {
            delay(1000L)
            onAtualizarEstado(estado.copy(segundos = estado.segundos + 1))
        }
    }
}
```

**Propriedades de Engenharia**:
- Chaves do efeito: `estado.gameOver` e `estado.vitoria`. Se o jogo termina por explosão ou vitória, o efeito anterior é automaticamente cancelado pelo Compose, encerrando o loop da coroutine sem risco de vazamento de memória (*coroutine leak*).
- O atraso não bloqueia a thread de interface (`Dispatchers.Main`), pois `delay` é uma função suspensiva não bloqueante.

### 4.2 Sincronização do Relógio do Sistema

Um segundo `LaunchedEffect(Unit)` roda continuamente para formatar a hora atual via `LocalTime.now()`, desacoplado do cronômetro da partida.

---

## 5. Persistência Local com SharedPreferences e JSON

O aplicativo utiliza a biblioteca nativa `org.json` acoplada ao `SharedPreferences` para garantir ausência de dependências externas pesadas (como Room/SQL):

1. **Estado em Andamento (`CHAVE_JOGO`)**:
   - Salvo reativamente a cada mudança de estado via `LaunchedEffect(estadoJogo)`.
   - Estrutura serializada:
     - Metadados: `nomeNivel`, `largura`, `altura`, `totalMinas`, `jogadas`, `segundos`, `gameOver`, `vitoria`.
     - Vetor de células: `JSONArray` com os inteiros da matriz linearizada.
     - Células reveladas: `JSONArray` com os índices já abertos.
     - Marcações: `JSONObject` mapeando `indice -> "BANDEIRA" | "DUVIDA"`.
   - Limpeza: Ao voltar ao menu ou reiniciar após conclusão, o registro salvo é removido.

2. **Ranking Global (`CHAVE_RANKING`)**:
   - Vetor de objetos contendo: `nome`, `dataHora`, `tempoSegundos`, `jogadas`, `pontuacao`, `nivel`.
   - Ordenado decrescente por pontuação e limitado estritamente ao Top 10 (`take(10)`).

---

## 6. Layout Adaptativo e Renderização

### 6.1 Dimensionamento Dinâmico com `BoxWithConstraints`

Um dos maiores desafios técnicos em tabuleiros grandes (ex: Difícil com $30$ colunas) é caber na tela de smartphones verticais. A solução implementada combina:

```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val tamanhoCalculado: Dp = (maxWidth / estado.largura).coerceIn(16.dp, 36.dp)

    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Column {
            // Renderização das linhas e colunas
        }
    }
}
```

- Para tabuleiros pequenos ($10 \times 10$), a célula expande até $36\,\text{dp}$.
- Para tabuleiros médios ($16 \times 16$), ajusta-se para ocupar perfeitamente a largura.
- Para tabuleiros gigantes ($30 \times 16$), fixa-se no limite mínimo legível com barra de rolagem horizontal suave (`horizontalScroll`), integrada à rolagem vertical da página inteira.

---

## 7. Perguntas e Respostas Rápidas para Revisão

1. **Como o jogo previne que o jogador perca ao clicar em uma célula marcada com bandeira?**
   No método `revelarCelula`, a primeira verificação valida se `estado.marcas[indice] == Marca.BANDEIRA`. Se for verdadeiro, o clique é imediatamente descartado sem alterar o estado.

2. **Qual é a condição formal de vitória?**
   A vitória ocorre quando o tamanho do conjunto de células reveladas atinge exatamente o número total de células seguras:
   $$|\text{reveladas}| \ge (\text{largura} \times \text{altura} - \text{totalMinas})$$

3. **Por que o estado do tabuleiro usa `IntArray` e não `List<List<Int>>`?**
   Por razões de desempenho e eficiência de memória na JVM do Android: arrays primitivos evitam o overhead de *boxing* (conversão de tipo primitivo para objeto `Integer`) e reduzem o consumo de memória em matrizes grandes como $30 \times 16 = 480$ células.

4. **Como o APK foi compilado neste ambiente?**
   O ambiente executa sob o runtime OpenJDK 21 via `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug`, gerando o binário `campominadoextreme.apk` disponível na raiz do repositório.
