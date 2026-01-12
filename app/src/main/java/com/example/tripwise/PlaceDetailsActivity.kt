package com.example.tripwise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.tripwise.ui.theme.TripWiseTheme
import com.google.firebase.firestore.FirebaseFirestore

class PlaceDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val placeId = intent.getStringExtra("placeId") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""

        setContent {
            TripWiseTheme {
                PlaceDetailsScreen(
                    userId = userId,
                    placeId = placeId,
                    onBack = { finish() },
                    onOpenMap = { uid, pid ->
                        startActivity(
                            Intent(this, MapActivity::class.java).apply {
                                putExtra("userId", uid)
                                putExtra("placeId", pid)
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceDetailsScreen(
    userId: String,
    placeId: String,
    onBack: () -> Unit,
    onOpenMap: (userId: String, placeId: String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }

    var loading by remember { mutableStateOf(true) }
    var place by remember { mutableStateOf<Place?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // ✅ Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onOpenMap(userId, placeId)
        } else {
            Toast.makeText(context, "Location permission is required to open the map", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestLocationPermissionAndOpenMap() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onOpenMap(userId, placeId)
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Real-time details listener
    LaunchedEffect(placeId, userId) {
        if (placeId.isBlank() || userId.isBlank()) {
            error = "Missing place data"
            loading = false
            return@LaunchedEffect
        }

        db.collection("users").document(userId)
            .collection("places").document(placeId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    error = e.message
                    loading = false
                    return@addSnapshotListener
                }
                place = snap?.toObject(Place::class.java)
                loading = false
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Place Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        error?.let {
            Column(modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        val p = place ?: run {
            Column(modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
                Text("Place not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Category: ${p.category}")
                    Text("Rating: ${p.rating} ★")
                    Text("Distance: ${(p.distanceMeters / 1000.0).let { "%.1f".format(it) }} km")
                    Text("Address: ${p.address}")
                }
            }

            // ✅ Map + Favorite (no “Open Map”)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                Button(
                    onClick = { requestLocationPermissionAndOpenMap() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Map")
                }

                OutlinedButton(
                    onClick = {
                        db.collection("users").document(userId)
                            .collection("places").document(placeId)
                            .update("isFavorite", !p.isFavorite)
                            .addOnFailureListener {
                                Toast.makeText(context, it.message ?: "Failed", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (p.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (p.isFavorite) "Unfavorite" else "Favorite")
                }
            }

            OutlinedButton(
                onClick = {
                    db.collection("users").document(userId)
                        .collection("places").document(placeId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, it.message ?: "Delete failed", Toast.LENGTH_SHORT).show()
                        }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Place")
            }
        }
    }
}
