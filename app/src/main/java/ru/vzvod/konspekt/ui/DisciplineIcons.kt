package ru.vzvod.konspekt.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Masks
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Значок предмета для плитки. Предмет из своей библиотеки, которого здесь нет,
 * получает общий значок — список не приходится держать в двух местах.
 */
fun disciplineIcon(id: String): ImageVector = when (id) {
    "tactic" -> Icons.Filled.Terrain
    "fire" -> Icons.Filled.GpsFixed
    "drill" -> Icons.Filled.DirectionsWalk
    "phys" -> Icons.Filled.FitnessCenter
    "regs" -> Icons.Filled.MenuBook
    "rhbz" -> Icons.Filled.Masks
    "topo" -> Icons.Filled.Explore
    "med" -> Icons.Filled.MedicalServices
    "eng" -> Icons.Filled.Construction
    "comm" -> Icons.Filled.Radio
    "polit" -> Icons.Filled.Flag
    "tech" -> Icons.Filled.Build
    "special" -> Icons.Filled.Settings
    else -> Icons.Filled.Apps
}
