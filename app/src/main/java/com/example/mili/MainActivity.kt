package com.example.mili

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Agregado para compatibilidad
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.mili.ui.theme.MiliTheme
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private object Constants {
    const val DATASTORE_NAME = "publications"
    const val PUBLICATIONS_KEY = "publications_list"
    const val USERS_KEY = "users_data"
    const val IMAGE_HEIGHT = 200
    const val DEFAULT_PADDING = 16
    const val MEDIUM_SPACING = 16
    const val LARGE_SPACING = 23
    const val SMALL_SPACING = 8
}

// Inicialización del DataStore (Memoria de la app)
private val Context.dataStore by preferencesDataStore(Constants.DATASTORE_NAME)

// ================= MODELOS DE DATOS (NUEVOS) =================
@Serializable
data class Publication(
    val titulo: String,
    val descripcion: String,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val isLiked: Boolean = false
)

@Serializable
data class User(
    val id: Int,
    val nombre: String,
    val edad: Int,
    val profesion: String,
    val descripcion: String,
    val imagenUrl: String? = null,
    val intereses: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SearchResult(
    val publications: List<Publication>,
    val users: List<User>
)

// ================= REPOSITORIOS (LÓGICA) =================
class PublicationRepository(private val context: Context) {
    private val publicationsKey = stringPreferencesKey(Constants.PUBLICATIONS_KEY)

    suspend fun savePublication(publication: Publication) {
        val currentList = getAllPublicationsList()
        val updatedList = listOf(publication) + currentList // Nuevo al principio
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    suspend fun updatePublicationLike(publicationId: Long, isLiked: Boolean) {
        val currentList = getAllPublicationsList()
        val updatedList = currentList.map { publication ->
            if (publication.timestamp == publicationId) {
                publication.copy(
                    likes = if (isLiked) publication.likes + 1 else maxOf(0, publication.likes - 1),
                    isLiked = isLiked
                )
            } else {
                publication
            }
        }
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    suspend fun deletePublication(publicationToDelete: Publication) {
        val currentList = getAllPublicationsList()
        val updatedList = currentList.filterNot { it.timestamp == publicationToDelete.timestamp }
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    fun getAllPublications(): Flow<List<Publication>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[publicationsKey] ?: "[]"
            try {
                Json.decodeFromString<List<Publication>>(jsonString)
            } catch (e: Exception) { emptyList() }
        }
    }

    private suspend fun getAllPublicationsList(): List<Publication> {
        val preferences = context.dataStore.data.first()
        val jsonString = preferences[publicationsKey] ?: "[]"
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) { emptyList() }
    }

    suspend fun searchAllContent(query: String, publications: List<Publication>): SearchResult {
        val userRepository = UserRepository(context)
        val filteredPubs = if (query.isBlank()) publications else publications.filter {
            it.titulo.contains(query, ignoreCase = true) || it.descripcion.contains(query, ignoreCase = true)
        }
        val filteredUsers = userRepository.searchUsers(query)
        return SearchResult(filteredPubs, filteredUsers)
    }
}

class UserRepository(private val context: Context) {
    // Lista de usuarios "quemados" para que la búsqueda no esté vacía
    private val defaultUsers = listOf(
        User(1, "María González", 25, "Diseñadora UX", "Amante del diseño.", intereses = listOf("Diseño", "Arte")),
        User(2, "Carlos Rodríguez", 32, "Dev Android", "Código y café.", intereses = listOf("Programación", "Kotlin")),
        User(3, "Ana López", 28, "Ingeniera", "Backend expert.", intereses = listOf("Java", "Sistemas"))
    )
    suspend fun searchUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        return defaultUsers.filter { user ->
            user.nombre.contains(query, ignoreCase = true) || user.profesion.contains(query, ignoreCase = true)
        }
    }
    suspend fun getUserById(id: Int): User? = defaultUsers.find { it.id == id }
}

class ImageManager(private val context: Context) {
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "publication_images")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "publication_${timeStamp}.jpg")
    }
    fun getPersistentUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    fun getDisplayableUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }

    fun copyUriToPersistentFile(temporaryUri: Uri): Uri? {
        return try {
            val persistentFile = createImageFile()
            context.contentResolver.openInputStream(temporaryUri)?.use { input ->
                persistentFile.outputStream().use { output -> input.copyTo(output) }
            }
            getPersistentUri(persistentFile)
        } catch (e: Exception) { null }
    }
}

// Utilidad de fecha
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiliTheme {
                AppNavegacion()
            }
        }
    }
}

@Composable
fun AppNavegacion() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "inicio"
    ) {
        // 1. EL INICIO AHORA ES EL FEED REAL (PantallaPublicaciones)
        composable("inicio") {
            PantallaPublicaciones(navController)
        }

        // 2. PANTALLAS REALES (Ya no usamos PantallaBlanca)
        composable("busqueda") {
            SearchScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("nueva_post") {
            CreateScreen(
                navController = navController, // Pasamos el controller
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 3. PANTALLAS SECUNDARIAS
        composable("perfil") {
            SimpleProfileScreen(onBack = { navController.popBackStack() })
        }
        composable("notificaciones") {
            NotificationScreenStyle(onBack = { navController.popBackStack() })
        }
        composable("configuracion") { ConfiguracionScreen(navController) }
        composable("contactanos") { ContactanosScreen(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(navController: NavController) {
    // Nota: Estos estados son locales. Al salir de la pantalla se reinician.
    // Para que persistan en toda la app, necesitaríamos una base de datos o ViewModel.
    var notificationsEnabled by remember { mutableStateOf(true) }
    var isDarkMode by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("Español") }

    // Este tema solo aplica a ESTA pantalla (efecto visual local)
    val currentTheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = currentTheme) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Configuración") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, "Volver")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        // Puedes ajustar estos colores a tu gusto o usar los del tema
                        containerColor = Color.LightGray.copy(alpha = 0.5f)
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.background) // Se adapta al modo oscuro/claro
            ) {

                // NOTIFICACIONES
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Notificaciones", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }

                // MODO OSCURO
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Modo Oscuro", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isDarkMode, onCheckedChange = { isDarkMode = it })
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // IDIOMA
                Text("Idioma", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))

                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = selectedLanguage,
                        onValueChange = { },
                        label = { Text("Seleccionar idioma") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Español", "English").forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang) },
                                onClick = {
                                    selectedLanguage = lang
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))


                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ==================== PANTALLA CONTÁCTANOS ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactanosScreen(navController: NavController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contáctanos", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.LightGray.copy(alpha = 0.5f)
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .background(Color.White),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContactItem(
                icon = Icons.Filled.Phone,
                text = "01 (238) 688 31 32",
                onClick = { /* Acción al llamar */ }
            )
            ContactItem(
                icon = Icons.Filled.CheckCircle,
                text = "01 (238) 104 80 04",
                onClick = { }
            )
            ContactItem(
                icon = Icons.Filled.LocationOn,
                text = "Reforma Norte #444 Col. Centro\nC.P. 75700",
                onClick = { }
            )
            ContactItem(
                icon = Icons.Filled.Email,
                text = "admisiones@unimilenium.edu.mx",
                onClick = { }
            )
            ContactItem(
                icon = Icons.Filled.Face,
                text = "Centro Universitario Milenium",
                onClick = { }
            )
            ContactItem(
                icon = Icons.Filled.Clear,
                text = "unimilenium",
                onClick = { }
            )
        }
    }
}

@Composable
fun ContactItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.DarkGray
        )
    }
}

// ==================== PANTALLA DE PERFIL (CORREGIDA) ====================
@Composable
fun SimpleProfileScreen(onBack: () -> Unit) {
    // 1. Adaptamos los datos falsos a la NUEVA estructura (titulo, descripcion...)
    val publications = remember {
        mutableStateListOf(
            Publication(
                titulo = "Un día increíble", // Usamos Título
                descripcion = "Buen trabajo Pedro! Sigue trabajando en eso!", // Usamos Descripción
                timestamp = System.currentTimeMillis() - 7200000 // Hace 2 horas aprox
            ),
            Publication(
                titulo = "Repost",
                descripcion = "Texto de ejemplo xd",
                timestamp = System.currentTimeMillis() - 10800000 // Hace 3 horas aprox
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabecera del Perfil (Se mantiene igual visualmente)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Atrás", tint = Color.Black)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rafael", fontWeight = FontWeight.Bold, fontSize = 32.sp, color = Color.Black)
                            Text("Pantera Osorio", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
                            Text(
                                "Ing. Sistemas Informáticos e\nInteligencia Artificial",
                                fontSize = 14.sp,
                                color = Color.DarkGray,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 16.dp)) {
                            Surface(modifier = Modifier.size(100.dp), shape = CircleShape, color = Color(0xFFEEEEEE), border = BorderStroke(2.dp, Color.LightGray)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
                                }
                            }
                            Text("2", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Posts", fontSize = 12.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("BIOGRAFIA", Modifier.align(Alignment.CenterHorizontally), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color.LightGray, thickness = 1.dp)
            }
        }

        // 2. Usamos la tarjeta NUEVA (PublicationCardModern) que ya está adaptada
        items(publications) { publication ->
            PublicationCardModern(
                publication = publication,
                onDelete = { publications.remove(publication) },
                onLike = { liked ->
                    val index = publications.indexOf(publication)
                    if (index != -1) {
                        publications[index] = publication.copy(isLiked = liked)
                    }
                }
            )
        }
    }
}

// =======================================
// ========= TARJETA UNIVERSAL ===========
// =======================================
@Composable
fun PublicationCardModern(
    publication: Publication,
    onDelete: () -> Unit,
    onLike: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val imageManager = remember { ImageManager(context) }

    // Convertimos la ruta de la imagen guardada en algo que se pueda ver
    val displayUri = remember(publication.imageUri) {
        imageManager.getDisplayableUri(publication.imageUri)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Cabecera (Usuario ficticio + Fecha)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("U", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Usuario", fontWeight = FontWeight.Bold)
                    Text(formatDate(publication.timestamp), fontSize = 12.sp, color = Color.Gray)
                }
                // Menú de borrar
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Borrar", tint = Color.Gray)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Título y Descripción (Lo nuevo)
            Text(publication.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(publication.descripcion, fontSize = 14.sp)

            // Imagen (Si existe)
            displayUri?.let {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Botón de Like
            Row(Modifier.padding(top = 16.dp)) {
                Icon(
                    if (publication.isLiked) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                    contentDescription = "Like",
                    tint = if (publication.isLiked) Color.Red else Color.Gray,
                    modifier = Modifier.clickable { onLike(!publication.isLiked) }
                )
                Spacer(Modifier.width(4.dp))
                Text("${publication.likes}")
            }
        }
    }
}

// ==================== PANTALLA DE NOTIFICACIONES ====================
@Composable
fun NotificationScreenStyle(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
            }

            Text(
                text = "Notificaciones",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Color.Black)
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(3) { index ->
                NotificationItemStyle(index)
            }
        }
    }
}

@Composable
fun NotificationItemStyle(index: Int) {
    val names = listOf("Pedro", "Pedro", "Willmar")
    val actions = listOf("Le dio me gusta", "Le dio me gusta", "@ Te mencionó")
    val times = listOf("5 mins", "5 mins", "1 hr")
    val isLike = index < 2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            modifier = Modifier.size(50.dp),
            tint = Color.LightGray
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = names.getOrElse(index) { "Usuario" },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLike) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF5F4776),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = actions.getOrElse(index) { "" },
                    color = Color.DarkGray,
                    fontSize = 14.sp
                )
            }
        }

        Text(
            text = times.getOrElse(index) { "" },
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
    Divider(color = Color(0xFFF5F5F5), thickness = 1.dp)
}
// =======================================
// ========= PANTALLA FEED (PRINCIPAL) ===
// =======================================
// =======================================
// ========= PANTALLA FEED (CORREGIDA) ===
// =======================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPublicaciones(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { PublicationRepository(context) }
    val publications by repository.getAllPublications().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Menú", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                HorizontalDivider()

                NavigationDrawerItem(label = { Text("Inicio") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate("inicio") })
                NavigationDrawerItem(label = { Text("Perfil") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate("perfil") })
                NavigationDrawerItem(label = { Text("Notificaciones") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate("notificaciones") })
                NavigationDrawerItem(label = { Text("Configuración") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate("configuracion") })
                NavigationDrawerItem(label = { Text("Contáctanos") }, selected = false, onClick = { scope.launch { drawerState.close() }; navController.navigate("contactanos") })
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    // 1. RECUPERAMOS TU LOGO (IMAGEN)
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.logo2),
                            contentDescription = "Logo de la app",
                            modifier = Modifier
                                .size(60.dp)
                                .clickable { navController.navigate("inicio") }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "Menú")
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("busqueda") }) {
                            Icon(Icons.Filled.Search, "Buscar")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navController.navigate("nueva_post") }) {
                    Icon(Icons.Filled.Add, "Crear")
                }
            },
            // 2. RECUPERAMOS LA POSICIÓN CENTRAL DEL BOTÓN
            floatingActionButtonPosition = FabPosition.Center
        ) { paddingValues ->
            if (publications.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No hay publicaciones aún.\n¡Sé el primero!", textAlign = TextAlign.Center, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(publications) { publication ->
                        PublicationCardModern(
                            publication = publication,
                            onDelete = { scope.launch { repository.deletePublication(publication) } },
                            onLike = { liked -> scope.launch { repository.updatePublicationLike(publication.timestamp, liked) } }
                        )
                    }
                }
            }
        }
    }
}

// =======================================
// ========= PANTALLA CREAR POST =========
// =======================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(navController: NavController, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PublicationRepository(context) }
    val imageManager = remember { ImageManager(context) }
    val scope = rememberCoroutineScope()

    var titulo by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // Configuración para abrir la galería
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) imageUri = imageManager.copyUriToPersistentFile(uri)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nueva Publicación") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("¿Qué estás pensando?") },
                modifier = Modifier.fillMaxWidth().height(100.dp)
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar Imagen")
            }

            imageUri?.let { uri ->
                Spacer(Modifier.height(16.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        repository.savePublication(Publication(titulo, descripcion, imageUri?.toString()))
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = titulo.isNotBlank() && descripcion.isNotBlank()
            ) {
                Text("PUBLICAR")
            }
        }
    }
}

// =======================================
// ========= PANTALLA BÚSQUEDA ===========
// =======================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PublicationRepository(context) }
    val publications by repository.getAllPublications().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(SearchResult(emptyList(), emptyList())) }

    // Cada vez que escribes, busca automáticamente
    LaunchedEffect(query) {
        result = repository.searchAllContent(query, publications)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Buscar") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar usuarios o posts...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Search, null) }
            )

            LazyColumn(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (result.users.isNotEmpty()) {
                    item { Text("Usuarios encontrados", fontWeight = FontWeight.Bold) }
                    items(result.users) { user ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = Color.Gray, modifier = Modifier.size(40.dp)) {}
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(user.nombre, fontWeight = FontWeight.Bold)
                                    Text(user.profesion, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (result.publications.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)); Text("Publicaciones encontradas", fontWeight = FontWeight.Bold) }
                    items(result.publications) { pub ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(pub.titulo, fontWeight = FontWeight.Bold)
                                Text(pub.descripcion, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}