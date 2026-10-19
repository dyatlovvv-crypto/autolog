package ru.autolog.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import ru.autolog.app.AutologApp
import ru.autolog.app.BuildConfig
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar

object GasMapTags {
    const val SCREEN = "gas_map_screen"
    const val BACK = "gas_map_back"
    const val PICK_TEST = "gas_map_pick_test"
}

private const val TAG = "AutologMap"

/**
 * OSM map in WebView. Stations come from [NearbyFuelStationsStore] (prefetched on refuel screen).
 * Large JSON is passed via JavascriptInterface (not evaluateJavascript) to avoid WebView crashes.
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var bridgeRef by remember { mutableStateOf<MapJsBridge?>(null) }
    var permissionNonce by remember { mutableStateOf(0) }
    val pickedOnce = remember { AtomicBoolean(false) }

    val showTestPick = BuildConfig.SHOW_MAP_TEST_PICK

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionNonce += 1
        val wv = webViewRef
        val bridge = bridgeRef
        if (wv != null && bridge != null) {
            centerAndLoadStations(wv, bridge, fuelStore)
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
                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                        .build()

                    WebView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setGeolocationEnabled(true)
                        settings.loadsImagesAutomatically = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Autolog/0.7.8"

                        val bridge = MapJsBridge(
                            webView = this,
                            mainHandler = mainHandler,
                            fuelStore = fuelStore,
                            onStationPicked = pick@{ name, lat, lon ->
                                if (!pickedOnce.compareAndSet(false, true)) return@pick
                                // Let WebView finish the touch/JS frame before tearing the screen down.
                                mainHandler.post {
                                    runCatching { onStationPicked(name, lat, lon) }
                                        .onFailure { Log.e(TAG, "onStationPicked", it) }
                                }
                            },
                        )
                        bridgeRef = bridge
                        webViewRef = this
                        addJavascriptInterface(bridge, "Autolog")

                        webChromeClient = object : WebChromeClient() {
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: android.webkit.GeolocationPermissions.Callback?,
                            ) {
                                callback?.invoke(origin, true, false)
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d(TAG, "js: ${consoleMessage?.message()}")
                                return true
                            }
                        }
                        webViewClient = object : WebViewClientCompat() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                return assetLoader.shouldInterceptRequest(request.url)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d(TAG, "page finished: $url nonce=$permissionNonce")
                                view.post {
                                    view.evaluateJavascript(
                                        "if(window.__autologMap){window.__autologMap.invalidateSize(true);}",
                                        null,
                                    )
                                    // Show cached stations immediately if prefetch already finished.
                                    bridge.injectCachedIfAny()
                                    centerAndLoadStations(view, bridge, fuelStore)
                                }
                                view.postDelayed({
                                    view.evaluateJavascript(
                                        "if(window.__autologMap){window.__autologMap.invalidateSize(true);}",
                                        null,
                                    )
                                }, 400)
                            }
                        }
                        loadUrl("https://appassets.androidplatform.net/assets/gas_map.html")
                    }
                },
                update = { view ->
                    view.post {
                        view.evaluateJavascript(
                            "if(window.__autologMap){window.__autologMap.invalidateSize(true);}",
                            null,
                        )
                    }
                },
            )
            if (showTestPick) {
                Button(
                    onClick = {
                        if (pickedOnce.compareAndSet(false, true)) {
                            onStationPicked("UITest АЗС", 55.75, 37.62)
                        }
                    },
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

private class MapJsBridge(
    private val webView: WebView,
    private val mainHandler: Handler,
    private val fuelStore: ru.autolog.app.data.map.NearbyFuelStationsStore,
    private val onStationPicked: (String, Double, Double) -> Unit,
) {
    @Volatile
    private var pendingStationsJson: String = """{"elements":[]}"""

    @JavascriptInterface
    fun onPick(name: String?, lat: Double, lon: Double) {
        val safe = name?.trim()?.take(200)?.ifBlank { "АЗС" } ?: "АЗС"
        if (!lat.isFinite() || !lon.isFinite()) return
        mainHandler.post { onStationPicked(safe, lat, lon) }
    }

    /** Safe path for large payloads — JS pulls JSON instead of embedding it in evaluateJavascript. */
    @JavascriptInterface
    fun getStationsJson(): String = pendingStationsJson

    @JavascriptInterface
    fun requestStations(lat: Double, lon: Double) {
        fetchStations(lat, lon)
    }

    fun injectCachedIfAny() {
        val cached = fuelStore.cachedOrNull() ?: return
        pendingStationsJson = cached.json
        webView.evaluateJavascript(
            "window.showStations && window.showStations(Autolog.getStationsJson());",
            null,
        )
    }

    fun fetchStations(lat: Double, lon: Double) {
        fuelStore.execute {
            val json = runCatching { fuelStore.getStations(lat, lon) }
                .onFailure {
                    if (it is InterruptedException) {
                        Thread.currentThread().interrupt()
                        Log.d(TAG, "fetchStations interrupted")
                    } else {
                        Log.w(TAG, "fetchStations: ${it.message}")
                    }
                }
                .getOrNull()
            if (Thread.currentThread().isInterrupted) return@execute
            mainHandler.post {
                if (!webView.isAttachedToWindow) return@post
                if (json == null) {
                    webView.evaluateJavascript(
                        "window.setStatus && window.setStatus(" +
                            "'Не удалось загрузить АЗС. Введите вручную.', true);",
                        null,
                    )
                } else {
                    pendingStationsJson = json
                    webView.evaluateJavascript(
                        "window.showStations && window.showStations(Autolog.getStationsJson());",
                        null,
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun centerAndLoadStations(
    webView: WebView,
    bridge: MapJsBridge,
    fuelStore: ru.autolog.app.data.map.NearbyFuelStationsStore,
) {
    val ctx = webView.context
    val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    fun go(lat: Double, lon: Double, msg: String) {
        webView.evaluateJavascript(
            "window.showHere && window.showHere($lat, $lon, ${JSONObject.quote(msg)});",
            null,
        )
        // Instant paint from cache, then refresh if needed.
        fuelStore.cachedOrNull()?.let { c ->
            if (
                distanceOk(c.lat, c.lon, lat, lon)
            ) {
                bridge.injectCachedIfAny()
            }
        }
        bridge.fetchStations(lat, lon)
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
    if (last != null && System.currentTimeMillis() - last.time < 5 * 60_000L) {
        Log.d(TAG, "lastKnown fresh=${last.latitude},${last.longitude}")
        go(last.latitude, last.longitude, "Ищу заправки рядом… тапните маркер")
    } else if (last != null) {
        Log.d(TAG, "lastKnown stale=${last.latitude},${last.longitude}")
        go(last.latitude, last.longitude, "Уточняю местоположение… тапните маркер")
    } else {
        go(55.75, 37.62, "Определяю местоположение…")
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(fresh: Location) {
            if (!usedFresh.compareAndSet(false, true)) return
            runCatching { lm?.removeUpdates(this) }
            Log.d(TAG, "fresh=${fresh.latitude},${fresh.longitude}")
            webView.post {
                go(fresh.latitude, fresh.longitude, "Ищу заправки рядом… тапните маркер")
            }
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
    webView.postDelayed({
        runCatching { lm?.removeUpdates(listener) }
    }, 12_000L)
}

private fun distanceOk(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
    val out = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, out)
    return out[0] < 2_500f
}
