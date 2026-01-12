package com.example.tripwise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tripwise.ui.theme.TripWiseTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class HomeActivity : ComponentActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TripWiseTheme {
                HomeScreen(
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    onOpenDetails = { userId, placeId ->
                        val i = Intent(this, PlaceDetailsActivity::class.java).apply {
                            putExtra("userId", userId)
                            putExtra("placeId", placeId)
                        }
                        startActivity(i)
                    }
                )
            }
        }
    }
}

/* ---------- Data Model ---------- */

data class Place(
    val id: String = "",
    val name: String = "",
    val category: String = "", // "Attractions" | "Restaurants" | "Hotels"
    val distanceMeters: Int = 0,
    val rating: Double = 0.0,
    val address: String = "",
    val isFavorite: Boolean = false
)

private fun samplePlaces(): List<Place> = listOf(
    Place("1", "Riverside Museum", "Attractions", 450, 4.6, "12 River St, City", false),
    Place("2", "Skyline Viewpoint", "Attractions", 900, 4.8, "Hilltop Rd, City", false),
    Place("3", "Blue Harbor Hotel", "Hotels", 1200, 4.3, "45 Ocean Ave, City", false),
    Place("4", "Bella Italia", "Restaurants", 300, 4.5, "22 Market Ln, City", false),
    Place("5", "City Art Gallery", "Attractions", 1500, 4.7, "Museum Sq, City", false),
    Place("6", "Maple Inn", "Hotels", 850, 4.1, "78 Park Rd, City", false),
)

/* ---------- HOME SCREEN ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onOpenDetails: (userId: String, placeId: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val userId = auth.currentUser?.uid ?: "guest"

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val seedDoc = remember { db.collection("users").document(userId).collection("meta").document("seed") }
    val placesCol = remember { db.collection("users").document(userId).collection("places") }

    // ✅ Seed once + real-time listener
    LaunchedEffect(userId) {
        try {
            val seedSnap = seedDoc.get().await()
            val alreadySeeded = seedSnap.getBoolean("done") == true

            if (!alreadySeeded) {
                val batch = db.batch()
                samplePlaces().forEach { p -> batch.set(placesCol.document(p.id), p) }
                batch.set(seedDoc, mapOf("done" to true))
                batch.commit().await()
            }

            startPlacesListener(
                placesColRef = placesCol,
                onData = {
                    places = it
                    loading = false
                },
                onError = {
                    error = it
                    loading = false
                }
            )
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong"
            loading = false
        }
    }

    val filtered = remember(query, selectedCategory, places) {
        places.filter { p ->
            (selectedCategory == null || p.category == selectedCategory) &&
                    (query.isBlank() || p.name.contains(query, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TripWise", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Place")
            }
        }
    ) { inner ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        "Discover nearby places",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.height(8.dp))

                    SearchBar(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    CategoryChips(
                        selected = selectedCategory,
                        onSelect = { selectedCategory = if (selectedCategory == it) null else it }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            error?.let {
                Text("Error: $it", color = Color.Red, modifier = Modifier.padding(16.dp))
                return@Scaffold
            }

            Text(
                "Nearby",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(filtered.size) { idx ->
                    val place = filtered[idx]
                    PlaceCard(
                        place = place,
                        onToggleFavorite = {
                            placesCol.document(place.id).update("isFavorite", !place.isFavorite)
                        },
                        onDetails = {
                            onOpenDetails(userId, place.id)
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddPlaceDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, category, address, rating ->
                    showAddDialog = false
                    addPlaceToFirestore(placesCol, name, category, address, rating)
                }
            )
        }
    }
}

/* ---------- Firestore helpers ---------- */

private fun startPlacesListener(
    placesColRef: com.google.firebase.firestore.CollectionReference,
    onData: (List<Place>) -> Unit,
    onError: (String) -> Unit
): ListenerRegistration {
    return placesColRef
        .orderBy("name")
        .addSnapshotListener { snap, e ->
            if (e != null) {
                onError(e.message ?: "Failed to load places")
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toObject(Place::class.java) } ?: emptyList()
            onData(list)
        }
}

private fun addPlaceToFirestore(
    placesCol: com.google.firebase.firestore.CollectionReference,
    name: String,
    category: String,
    address: String,
    rating: Double
) {
    val docRef: DocumentReference = placesCol.document()
    val randomDistance = (300..2500).random()

    val place = Place(
        id = docRef.id,
        name = name.trim(),
        category = category,
        distanceMeters = randomDistance,
        rating = rating,
        address = address.trim(),
        isFavorite = false
    )

    docRef.set(place)
}

/* ---------- UI Components ---------- */

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text("Search places…") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

@Composable
private fun CategoryChips(
    selected: String?,
    onSelect: (String) -> Unit
) {
    val categories = listOf("Attractions", "Restaurants", "Hotels")

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
    ) {
        Spacer(Modifier.width(12.dp))
        categories.forEach { cat ->
            val isSelected = selected == cat

            AssistChip(
                onClick = { onSelect(cat) },
                label = { Text(cat) },
                leadingIcon = {
                    when (cat) {
                        "Attractions" -> Icon(Icons.Default.Place, contentDescription = null)
                        "Restaurants" -> Icon(Icons.Default.Restaurant, contentDescription = null)
                        else -> Icon(Icons.Default.Hotel, contentDescription = null)
                    }
                },
                modifier = Modifier.padding(horizontal = 6.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun PlaceCard(
    place: Place,
    onToggleFavorite: () -> Unit,
    onDetails: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clickable { onDetails() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (place.category) {
                        "Attractions" -> Icons.Default.Place
                        "Restaurants" -> Icons.Default.Restaurant
                        else -> Icons.Default.Hotel
                    },
                    contentDescription = null
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    "${place.rating} ★  •  ${(place.distanceMeters / 1000.0).let { "%.1f".format(it) }} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    place.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleFavorite) {
                if (place.isFavorite) {
                    Icon(Icons.Default.Favorite, contentDescription = "Unfavorite")
                } else {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite")
                }
            }
        }
    }
}

/* ---------- Add Place Dialog ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlaceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, category: String, address: String, rating: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var ratingText by remember { mutableStateOf("4.5") }

    var category by remember { mutableStateOf("Attractions") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a Place") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Place name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("Attractions", "Restaurants", "Hotels").forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    category = it
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = ratingText,
                    onValueChange = { ratingText = it },
                    label = { Text("Rating (0.0 - 5.0)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = ratingText.toDoubleOrNull()?.coerceIn(0.0, 5.0) ?: 4.5
                    if (name.trim().isNotEmpty() && address.trim().isNotEmpty()) {
                        onAdd(name, category, address, ((r * 10).roundToInt() / 10.0))
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    TripWiseTheme { HomeScreen(onLogout = {}, onOpenDetails = { _, _ -> }) }
}
