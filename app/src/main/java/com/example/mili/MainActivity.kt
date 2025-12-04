package com.example.mili

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ================= CONSTANTES Y COLORES =================
private object Constants {
    const val DATASTORE_NAME = "milenium_db"
    const val PUBLICATIONS_KEY = "publications_list"
    const val DARK_MODE_KEY = "dark_mode_enabled"
}

// Tus colores personalizados
val PurplePrimary = Color(0xFF5F4776)
val PurpleDark = Color(0xFF3B2C4A)
val PurpleLight = Color(0xFFD5C3FB)
val Whiteish = Color(0xFFFDFDFD)

// Inicialización del DataStore
private val Context.dataStore by preferencesDataStore(Constants.DATASTORE_NAME)

// ================= MODELOS DE DATOS =================
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
    val profesion: String,
    val intereses: List<String> = emptyList()
)

@Serializable
data class SearchResult(
    val publications: List<Publication>,
    val users: List<User>
)

// ================= REPOSITORIOS =================

// 1. Repositorio de Configuración (Modo Oscuro Global)
class SettingsRepository(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey(Constants.DARK_MODE_KEY)

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[darkModeKey] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[darkModeKey] = enabled
        }
    }
}

// 2. Repositorio de Publicaciones
class PublicationRepository(private val context: Context) {
    private val publicationsKey = stringPreferencesKey(Constants.PUBLICATIONS_KEY)

    suspend fun savePublication(publication: Publication) {
        val currentList = getAllPublicationsList()
        val updatedList = listOf(publication) + currentList
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    suspend fun updatePublicationLike(timestamp: Long, isLiked: Boolean) {
        val currentList = getAllPublicationsList()
        val updatedList = currentList.map {
            if (it.timestamp == timestamp) it.copy(
                likes = if (isLiked) it.likes + 1 else maxOf(0, it.likes - 1),
                isLiked = isLiked
            ) else it
        }
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    suspend fun deletePublication(publication: Publication) {
        val currentList = getAllPublicationsList()
        val updatedList = currentList.filterNot { it.timestamp == publication.timestamp }
        context.dataStore.edit { preferences ->
            preferences[publicationsKey] = Json.encodeToString(updatedList)
        }
    }

    fun getAllPublications(): Flow<List<Publication>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[publicationsKey] ?: "[]"
            try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
        }
    }

    private suspend fun getAllPublicationsList(): List<Publication> {
        val preferences = context.dataStore.data.first()
        val json = preferences[publicationsKey] ?: "[]"
        return try { Json.decodeFromString(json) } catch (e: Exception) { emptyList() }
    }

    suspend fun searchAllContent(query: String, publications: List<Publication>): SearchResult {
        val userRepository = UserRepository()
        val filteredPubs = if (query.isBlank()) publications else publications.filter {
            it.titulo.contains(query, ignoreCase = true) || it.descripcion.contains(query, ignoreCase = true)
        }
        val filteredUsers = userRepository.searchUsers(query)
        return SearchResult(filteredPubs, filteredUsers)
    }
}

// 3. Repositorio de Usuarios (Falsos)
class UserRepository {
    private val defaultUsers = listOf(
        User(1, "María González", "Diseñadora UX", listOf("Diseño", "Arte")),
        User(2, "Carlos Rodríguez", "Dev Android", listOf("Programación", "Kotlin")),
        User(3, "Ana López", "Ingeniera", listOf("Java", "Sistemas"))
    )
    fun searchUsers(query: String): List<User> {
        if (query.isBlank()) return emptyList()
        return defaultUsers.filter {
            it.nombre.contains(query, ignoreCase = true) || it.profesion.contains(query, ignoreCase = true)
        }
    }
}

// 4. Gestor de Imágenes (Cámara y Galería)
class ImageManager(private val context: Context) {
    // Crea un archivo temporal vacío para que la cámara guarde la foto ahí
    fun createTempImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(context.filesDir, "publication_images")
        if (!storageDir.exists()) storageDir.mkdirs()
        return File(storageDir, "IMG_${timeStamp}.jpg")
    }

    // Obtiene la URI segura para la cámara (requiere FileProvider en Manifest)
    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Copia la imagen de la galería a nuestra carpeta privada (corrige el error de que no se adjuntaba)
    suspend fun copyUriToPersistentFile(sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            val destFile = createTempImageFile()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            getUriForFile(destFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getDisplayableUri(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }
}

// ================= MAIN ACTIVITY Y TEMA GLOBAL =================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Leemos la configuración global del modo oscuro
            val settingsRepo = remember { SettingsRepository(applicationContext) }
            val isDarkMode by settingsRepo.isDarkMode.collectAsState(initial = false)

            // Definimos el tema basado en tu paleta de colores
            val colorScheme = if (isDarkMode) {
                darkColorScheme(
                    primary = PurpleLight,
                    onPrimary = PurpleDark,
                    secondary = PurplePrimary,
                    background = PurpleDark,
                    surface = Color(0xFF2D2238), // Un poco más claro que el fondo
                    onSurface = Whiteish
                )
            } else {
                lightColorScheme(
                    primary = PurplePrimary,
                    onPrimary = Color.White,
                    secondary = PurpleLight,
                    background = Color(0xFFF5F0FF), // Lila muy clarito de fondo
                    surface = Color.White,
                    onSurface = PurpleDark
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                // Pasamos el repositorio de settings para poder cambiar el tema desde Configuración
                AppNavegacion(settingsRepo)
            }
        }
    }
}

// ================= NAVEGACIÓN PRINCIPAL =================
@Composable
fun AppNavegacion(settingsRepo: SettingsRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "inicio") {
        composable("inicio") { PantallaPublicaciones(navController) }

        composable("busqueda") {
            SearchScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("nueva_post") {
            CreateScreen(navController = navController, onNavigateBack = { navController.popBackStack() })
        }

        composable("perfil") { SimpleProfileScreen(onBack = { navController.popBackStack() }) }
        composable("notificaciones") { NotificationScreenStyle(onBack = { navController.popBackStack() }) }

        // Pasamos el repositorio a configuración para que el switch funcione
        composable("configuracion") { ConfiguracionScreen(navController, settingsRepo) }

        composable("contactanos") { ContactanosScreen(navController) }
    }
}

// ================= PANTALLA FEED (INICIO) =================
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
                // Header del Drawer con color morado
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp),
                        contentAlignment = Alignment.Center

                )

                {
                    Text("Menú", color = PurplePrimary, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Spacer(Modifier.height(20.dp))
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
                    title = {
                        // Tu logo recuperado
                        Image(
                            painter = painterResource(id = R.drawable.logo2),
                            contentDescription = "Logo",
                            modifier = Modifier.size(50.dp).clickable { navController.navigate("inicio") }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, "Menú", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { navController.navigate("busqueda") }) {
                            Icon(Icons.Filled.Search, "Buscar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate("nueva_post") },
                    containerColor = PurplePrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, "Crear")
                }
            },
            floatingActionButtonPosition = FabPosition.Center
        ) { padding ->
            if (publications.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No hay publicaciones.\n¡Sé el primero!", textAlign = TextAlign.Center, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp)
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

// ================= PANTALLA CREAR POST (CÁMARA + GALERÍA) =================
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

    // Estado para guardar la URI temporal de la foto que vamos a tomar
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 1. Launcher para Galería
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val savedUri = imageManager.copyUriToPersistentFile(uri)
                imageUri = savedUri // Actualizamos la imagen a mostrar
            }
        }
    }

    // 2. Launcher para Cámara
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            // Si la foto se tomó bien, usamos la URI temporal que creamos antes
            imageUri = tempCameraUri
        }
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
                label = { Text("Cuéntanos algo...") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Botones de Foto
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleLight, contentColor = PurpleDark)
                ) {
                    Icon(Icons.Filled.Menu, null) // Icono genérico de galería
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }

                Button(
                    onClick = {
                        // Creamos un archivo temporal y lanzamos la cámara
                        val tempFile = imageManager.createTempImageFile()
                        val uri = imageManager.getUriForFile(tempFile)
                        tempCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleLight)
                ) {
                    Icon(Icons.Filled.AddCircle, null) // Icono genérico de cámara
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara")
                }
            }

            imageUri?.let { uri ->
                Spacer(Modifier.height(16.dp))
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, PurplePrimary, RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(35.dp))

            Button(
                onClick = {
                    scope.launch {
                        repository.savePublication(Publication(titulo, descripcion, imageUri?.toString()))
                        Toast.makeText(context, "Publicado con éxito", Toast.LENGTH_SHORT).show()
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                enabled = titulo.isNotBlank() && descripcion.isNotBlank()
            ) {
                Text("PUBLICAR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ================= PANTALLA CONFIGURACIÓN (CON MODO OSCURO GLOBAL) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracionScreen(navController: NavController, settingsRepo: SettingsRepository) {
    // Leemos el estado global
    val isDarkMode by settingsRepo.isDarkMode.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    var notificationsEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Volver") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
        ) {
            // Switch Modo Oscuro
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Modo Oscuro", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { newValue ->
                        scope.launch { settingsRepo.setDarkMode(newValue) }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = PurplePrimary)
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Switch Notificaciones (Simulado)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Notificaciones", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }

            Spacer(Modifier.weight(1f))
            Text("Versión 1.0 - Milenium App", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ================= COMPONENTES VISUALES =================

@Composable
fun PublicationCardModern(publication: Publication, onDelete: () -> Unit, onLike: (Boolean) -> Unit) {
    val context = LocalContext.current
    val imageManager = remember { ImageManager(context) }
    val displayUri = remember(publication.imageUri) { imageManager.getDisplayableUri(publication.imageUri) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = PurpleLight) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("M", fontWeight = FontWeight.Bold, color = PurpleDark)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Estudiante Milenium", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatDate(publication.timestamp), fontSize = 12.sp, color = Color.Gray)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Borrar", tint = Color.Gray) }
            }
            Spacer(Modifier.height(12.dp))
            Text(publication.titulo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PurplePrimary)
            Text(publication.descripcion, color = MaterialTheme.colorScheme.onSurface)

            displayUri?.let {
                Spacer(Modifier.height(12.dp))
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            }

            Row(Modifier.padding(top = 12.dp)) {
                Icon(
                    if (publication.isLiked) Icons.Filled.Favorite else Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = if (publication.isLiked) Color.Red else Color.Gray,
                    modifier = Modifier.clickable { onLike(!publication.isLiked) }
                )
                Spacer(Modifier.width(4.dp))
                Text("${publication.likes}", color = Color.Gray)
            }
        }
    }
}

// Utility Date Formatter
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ================= OTRAS PANTALLAS (Simplificadas para espacio) =================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PublicationRepository(context) }
    val publications by repository.getAllPublications().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(SearchResult(emptyList(), emptyList())) }

    LaunchedEffect(query) { result = repository.searchAllContent(query, publications) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Buscar") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "") } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Buscar...") }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Filled.Search, "") })
            LazyColumn(Modifier.padding(top = 16.dp)) {
                if(result.users.isNotEmpty()) {
                    item { Text("Usuarios", fontWeight = FontWeight.Bold, color = PurplePrimary) }
                    items(result.users) { u -> Text("• ${u.nombre}", Modifier.padding(8.dp)) }
                }
                if(result.publications.isNotEmpty()) {
                    item { Text("Posts", fontWeight = FontWeight.Bold, color = PurplePrimary) }
                    items(result.publications) { p -> Text("• ${p.titulo}", Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

// =======================================
// ========= PANTALLA DE PERFIL ==========
// =======================================
@Composable
fun SimpleProfileScreen(onBack: () -> Unit) {
    // Datos falsos adaptados a la nueva estructura
    val publications = remember {
        mutableStateListOf(
            Publication(
                titulo = "Mi Graduación",
                descripcion = "Un día inolvidable en el Centro Universitario Milenium. ¡Gracias a todos!",
                timestamp = System.currentTimeMillis() - 86400000 // Hace 1 día
            ),
            Publication(
                titulo = "Proyecto Final",
                descripcion = "Presentando nuestro proyecto de Inteligencia Artificial.",
                timestamp = System.currentTimeMillis() - 172800000 // Hace 2 días
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera con degradado Morado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(PurplePrimary, PurpleDark)
                    )
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Atrás", tint = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Rafael Osorio", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Color.White)
                        Text("@pantera_osorio", fontSize = 14.sp, color = PurpleLight)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ing. Sistemas Informáticos\nInteligencia Artificial",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(90.dp),
                            shape = CircleShape,
                            color = Whiteish,
                            border = BorderStroke(2.dp, PurpleLight)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("R", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${publications.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text("Posts", fontSize = 12.sp, color = PurpleLight)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Lista de publicaciones del perfil
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("MIS PUBLICACIONES", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                Divider(color = PurpleLight, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
            }

            items(publications) { publication ->
                PublicationCardModern(
                    publication = publication,
                    onDelete = { publications.remove(publication) },
                    onLike = { liked ->
                        val index = publications.indexOf(publication)
                        if (index != -1) publications[index] = publication.copy(isLiked = liked)
                    }
                )
            }
        }
    }
}

// =======================================
// ====== PANTALLA DE NOTIFICACIONES =====
// =======================================
@Composable
fun NotificationScreenStyle(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar personalizada
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Atrás", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Notificaciones", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Icon(Icons.Filled.Search, "Buscar", tint = MaterialTheme.colorScheme.onBackground)
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(5) { index ->
                NotificationItemStyle(index)
            }
        }
    }
}

@Composable
fun NotificationItemStyle(index: Int) {
    val names = listOf("Pedro", "Ana López", "Milenium Oficial", "Carlos", "Sofía")
    val actions = listOf("Le dio me gusta a tu foto", "Comentó tu publicación", "Publicó un nuevo aviso", "Te comenzó a seguir", "Compartió tu historia")
    val times = listOf("2 min", "15 min", "1 h", "3 h", "1 d")
    val isLike = index == 0 || index == 2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = PurpleLight) {
            Box(contentAlignment = Alignment.Center) {
                Text(names[index].first().toString(), fontWeight = FontWeight.Bold, color = PurpleDark)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = names[index],
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLike) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = PurplePrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = actions[index],
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }

        Text(
            text = times[index],
            color = PurplePrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray.copy(alpha = 0.3f))
}

// =======================================
// ====== PANTALLA DE CONTÁCTANOS ========
// =======================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactanosScreen(navController: NavController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contáctanos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tarjeta de cabecera
            Card(
                colors = CardDefaults.cardColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Face, null, modifier = Modifier.size(60.dp), tint = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Centro Universitario Milenium", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("Estamos para servirte", color = PurpleLight)
                }
            }

            Text("Medios de contacto", fontWeight = FontWeight.Bold, color = PurplePrimary, modifier = Modifier.padding(top = 8.dp))

            ContactItem(Icons.Filled.Phone, "01 (238) 688 31 32")
            ContactItem(Icons.Filled.CheckCircle, "01 (238) 104 80 04")
            ContactItem(Icons.Filled.LocationOn, "Reforma Norte #444 Col. Centro\nC.P. 75700")
            ContactItem(Icons.Filled.Email, "admisiones@unimilenium.edu.mx")
            ContactItem(Icons.Filled.Share, "unimilenium (Facebook/Instagram)")
        }
    }
}

@Composable
fun ContactItem(icon: ImageVector, text: String) {
    Card(
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Surface(shape = CircleShape, color = PurpleLight, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = PurpleDark)
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}