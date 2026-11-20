package com.example.cloble.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cloble.data.remote.NominatimGeocoder
import com.example.cloble.data.remote.ResolvedLocation
import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.ui.theme.CloBleColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.util.Locale

@Composable
fun CloBleLocationPickerDialog(
    initialCity: String,
    initialLatitude: Double,
    initialLongitude: Double,
    onDismiss: () -> Unit,
    onLocationConfirmed: (city: String, latitude: Double, longitude: Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ResolvedLocation>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedLat by remember { mutableDoubleStateOf(initialLatitude) }
    var selectedLon by remember { mutableDoubleStateOf(initialLongitude) }
    var cityInput by remember { mutableStateOf(initialCity) }
    var fullAddressHint by remember { mutableStateOf(initialCity) }
    var isReverseGeocoding by remember { mutableStateOf(false) }
    var hasUserEditedCity by remember { mutableStateOf(false) }
    var isConfirmingLocation by remember { mutableStateOf(false) }
    var gpsStatusHint by remember { mutableStateOf<String?>(null) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var suppressNextReverseLookup by remember { mutableStateOf(false) }

    fun moveMapTo(lat: Double, lon: Double, zoom: Double = 14.0) {
        selectedLat = lat
        selectedLon = lon
        mapViewRef?.controller?.animateTo(GeoPoint(lat, lon), zoom, 350L)
    }

    fun applyDeviceLocation() {
        val location = readBestLastKnownLocation(context)
        if (location != null) {
            val isInsideIndonesia = location.latitude in -12.0..7.5 && location.longitude in 94.0..142.0
            if (!isInsideIndonesia) {
                gpsStatusHint = "GPS perangkat menunjuk ke luar Indonesia (default emulator). Silakan cari atau geser pin ke kota Anda di Indonesia."
                return
            }
            gpsStatusHint = null
            hasUserEditedCity = false
            suppressNextReverseLookup = false
            moveMapTo(location.latitude, location.longitude, 15.0)
            coroutineScope.launch {
                isReverseGeocoding = true
                val resolved = NominatimGeocoder.reverseGeocode(location.latitude, location.longitude)
                if (resolved != null && !hasUserEditedCity) {
                    cityInput = resolved.shortLabel
                    fullAddressHint = resolved.displayName
                }
                isReverseGeocoding = false
            }
        } else {
            gpsStatusHint = "Lokasi GPS belum tersedia, geser pin peta atau cari nama kota."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            applyDeviceLocation()
        } else {
            gpsStatusHint = "Izin lokasi ditolak. Geser peta atau ketik nama lokasi manual."
        }
    }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(500L)
        searchResults = NominatimGeocoder.searchPlaces(q)
        isSearching = false
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mapView = mapViewRef
        if (mapView == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView.onDetach()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("location_picker_dialog"),
            color = CloBleColors.Background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = CloBleColors.Surface,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_close_location_picker")
                            ) {
                                Icon(
                                    imageVector = CloBleIcons.ArrowLeft,
                                    contentDescription = "Tutup pemilih lokasi",
                                    tint = CloBleColors.TextPrimary
                                )
                            }
                            Text(
                                text = "Pilih Lokasi Barter",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CloBleColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CloBleColors.PrimaryContainer,
                                border = BorderStroke(1.dp, CloBleColors.PrimarySoft),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val hasFine = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val hasCoarse = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasFine || hasCoarse) {
                                            applyDeviceLocation()
                                        } else {
                                            permissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    }
                                    .testTag("btn_use_my_gps")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = CloBleIcons.Crosshair,
                                        contentDescription = "Gunakan lokasi GPS",
                                        tint = CloBleColors.OnPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Lokasi Saya",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CloBleColors.OnPrimaryContainer
                                    )
                                }
                            }
                        }

                        CloBleLabeledTextField(
                            label = "",
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Cari kota, kecamatan, atau daerah di Indonesia...",
                            singleLine = true,
                            minHeight = 46.dp,
                            inputTestTag = "input_search_location",
                            trailingIcon = {
                                when {
                                    isSearching -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = CloBleColors.Primary
                                        )
                                    }
                                    searchQuery.isNotEmpty() -> {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                searchResults = emptyList()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = CloBleIcons.XClose,
                                                contentDescription = "Hapus pencarian",
                                                tint = CloBleColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = CloBleIcons.Search,
                                            contentDescription = null,
                                            tint = CloBleColors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        )

                        if (!gpsStatusHint.isNullOrBlank()) {
                            Text(
                                text = gpsStatusHint.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CloBleColors.WarningText
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val config = Configuration.getInstance()
                            config.userAgentValue = "CloBleAndroid/1.0 (contact@cloble.app)"
                            val osmdroidDir = File(ctx.cacheDir, "osmdroid")
                            val tilesDir = File(osmdroidDir, "tiles_v2")
                            tilesDir.mkdirs()
                            config.osmdroidBasePath = osmdroidDir
                            config.osmdroidTileCache = tilesDir

                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(13.0)
                                controller.setCenter(GeoPoint(initialLatitude, initialLongitude))
                                addMapListener(
                                    DelayedMapListener(
                                        object : MapListener {
                                            override fun onScroll(event: ScrollEvent?): Boolean {
                                                val center = mapCenter
                                                val lat = center.latitude
                                                val lon = center.longitude
                                                selectedLat = lat
                                                selectedLon = lon
                                                if (suppressNextReverseLookup) {
                                                    suppressNextReverseLookup = false
                                                    return true
                                                }
                                                coroutineScope.launch {
                                                    isReverseGeocoding = true
                                                    val resolved = NominatimGeocoder.reverseGeocode(lat, lon)
                                                    if (resolved != null) {
                                                        fullAddressHint = resolved.displayName
                                                        if (!hasUserEditedCity) {
                                                            cityInput = resolved.shortLabel
                                                        }
                                                    }
                                                    isReverseGeocoding = false
                                                }
                                                return true
                                            }

                                            override fun onZoom(event: ZoomEvent?): Boolean {
                                                val center = mapCenter
                                                selectedLat = center.latitude
                                                selectedLon = center.longitude
                                                return true
                                            }
                                        },
                                        450L
                                    )
                                )
                                mapViewRef = this
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("osmdroid_map_view")
                    )

                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CloBleColors.Surface,
                            shadowElevation = 4.dp,
                            border = BorderStroke(2.dp, CloBleColors.Primary)
                        ) {
                            Icon(
                                imageVector = CloBleIcons.MapPin,
                                contentDescription = "Pin lokasi",
                                tint = CloBleColors.Primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    if (isReverseGeocoding) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = CloBleColors.Surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = CloBleColors.Primary
                                )
                                Text(
                                    text = "Mengidentifikasi area...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CloBleColors.TextSecondary
                                )
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CloBleColors.Surface,
                            border = BorderStroke(1.dp, CloBleColors.Outline),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            LazyColumn {
                                items(searchResults) { item ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                hasUserEditedCity = false
                                                suppressNextReverseLookup = true
                                                cityInput = item.shortLabel
                                                fullAddressHint = item.displayName
                                                searchQuery = ""
                                                searchResults = emptyList()
                                                moveMapTo(item.latitude, item.longitude, 13.5)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = item.shortLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = CloBleColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CloBleColors.TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(color = CloBleColors.Outline)
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = CloBleColors.Surface,
                    border = BorderStroke(1.dp, CloBleColors.Outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CloBleLabeledTextField(
                            label = "Nama Kota / Area Tampil",
                            value = cityInput,
                            onValueChange = {
                                hasUserEditedCity = true
                                cityInput = it
                            },
                            placeholder = "Contoh: Sleman, DI Yogyakarta",
                            inputTestTag = "input_selected_city_label"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = fullAddressHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = CloBleColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "  %.4f, %.4f",
                                    selectedLat,
                                    selectedLon
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = CloBleColors.Primary
                            )
                        }

                        CloBlePrimaryButton(
                            text = if (isConfirmingLocation) "Menyimpan Lokasi..." else "Pilih Lokasi Ini",
                            enabled = cityInput.isNotBlank() && !isConfirmingLocation,
                            onClick = {
                                val currentMapCenter = mapViewRef?.mapCenter
                                var finalLat = currentMapCenter?.latitude ?: selectedLat
                                var finalLon = currentMapCenter?.longitude ?: selectedLon
                                val cleanCity = cityInput.trim()
                                val pinUnmovedFromInitial = DistanceCalculator.calculateKmExact(
                                    finalLat,
                                    finalLon,
                                    initialLatitude,
                                    initialLongitude
                                ) < 0.05
                                if (hasUserEditedCity || (searchQuery.isNotBlank() && pinUnmovedFromInitial)) {
                                    coroutineScope.launch {
                                        isConfirmingLocation = true
                                        val queryText = cleanCity.ifBlank { searchQuery.trim() }
                                        val geocoded = NominatimGeocoder.searchPlaces(queryText, limit = 1).firstOrNull()
                                        if (geocoded != null && (!hasUserEditedCity || pinUnmovedFromInitial)) {
                                            finalLat = geocoded.latitude
                                            finalLon = geocoded.longitude
                                        } else {
                                            val resolved = DistanceCalculator.resolveEffectiveCoordinates(
                                                cleanCity,
                                                finalLat,
                                                finalLon
                                            )
                                            finalLat = resolved.first
                                            finalLon = resolved.second
                                        }
                                        isConfirmingLocation = false
                                        onLocationConfirmed(cleanCity, finalLat, finalLon)
                                    }
                                } else {
                                    val resolved = DistanceCalculator.resolveEffectiveCoordinates(
                                        cleanCity,
                                        finalLat,
                                        finalLon
                                    )
                                    onLocationConfirmed(cleanCity, resolved.first, resolved.second)
                                }
                            },
                            modifier = Modifier.testTag("btn_confirm_location")
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun readBestLastKnownLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    val providers = listOfNotNull(
        LocationManager.GPS_PROVIDER.takeIf { hasFine },
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    var best: Location? = null
    for (provider in providers) {
        val candidate = try {
            manager.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        } ?: continue
        if (best == null || candidate.time > best.time) {
            best = candidate
        }
    }
    return best
}
