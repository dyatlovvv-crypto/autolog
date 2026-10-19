package ru.autolog.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import ru.autolog.app.AutologApp
import ru.autolog.app.BuildConfig
import ru.autolog.app.data.map.NearbyFuelStationsStore
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar

object GasMapTags {
    const val SCREEN = "gas_map_screen"
    const val BACK = "gas_map_back"
    const val PICK_TEST = "gas_map_pick_test"
    const val MY_LOCATION = "gas_map_my_location"
}

private const val TAG = "AutologMap"
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_STATIONS = "autolog-fuel-stations"
private const val LAYER_STATIONS = "autolog-fuel-stations-layer"
private const val SOURCE_HERE = "autolog-here"
private const val LAYER_HERE = "autolog-here-layer"
private const val PROP_NAME = "name"

/**
 * Native MapLibre map (OpenFreeMap style, no API key).
 * Stations from [NearbyFuelStationsStore] (fast nearby, then city/oblast).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GasStationMapScreen(
    onStationPicked: (name: String, lat: Double, lon: Double) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val fuelStore = remember { AutologApp.instance.container.fuelStations }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Загрузка карты…") }
    var hintError by remember { mutableStateOf(false) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    val fittedOnce = remember { AtomicBoolean(false) }
    val pickedOnce = remember { AtomicBoolean(false) }
    val showTestPick = BuildConfig.SHOW_MAP_TEST_PICK

    fun pick(name: String, lat: Double, lon: Double) {
        if (!pickedOnce.compareAndSet(false, true)) return
        mainHandler.post {
            runCatching { onStationPicked(name, lat, lon) }
                .onFailure { Log.e(TAG, "onStationPicked", it) }
        }
    }

    fun applyStationsJson(map: MapLibreMap, json: String, centerOnUser: Boolean) {
        val style = map.style ?: return
        val features = parseStationFeatures(json)
        val fc = FeatureCollection.fromFeatures(features)
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_STATIONS)
        if (source != null) {
            source.setGeoJson(fc)
        } else {
            style.addSource(GeoJsonSource(SOURCE_STATIONS, fc))
            style.addLayer(
                CircleLayer(LAYER_STATIONS, SOURCE_STATIONS).withProperties(
                    circleRadius(7f),
                    circleColor(Color.parseColor("#1565C0")),
                    circleStrokeWidth(2f),
                    circleStrokeColor(Color.WHITE),
                ),
            )
        }
        if (features.isEmpty()) {
            hint = "АЗС рядом не найдены — введите название вручную"
            hintError = false
            return
        }
        hint = "Найдено ${features.size} АЗС. Тапните маркер, чтобы выбрать"
        hintError = false
        // Stay on the user — do not fitBounds to far oblast clusters.
        if (centerOnUser && fittedOnce.compareAndSet(false, true)) {
            val la = userLat
            val lo = userLon
            if (la != null && lo != null) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(la, lo), 12.5),
                    400,
                )
            }
        }
    }

    fun showHere(map: MapLibreMap, lat: Double, lon: Double, moveCamera: Boolean) {
        userLat = lat
        userLon = lon
        val style = map.style ?: return
        val fc = FeatureCollection.fromFeatures(
            listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))),
        )
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_HERE)
        if (source != null) {
            source.setGeoJson(fc)
        } else {
            style.addSource(GeoJsonSource(SOURCE_HERE, fc))
            style.addLayer(
                CircleLayer(LAYER_HERE, SOURCE_HERE).withProperties(
                    circleRadius(9f),
                    circleColor(Color.parseColor("#42A5F5")),
                    circleStrokeWidth(3f),
                    circleStrokeColor(Color.parseColor("#0D47A1")),
                ),
            )
        }
        if (moveCamera) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 13.0),
                400,
            )
        }
    }

    fun loadStations(map: MapLibreMap, lat: Double, lon: Double, status: String) {
        hint = status
        hintError = false
        showHere(map, lat, lon, moveCamera = true)
        fuelStore.cachedOrNull()?.let { c ->
            if (distanceOk(c.lat, c.lon, lat, lon, 8_000f)) {
                applyStationsJson(map, c.json, centerOnUser = true)
            }
        }
        fuelStore.execute {
            val finalJson = runCatching {
                fuelStore.fetchStationsProgressive(lat, lon) { partial ->
                    mainHandler.post {
                        applyStationsJson(map, partial, centerOnUser = true)
                        hint = "Догружаю АЗС по области…"
                        hintError = false
                    }
                }
            }.onFailure {
                if (it is InterruptedException) {
                    Thread.currentThread().interrupt()
                } else {
                    Log.w(TAG, "progressive: ${it.message}")
                }
            }.getOrNull()
            if (Thread.currentThread().isInterrupted) return@execute
            mainHandler.post {
                if (finalJson == null) {
                    if (fuelStore.cachedOrNull() == null) {
                        hint = "Не удалось загрузить АЗС. Введите вручную."
                        hintError = true
                    }
                } else {
                    applyStationsJson(map, finalJson, centerOnUser = false)
                }
            }
        }
    }

    fun goToMyLocation() {
        val map = mapRef ?: return
        if (!styleReady) return
        val lat = userLat
        val lon = userLon
        if (lat != null && lon != null) {
            showHere(map, lat, lon, moveCamera = true)
            return
        }
        hint = "Определяю местоположение…"
        hintError = false
        requestFreshLocation(context) { freshLat, freshLon ->
            showHere(map, freshLat, freshLon, moveCamera = true)
            loadStations(map, freshLat, freshLon, "Ищу заправки рядом… тапните маркер")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val map = mapRef
        if (map != null && styleReady) {
            centerAndLoad(context, map, fuelStore, ::loadStations)
        }
    }

    DisposableEffect(Unit) {
        fuelStore.prefetchAsync()
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
        onDispose { }
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mapView = mapViewRef ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        when (lifecycleOwner.lifecycle.currentState) {
            Lifecycle.State.STARTED,
            Lifecycle.State.RESUMED,
            -> {
                mapView.onStart()
                if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) {
                    mapView.onResume()
                }
            }
            else -> Unit
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    GlassScaffold(
        modifier = Modifier.testTag(GasMapTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Заправка на карте") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(GasMapTags.BACK),
                    ) { Text("←") }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        onCreate(Bundle())
                        mapViewRef = this
                        getMapAsync { map ->
                            mapRef = map
                            map.uiSettings.isAttributionEnabled = true
                            map.uiSettings.isLogoEnabled = false
                            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { _ ->
                                styleReady = true
                                map.addOnMapClickListener { latLng ->
                                    val screen = map.projection.toScreenLocation(latLng)
                                    val hits = map.queryRenderedFeatures(screen, LAYER_STATIONS)
                                    val f = hits.firstOrNull() ?: return@addOnMapClickListener false
                                    val geom = f.geometry() as? Point
                                        ?: return@addOnMapClickListener false
                                    val name = f.getStringProperty(PROP_NAME)?.trim()
                                        ?.ifBlank { null } ?: "АЗС"
                                    pick(name, geom.latitude(), geom.longitude())
                                    true
                                }
                                // Instant paint from prefetch cache before GPS settles.
                                fuelStore.cachedOrNull()?.let { c ->
                                    showHere(map, c.lat, c.lon, moveCamera = false)
                                    applyStationsJson(map, c.json, centerOnUser = true)
                                }
                                centerAndLoad(ctx, map, fuelStore, ::loadStations)
                            }
                        }
                    }
                },
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hintError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            FloatingActionButton(
                onClick = { goToMyLocation() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .padding(bottom = if (showTestPick) 72.dp else 0.dp)
                    .testTag(GasMapTags.MY_LOCATION),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MyLocation,
                    contentDescription = "Моё место",
                )
            }
            if (showTestPick) {
                Button(
                    onClick = { pick("UITest АЗС", 55.75, 37.62) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag(GasMapTags.PICK_TEST),
                ) {
                    Text("Тест: выбрать АЗС")
                }
            }
        }
    }
}

private fun parseStationFeatures(json: String): List<Feature> {
    return runCatching {
        val root = JSONObject(json)
        val els = root.optJSONArray("elements") ?: return emptyList()
        buildList {
            for (i in 0 until els.length()) {
                val n = els.optJSONObject(i) ?: continue
                val lat = when {
                    n.has("lat") -> n.optDouble("lat")
                    else -> n.optJSONObject("center")?.optDouble("lat")
                } ?: continue
                val lon = when {
                    n.has("lon") -> n.optDouble("lon")
                    else -> n.optJSONObject("center")?.optDouble("lon")
                } ?: continue
                if (!lat.isFinite() || !lon.isFinite()) continue
                val tags = n.optJSONObject("tags")
                val name = tags?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: tags?.optString("brand")?.takeIf { it.isNotBlank() }
                    ?: tags?.optString("operator")?.takeIf { it.isNotBlank() }
                    ?: "АЗС"
                val f = Feature.fromGeometry(Point.fromLngLat(lon, lat))
                f.addStringProperty(PROP_NAME, name.take(200))
                add(f)
            }
        }
    }.getOrElse {
        Log.w(TAG, "parseStations: ${it.message}")
        emptyList()
    }
}

@SuppressLint("MissingPermission")
private fun requestFreshLocation(
    ctx: android.content.Context,
    onLocation: (Double, Double) -> Unit,
) {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        onLocation(55.75, 37.62)
        return
    }
    val lm = ctx.getSystemService(LocationManager::class.java)
    val last = lm?.let { manager ->
        sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }
    if (last != null) {
        onLocation(last.latitude, last.longitude)
    }
    val once = AtomicBoolean(false)
    val freshListener = object : LocationListener {
        override fun onLocationChanged(fresh: Location) {
            if (!once.compareAndSet(false, true)) return
            runCatching { lm?.removeUpdates(this) }
            onLocation(fresh.latitude, fresh.longitude)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }
    runCatching {
        if (fine) {
            lm?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                freshListener,
                Looper.getMainLooper(),
            )
        }
        lm?.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            0L,
            0f,
            freshListener,
            Looper.getMainLooper(),
        )
    }
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching { lm?.removeUpdates(freshListener) }
        if (!once.get() && last == null) onLocation(55.75, 37.62)
    }, 8_000L)
}

@SuppressLint("MissingPermission")
private fun centerAndLoad(
    ctx: android.content.Context,
    map: MapLibreMap,
    fuelStore: NearbyFuelStationsStore,
    loadStations: (MapLibreMap, Double, Double, String) -> Unit,
) {
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun go(lat: Double, lon: Double, msg: String) {
        loadStations(map, lat, lon, msg)
    }

    if (!fine && !coarse) {
        go(55.75, 37.62, "Нет геолокации — Москва. Ищу АЗС…")
        return
    }
    val lm = ctx.getSystemService(LocationManager::class.java)
    val last: Location? = lm?.let { manager ->
        sequenceOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    val usedFresh = AtomicBoolean(false)
    when {
        last != null && System.currentTimeMillis() - last.time < 5 * 60_000L -> {
            go(last.latitude, last.longitude, "Ищу заправки рядом…")
        }
        last != null -> {
            go(last.latitude, last.longitude, "Уточняю местоположение…")
        }
        else -> go(55.75, 37.62, "Определяю местоположение…")
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(fresh: Location) {
            if (!usedFresh.compareAndSet(false, true)) return
            runCatching { lm?.removeUpdates(this) }
            go(fresh.latitude, fresh.longitude, "Ищу заправки рядом…")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    runCatching {
        if (fine) {
            lm?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                listener,
                Looper.getMainLooper(),
            )
        }
        lm?.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            0L,
            0f,
            listener,
            Looper.getMainLooper(),
        )
    }
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching { lm?.removeUpdates(listener) }
    }, 12_000L)
}

private fun distanceOk(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
    maxM: Float,
): Boolean {
    val out = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, out)
    return out[0] < maxM
}
