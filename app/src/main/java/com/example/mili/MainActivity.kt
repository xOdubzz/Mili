package com.example.mili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mili.ui.theme.MiliTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Shapes
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp




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
        composable("inicio") { PantallaInicio(navController) }

//        // PANTALLAS EN BLANCO
//        composable("perfil") { PantallaBlanca("Perfil") }
//        composable("notificaciones") { PantallaBlanca("Notificaciones") }
//        composable("contactanos") { PantallaBlanca("Contáctanos") }
//        composable("configuracion") { PantallaBlanca("Configuración") }
//        composable("busqueda") { PantallaBlanca("Buscar") }
//        composable("nueva_post") { PantallaBlanca("Crear nueva publicación") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(navController: NavController) {

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {



                Text(
                    text = "Menú",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                        .fillMaxWidth()
                )

                NavigationDrawerItem(
                    label = { Text("Perfil") },
                    selected = false,
                    onClick = { navController.navigate("perfil") }
                )

                NavigationDrawerItem(
                    label = { Text("Inicio") },
                    selected = false,
                    onClick = { navController.navigate("inicio") }
                )

                NavigationDrawerItem(
                    label = { Text("Notificaciones") },
                    selected = false,
                    onClick = { navController.navigate("notificaciones") }
                )

                NavigationDrawerItem(
                    label = { Text("Contáctanos") },
                    selected = false,
                    onClick = { navController.navigate("contactanos") }
                )

                NavigationDrawerItem(
                    label = { Text("Configuración") },
                    selected = false,
                    onClick = { navController.navigate("configuracion") }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(

                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Menú"
                            )
                        }
                    },
                    title = {
                        Image(
                            painter = painterResource(id = R.drawable.logo2),   // ← tu imagen
                            contentDescription = "Logo de la app",
                            modifier = Modifier
                                .size(60.dp)
                                .clickable { navController.navigate("inicio") }
                        )
                    },

                    actions = {
                        IconButton(onClick = { navController.navigate("busqueda") }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Buscar"
                            )
                        }
                    }
                )
            },

            floatingActionButton = {
                FloatingActionButton(
                    onClick = { navController.navigate("nueva_post") }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Crear nueva publicación"
                    )
                }
            },

            floatingActionButtonPosition = FabPosition.Center
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = "Pantalla de inicio",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

//@Composable
//fun PantallaBlanca(titulo: String) {
//    Box(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(24.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Text(
//            text = titulo,
//            style = MaterialTheme.typography.headlineLarge
//        )
//    }
//}
