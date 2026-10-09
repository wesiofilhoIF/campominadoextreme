package com.example.campominadoextreme

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.example.campominadoextreme.ui.theme.CampoMinadoExtremeTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

private const val PREFS_NOME = "campo_minado_prefs"
private const val CHAVE_RANKING = "ranking_top10"
private const val CHAVE_JOGO = "jogo_salvo"
private const val CHAVE_CUSTOM_LARGURA = "custom_largura"
private const val CHAVE_CUSTOM_ALTURA = "custom_altura"
private const val CHAVE_CUSTOM_BOMBAS = "custom_bombas"
private const val CHAVE_CUSTOM_MODO_PORCENTAGEM = "custom_modo_porcentagem"

enum class Marca { NORMAL, BANDEIRA, DUVIDA }

data class NivelConfig(
    val nome: String,
    val descricao: String,
    val largura: Int,
    val altura: Int,
    val porcentagem: Int
)

val NIVEIS_PREDEFINIDOS = listOf(
    NivelConfig("Fácil", "10 × 10 — 15% de bombas", 10, 10, 15),
    NivelConfig("Médio", "16 × 16 — 18% de bombas", 16, 16, 18),
    NivelConfig("Difícil", "30 × 16 — 21% de bombas", 30, 16, 21)
)

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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EstadoJogo) return false
        if (nomeNivel != other.nomeNivel) return false
        if (largura != other.largura) return false
        if (altura != other.altura) return false
        if (totalMinas != other.totalMinas) return false
        if (!celulas.contentEquals(other.celulas)) return false
        if (reveladas != other.reveladas) return false
        if (marcas != other.marcas) return false
        if (jogadas != other.jogadas) return false
        if (segundos != other.segundos) return false
        if (gameOver != other.gameOver) return false
        if (vitoria != other.vitoria) return false
        if (partidaSalvaNoRanking != other.partidaSalvaNoRanking) return false
        return true
    }

    override fun hashCode(): Int {
        var res = nomeNivel.hashCode()
        res = 31 * res + largura
        res = 31 * res + altura
        res = 31 * res + totalMinas
        res = 31 * res + celulas.contentHashCode()
        res = 31 * res + reveladas.hashCode()
        res = 31 * res + marcas.hashCode()
        res = 31 * res + jogadas
        res = 31 * res + segundos
        res = 31 * res + gameOver.hashCode()
        res = 31 * res + vitoria.hashCode()
        res = 31 * res + partidaSalvaNoRanking.hashCode()
        return res
    }
}

data class RegistroPartida(
    val nome: String,
    val dataHora: String,
    val tempoSegundos: Int,
    val jogadas: Int,
    val pontuacao: Int,
    val nivel: String = "Fácil"
)

fun calcularQuantidadeMinas(largura: Int, altura: Int, porcentagem: Int): Int {
    val total = largura * altura
    val calculadas = (total * (porcentagem / 100.0)).roundToInt()
    return calculadas.coerceIn(1, (total - 1).coerceAtLeast(1))
}

fun criarEstadoJogo(
    nomeNivel: String,
    largura: Int,
    altura: Int,
    quantidadeMinas: Int
): EstadoJogo {
    val total = largura * altura
    val minasEfetivas = quantidadeMinas.coerceIn(1, (total - 1).coerceAtLeast(1))
    val celulas = IntArray(total)

    val indices = (0 until total).shuffled(Random.Default)
    for (i in 0 until minasEfetivas) {
        celulas[indices[i]] = -1
    }

    for (lin in 0 until altura) {
        for (col in 0 until largura) {
            val idx = lin * largura + col
            if (celulas[idx] == -1) continue

            var contagem = 0
            for (dl in -1..1) {
                for (dc in -1..1) {
                    if (dl == 0 && dc == 0) continue
                    val nl = lin + dl
                    val nc = col + dc
                    if (nl in 0 until altura && nc in 0 until largura) {
                        if (celulas[nl * largura + nc] == -1) {
                            contagem++
                        }
                    }
                }
            }
            celulas[idx] = contagem
        }
    }

    return EstadoJogo(
        nomeNivel = nomeNivel,
        largura = largura,
        altura = altura,
        totalMinas = minasEfetivas,
        celulas = celulas,
        reveladas = emptySet(),
        marcas = emptyMap(),
        jogadas = 0,
        segundos = 0,
        gameOver = false,
        vitoria = false,
        partidaSalvaNoRanking = false
    )
}

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

fun revelarCelula(estado: EstadoJogo, indice: Int): EstadoJogo {
    if (estado.gameOver || estado.vitoria) return estado
    if (indice in estado.reveladas) return estado
    if (estado.marcas[indice] == Marca.BANDEIRA) return estado

    val novasJogadas = estado.jogadas + 1
    val valor = estado.celulas[indice]

    if (valor == -1) {
        val todasMinas = estado.celulas.indices.filter { estado.celulas[it] == -1 }.toSet()
        return estado.copy(
            reveladas = estado.reveladas + todasMinas,
            jogadas = novasJogadas,
            gameOver = true
        )
    }

    val expandidas = if (valor == 0) {
        expandirZeros(indice, estado.celulas, estado.largura, estado.altura)
    } else {
        setOf(indice)
    }

    val novasReveladas = estado.reveladas + expandidas
    val novasMarcas = estado.marcas.filterKeys { it !in expandidas }

    val totalSeguras = estado.largura * estado.altura - estado.totalMinas
    val venceu = novasReveladas.size >= totalSeguras

    return estado.copy(
        reveladas = novasReveladas,
        marcas = novasMarcas,
        jogadas = novasJogadas,
        vitoria = venceu
    )
}

fun alternarMarca(estado: EstadoJogo, indice: Int): EstadoJogo {
    if (estado.gameOver || estado.vitoria) return estado
    if (indice in estado.reveladas) return estado

    val atual = estado.marcas[indice] ?: Marca.NORMAL
    val proxima = when (atual) {
        Marca.NORMAL -> Marca.BANDEIRA
        Marca.BANDEIRA -> Marca.DUVIDA
        Marca.DUVIDA -> Marca.NORMAL
    }

    val novasMarcas = estado.marcas.toMutableMap()
    if (proxima == Marca.NORMAL) {
        novasMarcas.remove(indice)
    } else {
        novasMarcas[indice] = proxima
    }

    return estado.copy(marcas = novasMarcas)
}

fun salvarPartidaEmAndamento(context: Context, estado: EstadoJogo?) {
    val prefs = context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
    if (estado == null) {
        prefs.edit(commit = false) { remove(CHAVE_JOGO) }
        return
    }

    val json = JSONObject()
    json.put("nomeNivel", estado.nomeNivel)
    json.put("largura", estado.largura)
    json.put("altura", estado.altura)
    json.put("totalMinas", estado.totalMinas)
    json.put("jogadas", estado.jogadas)
    json.put("segundos", estado.segundos)
    json.put("gameOver", estado.gameOver)
    json.put("vitoria", estado.vitoria)
    json.put("partidaSalvaNoRanking", estado.partidaSalvaNoRanking)

    val celulasArray = JSONArray()
    for (v in estado.celulas) {
        celulasArray.put(v)
    }
    json.put("celulas", celulasArray)

    val reveladasArray = JSONArray()
    for (r in estado.reveladas) {
        reveladasArray.put(r)
    }
    json.put("reveladas", reveladasArray)

    val marcasObj = JSONObject()
    for ((k, v) in estado.marcas) {
        marcasObj.put(k.toString(), v.name)
    }
    json.put("marcas", marcasObj)

    prefs.edit(commit = false) { putString(CHAVE_JOGO, json.toString()) }
}

fun carregarPartidaEmAndamento(context: Context): EstadoJogo? {
    val prefs = context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
    val str = prefs.getString(CHAVE_JOGO, null) ?: return null
    return try {
        val json = JSONObject(str)
        val nomeNivel = json.optString("nomeNivel", "Personalizado")
        val largura = json.getInt("largura")
        val altura = json.getInt("altura")
        val totalMinas = json.getInt("totalMinas")
        val jogadas = json.getInt("jogadas")
        val segundos = json.getInt("segundos")
        val gameOver = json.getBoolean("gameOver")
        val vitoria = json.getBoolean("vitoria")
        val salvaRanking = json.optBoolean("partidaSalvaNoRanking", false)

        val celulasArray = json.getJSONArray("celulas")
        val celulas = IntArray(celulasArray.length()) { celulasArray.getInt(it) }

        val reveladasArray = json.getJSONArray("reveladas")
        val reveladas = mutableSetOf<Int>()
        for (i in 0 until reveladasArray.length()) {
            reveladas.add(reveladasArray.getInt(i))
        }

        val marcas = mutableMapOf<Int, Marca>()
        val marcasObj = json.optJSONObject("marcas")
        if (marcasObj != null) {
            val keys = marcasObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                marcas[k.toInt()] = Marca.valueOf(marcasObj.getString(k))
            }
        }

        EstadoJogo(
            nomeNivel = nomeNivel,
            largura = largura,
            altura = altura,
            totalMinas = totalMinas,
            celulas = celulas,
            reveladas = reveladas,
            marcas = marcas,
            jogadas = jogadas,
            segundos = segundos,
            gameOver = gameOver,
            vitoria = vitoria,
            partidaSalvaNoRanking = salvaRanking
        )
    } catch (_: Exception) {
        null
    }
}

fun carregarRanking(context: Context): List<RegistroPartida> {
    val prefs = context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
    val jsonString = prefs.getString(CHAVE_RANKING, null) ?: return emptyList()
    val lista = mutableListOf<RegistroPartida>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            lista.add(
                RegistroPartida(
                    nome = obj.getString("nome"),
                    dataHora = obj.getString("dataHora"),
                    tempoSegundos = obj.getInt("tempoSegundos"),
                    jogadas = obj.getInt("jogadas"),
                    pontuacao = obj.getInt("pontuacao"),
                    nivel = obj.optString("nivel", "Fácil")
                )
            )
        }
    } catch (_: Exception) {
    }
    return lista.sortedByDescending { it.pontuacao }.take(10)
}

fun salvarPartidaNoRanking(context: Context, novo: RegistroPartida) {
    val atual = carregarRanking(context).toMutableList()
    atual.add(novo)
    val ordenado = atual.sortedByDescending { it.pontuacao }.take(10)
    val jsonArray = JSONArray()
    for (item in ordenado) {
        val obj = JSONObject()
        obj.put("nome", item.nome)
        obj.put("dataHora", item.dataHora)
        obj.put("tempoSegundos", item.tempoSegundos)
        obj.put("jogadas", item.jogadas)
        obj.put("pontuacao", item.pontuacao)
        obj.put("nivel", item.nivel)
        jsonArray.put(obj)
    }
    context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE)
        .edit(commit = false) { putString(CHAVE_RANKING, jsonArray.toString()) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CampoMinadoExtremeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CampoMinadoApp()
                }
            }
        }
    }
}

@Composable
fun CampoMinadoApp() {
    val context = LocalContext.current
    var estadoJogo by remember { mutableStateOf(carregarPartidaEmAndamento(context)) }
    var telaRankingAberta by remember { mutableStateOf(false) }
    var dialogoCustomAberto by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences(PREFS_NOME, Context.MODE_PRIVATE) }
    var customLargura by remember { mutableIntStateOf(prefs.getInt(CHAVE_CUSTOM_LARGURA, 12)) }
    var customAltura by remember { mutableIntStateOf(prefs.getInt(CHAVE_CUSTOM_ALTURA, 12)) }
    var customBombas by remember { mutableIntStateOf(prefs.getInt(CHAVE_CUSTOM_BOMBAS, 20)) }
    var customPorcentagem by remember { mutableStateOf(prefs.getBoolean(CHAVE_CUSTOM_MODO_PORCENTAGEM, true)) }

    LaunchedEffect(estadoJogo) {
        salvarPartidaEmAndamento(context, estadoJogo)
    }

    val jogoAtual = estadoJogo
    if (jogoAtual == null) {
        TelaSelecaoNivel(
            temPartidaSalva = false,
            onContinuar = {},
            onSelecionarNivel = { config ->
                val minas = calcularQuantidadeMinas(config.largura, config.altura, config.porcentagem)
                estadoJogo = criarEstadoJogo(config.nome, config.largura, config.altura, minas)
            },
            onAbrirPersonalizado = { dialogoCustomAberto = true },
            onAbrirRanking = { telaRankingAberta = true }
        )
    } else {
        TelaPartida(
            estado = jogoAtual,
            onAtualizarEstado = { estadoJogo = it },
            onVoltarAoMenu = { estadoJogo = null },
            onAbrirRanking = { telaRankingAberta = true }
        )
    }

    if (dialogoCustomAberto) {
        DialogoPersonalizado(
            larguraInicial = customLargura,
            alturaInicial = customAltura,
            bombasInicial = customBombas,
            modoPorcentagemInicial = customPorcentagem,
            onDismiss = { dialogoCustomAberto = false },
            onConfirmar = { l, a, b, porc ->
                customLargura = l
                customAltura = a
                customBombas = b
                customPorcentagem = porc
                prefs.edit(commit = false) {
                    putInt(CHAVE_CUSTOM_LARGURA, l)
                    putInt(CHAVE_CUSTOM_ALTURA, a)
                    putInt(CHAVE_CUSTOM_BOMBAS, b)
                    putBoolean(CHAVE_CUSTOM_MODO_PORCENTAGEM, porc)
                }
                val minasFinais = if (porc) {
                    calcularQuantidadeMinas(l, a, b)
                } else {
                    b.coerceIn(1, (l * a - 1).coerceAtLeast(1))
                }
                estadoJogo = criarEstadoJogo("Personalizado", l, a, minasFinais)
                dialogoCustomAberto = false
            }
        )
    }

    if (telaRankingAberta) {
        DialogoRanking(
            context = context,
            onFechar = { telaRankingAberta = false }
        )
    }
}

@Composable
fun TelaSelecaoNivel(
    temPartidaSalva: Boolean,
    onContinuar: () -> Unit,
    onSelecionarNivel: (NivelConfig) -> Unit,
    onAbrirPersonalizado: () -> Unit,
    onAbrirRanking: () -> Unit
) {
    var horaAtual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatador = DateTimeFormatter.ofPattern("HH:mm:ss")
        while (true) {
            horaAtual = LocalTime.now().format(formatador)
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🕒 $horaAtual",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "💣 Campo Minado Extreme",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Escolha a dificuldade da partida",
            fontSize = 14.sp,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (temPartidaSalva) {
            Button(
                onClick = onContinuar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("▶ Continuar Partida Salva")
            }
        }

        for (nivel in NIVEIS_PREDEFINIDOS) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Button(
                    onClick = { onSelecionarNivel(nivel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardDefaults.shape
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = nivel.nome,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = nivel.descricao,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onAbrirPersonalizado,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text("⚙ Nível Personalizado", fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onAbrirRanking) {
            Text("🏆 Ver Melhores Pontuações (Ranking)", fontSize = 14.sp)
        }
    }
}

@Composable
fun DialogoPersonalizado(
    larguraInicial: Int,
    alturaInicial: Int,
    bombasInicial: Int,
    modoPorcentagemInicial: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (largura: Int, altura: Int, bombas: Int, modoPorcentagem: Boolean) -> Unit
) {
    var larguraTexto by remember { mutableStateOf(larguraInicial.toString()) }
    var alturaTexto by remember { mutableStateOf(alturaInicial.toString()) }
    var bombasTexto by remember { mutableStateOf(bombasInicial.toString()) }
    var modoPorcentagem by remember { mutableStateOf(modoPorcentagemInicial) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Tabuleiro Personalizado") },
        text = {
            Column {
                OutlinedTextField(
                    value = larguraTexto,
                    onValueChange = { larguraTexto = it.filter { c -> c.isDigit() } },
                    label = { Text("Largura (colunas: 5 a 40)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = alturaTexto,
                    onValueChange = { alturaTexto = it.filter { c -> c.isDigit() } },
                    label = { Text("Altura (linhas: 5 a 30)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (modoPorcentagem) "Tipo: Porcentagem (%)" else "Tipo: Quantidade Fixa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { modoPorcentagem = !modoPorcentagem }) {
                        Text(if (modoPorcentagem) "Mudar p/ Qtd" else "Mudar p/ %")
                    }
                }

                OutlinedTextField(
                    value = bombasTexto,
                    onValueChange = { bombasTexto = it.filter { c -> c.isDigit() } },
                    label = {
                        Text(if (modoPorcentagem) "Porcentagem de Bombas (5% a 80%)" else "Quantidade de Bombas")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (mensagemErro != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = mensagemErro ?: "",
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val larg = larguraTexto.toIntOrNull()
                    val alt = alturaTexto.toIntOrNull()
                    val bmb = bombasTexto.toIntOrNull()

                    if (larg == null || alt == null || bmb == null) {
                        mensagemErro = "Preencha todos os campos com números válidos."
                        return@Button
                    }
                    if (larg !in 5..40) {
                        mensagemErro = "Largura deve ser entre 5 e 40."
                        return@Button
                    }
                    if (alt !in 5..30) {
                        mensagemErro = "Altura deve ser entre 5 e 30."
                        return@Button
                    }
                    val total = larg * alt
                    if (modoPorcentagem) {
                        if (bmb !in 5..80) {
                            mensagemErro = "Porcentagem deve ser entre 5% e 80%."
                            return@Button
                        }
                    } else {
                        if (bmb !in 1 until total) {
                            mensagemErro = "Quantidade de bombas deve ser entre 1 e ${total - 1}."
                            return@Button
                        }
                    }
                    mensagemErro = null
                    onConfirmar(larg, alt, bmb, modoPorcentagem)
                }
            ) {
                Text("Iniciar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun TelaPartida(
    estado: EstadoJogo,
    onAtualizarEstado: (EstadoJogo) -> Unit,
    onVoltarAoMenu: () -> Unit,
    onAbrirRanking: () -> Unit
) {
    val context = LocalContext.current
    var modoMarcacao by remember { mutableStateOf(false) }
    var mostrarDialogVitoria by remember { mutableStateOf(false) }
    var nomeJogador by remember { mutableStateOf("") }

    var horaAtual by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val formatador = DateTimeFormatter.ofPattern("HH:mm:ss")
        while (true) {
            horaAtual = LocalTime.now().format(formatador)
            delay(1000L)
        }
    }

    LaunchedEffect(estado.gameOver, estado.vitoria) {
        if (!estado.gameOver && !estado.vitoria) {
            while (true) {
                delay(1000L)
                onAtualizarEstado(estado.copy(segundos = estado.segundos + 1))
            }
        }
    }

    LaunchedEffect(estado.vitoria) {
        if (estado.vitoria && !estado.partidaSalvaNoRanking) {
            mostrarDialogVitoria = true
        }
    }

    val minutos = estado.segundos / 60
    val segs = estado.segundos % 60
    val tempoFormatado = String.format(Locale.US, "%02d:%02d", minutos, segs)

    val bandeirasColocadas = estado.marcas.values.count { it == Marca.BANDEIRA }
    val bombasRestantes = estado.totalMinas - bandeirasColocadas

    val tituloStatus = when {
        estado.gameOver -> "💥 BOOM! Você Perdeu"
        estado.vitoria -> "🎉 VITÓRIA! Parabéns!"
        else -> "${estado.nomeNivel} (${estado.largura}×${estado.altura})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🕒 $horaAtual", fontSize = 12.sp, color = Color.Gray)
            TextButton(onClick = onVoltarAoMenu) {
                Text("🏠 Menu", fontSize = 13.sp)
            }
            TextButton(onClick = onAbrirRanking) {
                Text("🏆 Ranking", fontSize = 13.sp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎮 Jogadas: ${estado.jogadas}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("⏱️ $tempoFormatado", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("💣 Restam: $bombasRestantes", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = tituloStatus,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = when {
                estado.gameOver -> Color(0xFFC62828)
                estado.vitoria -> Color(0xFF2E7D32)
                else -> Color.Black
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { modoMarcacao = !modoMarcacao },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (modoMarcacao) Color(0xFFEF6C00) else Color(0xFF1976D2)
                )
            ) {
                Text(
                    text = if (modoMarcacao) "Modo: 🚩 MARCAR" else "Modo: 👆 REVELAR",
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = {
                    val novo = criarEstadoJogo(
                        estado.nomeNivel,
                        estado.largura,
                        estado.altura,
                        estado.totalMinas
                    )
                    onAtualizarEstado(novo)
                }
            ) {
                Text("🔄 Reiniciar", fontSize = 12.sp)
            }
        }

        Text(
            text = "Toque rápido revela (ou marca no modo 🚩). Toque longo alterna 🚩/?",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val tamanhoCalculado: Dp = (maxWidth / estado.largura).coerceIn(16.dp, 36.dp)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center
            ) {
                Column {
                    for (lin in 0 until estado.altura) {
                        Row {
                            for (col in 0 until estado.largura) {
                                val idx = lin * estado.largura + col
                                val isRevelada = estado.reveladas.contains(idx)
                                val marca = estado.marcas[idx] ?: Marca.NORMAL
                                val valor = estado.celulas[idx]

                                CelulaItem(
                                    tamanho = tamanhoCalculado,
                                    revelada = isRevelada,
                                    valor = valor,
                                    marca = marca,
                                    gameOver = estado.gameOver,
                                    onClick = {
                                        if (modoMarcacao) {
                                            onAtualizarEstado(alternarMarca(estado, idx))
                                        } else {
                                            onAtualizarEstado(revelarCelula(estado, idx))
                                        }
                                    },
                                    onLongClick = {
                                        onAtualizarEstado(alternarMarca(estado, idx))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (estado.gameOver || estado.vitoria) {
            Button(
                onClick = {
                    val novo = criarEstadoJogo(
                        estado.nomeNivel,
                        estado.largura,
                        estado.altura,
                        estado.totalMinas
                    )
                    onAtualizarEstado(novo)
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            ) {
                Text("Jogar Novamente")
            }
        }
    }

    if (mostrarDialogVitoria && !estado.partidaSalvaNoRanking) {
        val pontuacao = maxOf(10, 10000 - (estado.segundos * 10 + estado.jogadas * 20))
        AlertDialog(
            onDismissRequest = { mostrarDialogVitoria = false },
            title = { Text("🎉 Parabéns! Você Venceu!") },
            text = {
                Column {
                    Text("Nível: ${estado.nomeNivel}")
                    Text("Tempo: $tempoFormatado | Jogadas: ${estado.jogadas}")
                    Text("Pontuação: $pontuacao pts", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nomeJogador,
                        onValueChange = { nomeJogador = it },
                        label = { Text("Nome do Jogador") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nomeFinal = if (nomeJogador.isNotBlank()) nomeJogador.trim() else "Jogador"
                        val dataHoraAtual = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        salvarPartidaNoRanking(
                            context,
                            RegistroPartida(
                                nome = nomeFinal,
                                dataHora = dataHoraAtual,
                                tempoSegundos = estado.segundos,
                                jogadas = estado.jogadas,
                                pontuacao = pontuacao,
                                nivel = estado.nomeNivel
                            )
                        )
                        onAtualizarEstado(estado.copy(partidaSalvaNoRanking = true))
                        mostrarDialogVitoria = false
                        onAbrirRanking()
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogVitoria = false }) {
                    Text("Pular")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CelulaItem(
    tamanho: Dp,
    revelada: Boolean,
    valor: Int,
    marca: Marca,
    gameOver: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val corFundo = when {
        !revelada && marca == Marca.BANDEIRA -> Color(0xFFFFCC80)
        !revelada && marca == Marca.DUVIDA -> Color(0xFFE1BEE7)
        !revelada -> Color(0xFF90CAF9)
        valor == -1 -> Color(0xFFEF5350)
        valor == 0 -> Color(0xFFA5D6A7)
        else -> Color(0xFF1976D2)
    }

    val texto = when {
        !revelada && marca == Marca.BANDEIRA -> "🚩"
        !revelada && marca == Marca.DUVIDA -> "?"
        !revelada -> ""
        valor == -1 -> "💣"
        valor == 0 -> ""
        else -> valor.toString()
    }

    val corTexto = when {
        !revelada -> Color.Black
        valor == -1 -> Color.White
        valor == 0 -> Color.Transparent
        valor == 1 -> Color(0xFF0D47A1)
        valor == 2 -> Color(0xFF1B5E20)
        valor == 3 -> Color(0xFFB71C1C)
        valor == 4 -> Color(0xFF4A148C)
        else -> Color.White
    }

    val tamanhoFonte = (tamanho.value * 0.45f).coerceIn(9f, 16f).sp

    Box(
        modifier = Modifier
            .size(tamanho)
            .padding(1.dp)
            .border(0.5.dp, Color(0xFF546E7A))
            .background(corFundo)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (texto.isNotEmpty()) {
            Text(
                text = texto,
                fontSize = tamanhoFonte,
                fontWeight = FontWeight.Bold,
                color = corTexto
            )
        }
    }
}

@Composable
fun DialogoRanking(
    context: Context,
    onFechar: () -> Unit
) {
    val ranking = remember { carregarRanking(context) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("🏆 Top 10 Melhores Partidas") },
        text = {
            if (ranking.isEmpty()) {
                Text("Nenhuma vitória registrada ainda!")
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(ranking) { index, item ->
                        val m = item.tempoSegundos / 60
                        val s = item.tempoSegundos % 60
                        val tFormat = String.format(Locale.US, "%02d:%02d", m, s)
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${index + 1}º ${item.nome}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${item.pontuacao} pts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1565C0)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "[${item.nivel}] Tempo: $tFormat | Jogadas: ${item.jogadas}",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = item.dataHora,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onFechar) {
                Text("Fechar")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun CampoMinadoPreview() {
    CampoMinadoExtremeTheme {
        CampoMinadoApp()
    }
}
