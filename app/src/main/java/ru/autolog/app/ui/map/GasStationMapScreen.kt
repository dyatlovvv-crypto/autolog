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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import org.json.JSONArray
import org.json.JSONObject
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar

object GasMapTags {
    const val SCREEN = "gas_map_screen"
    const val PICK_TEST = "gas_map_pick_test"
}

private const val TAG = "AutologMap"

/**
 * OSM map in WebView (AssetLoader https origin).
 * Overpass runs on Android (avoids WebView CORS) and injects markers into JS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GasStationMapScreen(
    onStationPicked: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val io = remember { Executors.newSingleThreadExecutor() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var bridgeRef by remember { mutableStateOf<MapJsBridge?>(null) }
    var permissionNonce by remember { mutableStateOf(0) }

    val underEspresso = remember {
        try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionNonce += 1
        val wv = webViewRef
        val bridge = bridgeRef
        if (wv != null && bridge != null) {
            centerAndLoadStations(wv, bridge)
        }
    }

    DisposableEffect(Unit) {
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
        onDispose {
            io.shutdownNow()
        }
    }

    GlassScaffold(
        modifier = Modifier.testTag(GasMapTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Заправка на карте") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
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
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 Autolog/0.7.3"

                        val bridge = MapJsBridge(
                            webView = this,
                            mainHandler = mainHandler,
                            io = io,
                            onStationPicked = onStationPicked,
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
                                    centerAndLoadStations(view, bridge)
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
            if (underEspresso) {
                Button(
                    onClick = { onStationPicked("UITest АЗС") },
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
    private val io: java.util.concurrent.ExecutorService,
    private val onStationPicked: (String) -> Unit,
) {
    @JavascriptInterface
    fun onPick(name: String) {
        mainHandler.post { onStationPicked(name) }
    }

    @JavascriptInterface
    fun requestStations(lat: Double, lon: Double) {
        fetchStations(lat, lon)
    }

    fun fetchStations(lat: Double, lon: Double) {
        io.execute {
            val json = queryOverpass(lat, lon)
            mainHandler.post {
                if (json == null) {
                    webView.evaluateJavascript(
                        "window.setStatus && window.setStatus('Не удалось загрузить АЗС. Введите вручную.', true);",
                        null,
                    )
                } else {
                    val escaped = JSONObject.quote(json)
                    webView.evaluateJavascript(
                        "window.showStations && window.showStations($escaped);",
                        null,
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun centerAndLoadStations(webView: WebView, bridge: MapJsBridge) {
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

private const val HTTP_UA = "Autolog/0.7.3 (Android; fuel map; contact=github.com/dyatlovvv-crypto/autolog)"

/** Overpass race + Nominatim/Photon fallbacks. Returns Overpass-shaped JSON for JS. */
private fun queryOverpass(lat: Double, lon: Double): String? {
    val aroundM = 8_000
    val q = """
        [out:json][timeout:20];
        (
          nwr["amenity"="fuel"](around:$aroundM,$lat,$lon);
          nwr["highway"="services"]["fuel"="yes"](around:$aroundM,$lat,$lon);
        );
        out center tags;
    """.trimIndent()
    val endpoints = listOf(
        "https://overpass.openstreetmap.ru/cgi/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass.osm.ch/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )
    val pool = Executors.newFixedThreadPool(endpoints.size.coerceAtMost(4))
    try {
        val ecs = ExecutorCompletionService<String?>(pool)
        endpoints.forEach { ep ->
            ecs.submit(Callable { postOverpass(ep, q) })
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(22)
        for (i in endpoints.indices) {
            val waitNs = (deadline - System.nanoTime()).coerceAtLeast(0)
            val fut = ecs.poll(waitNs, TimeUnit.NANOSECONDS) ?: break
            val body = runCatching { fut.get() }.getOrNull()
            if (!body.isNullOrBlank() && hasFuelElements(body)) {
                Log.d(TAG, "overpass ok, bytes=${body.length}")
                return body
            }
        }
    } finally {
        pool.shutdownNow()
    }
    Log.w(TAG, "overpass all failed, trying fallbacks")
    return queryNominatimFuel(lat, lon) ?: queryPhotonFuel(lat, lon)
}

private fun hasFuelElements(body: String): Boolean {
    return runCatching {
        JSONObject(body).optJSONArray("elements")?.length()?.let { it > 0 } == true
    }.getOrDefault(false)
}

private fun postOverpass(endpoint: String, query: String): String? {
    return try {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", HTTP_UA)
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.bufferedWriter().use {
            it.write("data=" + java.net.URLEncoder.encode(query, "UTF-8"))
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        if (code in 200..299 && !body.isNullOrBlank()) {
            Log.d(TAG, "overpass $endpoint ok")
            body
        } else {
            Log.w(TAG, "overpass $endpoint -> $code")
            null
        }
    } catch (t: Throwable) {
        Log.w(TAG, "overpass fail $endpoint: ${t.message}")
        null
    }
}

private fun queryNominatimFuel(lat: Double, lon: Double): String? {
    val delta = 0.08
    val south = lat - delta
    val north = lat + delta
    val west = lon - delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
    val east = lon + delta / cos(Math.toRadians(lat)).coerceAtLeast(0.2)
    val viewbox = "$west,$north,$east,$south"
    val q = java.net.URLEncoder.encode("[amenity=fuel]", "UTF-8")
    val vb = java.net.URLEncoder.encode(viewbox, "UTF-8")
    val url =
        "https://nominatim.openstreetmap.org/search?format=jsonv2&q=$q" +
            "&limit=50&bounded=1&viewbox=$vb"
    return try {
        val raw = httpGet(url) ?: return null
        val arr = JSONArray(raw)
        val elements = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val el = JSONObject()
            el.put("type", "node")
            el.put("lat", o.optDouble("lat"))
            el.put("lon", o.optDouble("lon"))
            val tags = JSONObject()
            val name = o.optString("name").ifBlank {
                o.optString("display_name").substringBefore(',').trim()
            }
            if (name.isNotBlank()) tags.put("name", name)
            el.put("tags", tags)
            elements.put(el)
        }
        if (elements.length() == 0) return null
        Log.d(TAG, "nominatim fuel=${elements.length()}")
        JSONObject().put("elements", elements).toString()
    } catch (t: Throwable) {
        Log.w(TAG, "nominatim fail: ${t.message}")
        null
    }
}

private fun queryPhotonFuel(lat: Double, lon: Double): String? {
    val url =
        "https://photon.komoot.io/api/?q=fuel&lat=$lat&lon=$lon&limit=50&osm_tag=amenity:fuel"
    return try {
        val raw = httpGet(url) ?: return null
        val features = JSONObject(raw).optJSONArray("features") ?: return null
        val elements = JSONArray()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val props = f.optJSONObject("properties")
            val el = JSONObject()
            el.put("type", "node")
            el.put("lon", coords.optDouble(0))
            el.put("lat", coords.optDouble(1))
            val tags = JSONObject()
            val name = props?.optString("name")?.ifBlank { null }
                ?: props?.optString("street")?.ifBlank { null }
                ?: "АЗС"
            tags.put("name", name)
            props?.optString("brand")?.takeIf { it.isNotBlank() }?.let { tags.put("brand", it) }
            el.put("tags", tags)
            elements.put(el)
        }
        if (elements.length() == 0) return null
        Log.d(TAG, "photon fuel=${elements.length()}")
        JSONObject().put("elements", elements).toString()
    } catch (t: Throwable) {
        Log.w(TAG, "photon fail: ${t.message}")
        null
    }
}

private fun httpGet(url: String): String? {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 14_000
        setRequestProperty("User-Agent", HTTP_UA)
        setRequestProperty("Accept", "application/json")
    }
    return try {
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }
        if (code in 200..299 && !body.isNullOrBlank()) body else null
    } finally {
        conn.disconnect()
    }
}
