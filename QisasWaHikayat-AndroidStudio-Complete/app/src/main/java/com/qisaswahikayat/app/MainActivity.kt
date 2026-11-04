package com.qisaswahikayat.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import androidx.compose.runtime.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

const val API_BASE = "https://qisas-app-api.layali-7kayat-719.workers.dev" // Cloudflare Worker المستقل المعتمد
private const val GOOGLE_WEB_CLIENT_ID = "384580739745-stahpkl2nsf26nj3l88lpi4lebf1o7t7.apps.googleusercontent.com"
val Ink = Color(0xFF08090D)
val Panel = Color(0xFF15171D)
val Gold = Color(0xFFFFC400)
val Muted = Color(0xFF9A9BA5)
val Line = Color(0xFF292B34)
val TopBar = Color(0xFF090A0F)

private fun mediaUrl(path: String?): String? {
    val value = path?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    return if (value.startsWith("https://") || value.startsWith("http://")) value
    else "$API_BASE/media/${value.trimStart('/')}"
}

private suspend fun requestJson(path: String, method: String = "GET", body: JSONObject? = null, headers: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
    val connection = URL(API_BASE + path).openConnection() as HttpURLConnection
    connection.connectTimeout = 12000
    connection.readTimeout = 20000
    connection.requestMethod = method
    headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
    if (body != null) {
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
    }
    try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        val result = JSONObject(text)
        if (code !in 200..299) throw IllegalStateException(result.optString("error", "تعذر تنفيذ الطلب"))
        result
    } finally { connection.disconnect() }
}

suspend fun adminRequest(path: String, email: String, password: String, method: String = "GET", body: String? = null): JSONObject =
    requestJson(path, method, body?.let { JSONObject(it) }, mapOf("X-Admin-Email" to email, "X-Admin-Password" to password))

private fun JSONArray.toObjects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.toStrings(): List<String> = (0 until length()).map { optString(it) }

data class Category(val id: String, val title: String, val image: String?)
data class Story(
    val id: String, val title: String, val description: String, val audio: String,
    val image: String?, val status: String, val views: Int, val categoryIds: List<String>,
    val suggestedImage: String? = null,
    val totalEpisodes: Int = 1,
    val createdAt: String = "",
    val characterOneName: String = "البطلة", val characterTwoName: String = "البطل", val characterThreeName: String = "شخصية محورية",
    val characterOneImage: String? = null, val characterTwoImage: String? = null, val characterThreeImage: String? = null
)
data class Episode(val id: String, val title: String, val audio: String, val image: String?, val number: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep Android status bar (time/battery) and navigation controls visible.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 13)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 13)
        MobileAds.initialize(this)
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE) // يمنع لقطات الشاشة والتسجيل داخل التطبيق
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Ink, surface = Panel)) { QisasApp() } }
    }
}




@Composable
private fun AccountScreen(
    displayName: String?, email: String?, photoUrl: String?, onSignIn: () -> Unit, onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Ink).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(model = photoUrl, contentDescription = "صورة المستخدم", contentScale = ContentScale.Crop,
                modifier = Modifier.size(104.dp).clip(RoundedCornerShape(52.dp)))
        } else {
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(52.dp)).background(Gold), contentAlignment = Alignment.Center) {
                Text(displayName?.firstOrNull()?.uppercase() ?: "D", color = Ink, fontSize = 48.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(displayName?.takeIf { it.isNotBlank() } ?: "مرحباً بك في Dramio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (!email.isNullOrBlank()) Text(email, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(26.dp))
        if (email.isNullOrBlank()) {
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink), shape = RoundedCornerShape(15.dp)) {
                Text("المتابعة باستخدام Google", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("تسجيل الخروج", color = Color.White) }
        }
    }
}

@Composable
private fun AdMobBanner() {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            adUnitId = "ca-app-pub-8926612269781002/8133025827"
            setAdSize(AdSize.BANNER)
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().wrapContentHeight().background(Color(0xFF111218)),
        factory = { adView },
        update = { }
    )
}


@Composable
private fun NativeAdCard() {
    val context = LocalContext.current
    val nativeAdView = remember {
        NativeAdView(context).apply {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                setBackgroundColor(AndroidColor.rgb(21, 23, 29))
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val label = TextView(context).apply {
                text = "إعلان"
                textSize = 10f
                setTextColor(AndroidColor.rgb(242, 185, 75))
            }
            val headline = TextView(context).apply {
                textSize = 15f
                setTextColor(AndroidColor.WHITE)
                setPadding(0, 5, 0, 4)
            }
            val body = TextView(context).apply {
                textSize = 12f
                setTextColor(AndroidColor.LTGRAY)
                setPadding(0, 2, 0, 8)
            }
            val cta = TextView(context).apply {
                textSize = 12f
                setTextColor(AndroidColor.rgb(8, 9, 13))
                setBackgroundColor(AndroidColor.rgb(242, 185, 75))
                setPadding(18, 10, 18, 10)
            }
            container.addView(label)
            container.addView(headline)
            container.addView(body)
            container.addView(cta)
            addView(container)
            headlineView = headline
            bodyView = body
            callToActionView = cta
        }
    }
    var loadedAd by remember { mutableStateOf<NativeAd?>(null) }
    DisposableEffect(nativeAdView) {
        val loader = AdLoader.Builder(context, "ca-app-pub-8926612269781002/6968117968")
            .forNativeAd { ad ->
                loadedAd?.destroy()
                loadedAd = ad
                nativeAdView.headlineView?.let { (it as TextView).text = ad.headline }
                nativeAdView.bodyView?.let {
                    (it as TextView).text = ad.body.orEmpty()
                    it.visibility = if (ad.body.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
                }
                nativeAdView.callToActionView?.let {
                    (it as TextView).text = ad.callToAction.orEmpty()
                    it.visibility = if (ad.callToAction.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
                }
                nativeAdView.setNativeAd(ad)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            loadedAd?.destroy()
        }
    }
    if (loadedAd != null) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)),
            factory = { nativeAdView }
        )
    }
}

@Composable
private fun DrawerMenuRow(label: String, onClick: () -> Unit, textColor: Color = Color.White, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(58.dp).clickable { onClick() }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QisasApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("qisas_native", 0) }
    var pendingGoogleIdToken by remember { mutableStateOf<String?>(null) }
    var pendingGooglePhoto by remember { mutableStateOf("") }
    var showDisplayNameDialog by remember { mutableStateOf(false) }
    var newDisplayName by remember { mutableStateOf("") }
    var googleAuthBusy by remember { mutableStateOf(false) }
    var googleAuthMessage by remember { mutableStateOf<String?>(null) }
    val visitorId = remember { prefs.getString("visitor_id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("visitor_id", it).apply() } }
    var notificationMessage by remember { mutableStateOf<String?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }
    var interstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }
    var interstitialShowing by remember { mutableStateOf(false) }
    var listeningTimeSinceAd by remember { mutableLongStateOf(0L) }
    var lastEndAdEpisodeId by remember { mutableStateOf("") }
    fun loadInterstitial() {
        if (interstitialAd != null) return
        InterstitialAd.load(context, "ca-app-pub-8926612269781002/9594281306",
            AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
            })
    }
    fun showInterstitialThen(after: () -> Unit) {
        val ad = interstitialAd
        val activity = context as? Activity
        if (interstitialShowing) { after(); return }
        if (ad == null || activity == null || activity.isFinishing) {
            after()
            loadInterstitial()
            return
        }
        interstitialShowing = true
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { interstitialShowing = false; after(); loadInterstitial() }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) { interstitialShowing = false; after(); loadInterstitial() }
        }
        runCatching { ad.show(activity) }.onFailure {
            interstitialShowing = false
            after()
            loadInterstitial()
        }
    }
    LaunchedEffect(Unit) { loadInterstitial() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var categories by remember { mutableStateOf(emptyList<Category>()) }
    var stories by remember { mutableStateOf(emptyList<Story>()) }
    var latestUpdates by remember { mutableStateOf(emptyList<JSONObject>()) }
    var homeTab by remember { mutableStateOf("updates") }
    var homeListMode by remember { mutableStateOf(false) }
    var featuredStoryId by remember { mutableStateOf<String?>(null) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
    var actorVoteStats by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) } // likes, dislikes
    var actorMyVotes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var actorVotesLoading by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var episodes by remember { mutableStateOf(emptyList<Episode>()) }
    var comments by remember { mutableStateOf(emptyList<JSONObject>()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf("home") }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedStory by remember { mutableStateOf<Story?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                playing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var currentEpisodeIndex by remember { mutableIntStateOf(-1) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var commentName by remember { mutableStateOf(prefs.getString("display_name", "") ?: "") }
    var commentText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var replyParentId by remember { mutableStateOf<String?>(null) }
    var editingCommentId by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var history by remember { mutableStateOf(prefs.getStringSet("history", emptySet())?.toSet() ?: emptySet()) }
    var adminPanel by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf("أرغب بالاستماع إليها") }
    var storyLists by remember {
        mutableStateOf(runCatching {
            val saved = JSONObject(prefs.getString("story_lists", "{}") ?: "{}")
            saved.keys().asSequence().associateWith { key -> saved.optString(key).let { value -> if (value == "تمت مشاهدتها") "تم الاستماع إليها" else value } }
        }.getOrDefault(emptyMap()))
    }

    fun saveIds(key: String, ids: Set<String>) { prefs.edit().putStringSet(key, ids).apply() }
    fun rememberStory(story: Story) {
        history = (listOf(story.id) + history.filterNot { it == story.id }).take(100).toSet()
        saveIds("history", history)
    }
    fun loadCatalog() {
        scope.launch {
            loading = true; error = null
            try {
                val json = requestJson("/api/catalog")
                categories = json.optJSONArray("categories")?.toObjects()?.map {
                    Category(it.optString("id"), it.optString("title"), it.optString("image_path").takeIf { v -> v.isNotBlank() && v != "null" })
                } ?: emptyList()
                stories = json.optJSONArray("stories")?.toObjects()?.map {
                    Story(it.optString("id"), it.optString("title"), it.optString("description"), it.optString("audio_path"),
                        it.optString("image_path").takeIf { v -> v.isNotBlank() && v != "null" }, it.optString("status", "ongoing"),
                        it.optInt("views"), it.optJSONArray("category_ids")?.toStrings() ?: emptyList(),
                        it.optString("suggested_image_path").takeIf { v -> v.isNotBlank() && v != "null" }, it.optInt("total_episodes", 1),
                        it.optString("created_at"), it.optString("character_one_name", "البطلة"), it.optString("character_two_name", "البطل"), it.optString("character_three_name", "شخصية محورية"),
                        it.optString("character_one_image_path").takeIf { v -> v.isNotBlank() && v != "null" },
                        it.optString("character_two_image_path").takeIf { v -> v.isNotBlank() && v != "null" },
                        it.optString("character_three_image_path").takeIf { v -> v.isNotBlank() && v != "null" })
                } ?: emptyList()
                latestUpdates = requestJson("/api/latest-updates").optJSONArray("updates")?.toObjects() ?: emptyList()
                if (featuredStoryId == null && stories.isNotEmpty()) featuredStoryId = stories.random().id
            } catch (e: Exception) { error = e.localizedMessage ?: "تعذر الاتصال بالخادم" }
            loading = false
        }
    }
    fun openStory(story: Story) {
        selectedStory = story; page = "detail"; rememberStory(story); episodes = emptyList(); comments = emptyList(); message = null
        scope.launch {
            try {
                val parts = requestJson("/api/story/${story.id}/parts").optJSONArray("parts")?.toObjects() ?: emptyList()
                episodes = listOf(Episode("main-${story.id}", "الحلقة الأولى", story.audio, story.image, 1)) + parts.map {
                    Episode(it.optString("id"), it.optString("title"), it.optString("audio_path"), it.optString("image_path").takeIf { v -> v.isNotBlank() && v != "null" }, it.optInt("sort_order") + 1)
                }
            } catch (_: Exception) { episodes = listOf(Episode("main-${story.id}", "الحلقة الأولى", story.audio, story.image, 1)) }
            try {
                comments = requestJson("/api/story/${story.id}/comments", headers = mapOf("X-Visitor-ID" to visitorId)).optJSONArray("comments")?.toObjects() ?: emptyList()
            } catch (_: Exception) { /* comments are optional if the service is unavailable */ }
        }
        scope.launch { runCatching { requestJson("/api/view/${story.id}", "POST") } }
    }
    fun playEpisode(episode: Episode, showStartAd: Boolean = false) {
        val url = mediaUrl(episode.audio) ?: return
        fun prepareAndStart() {
            currentEpisodeIndex = episodes.indexOfFirst { it.id == episode.id }.let { if (it >= 0) it else 0 }
            lastEndAdEpisodeId = ""
            player.setMediaItem(MediaItem.fromUri(url)); player.setPlaybackSpeed(playbackSpeed); player.prepare()
            val savedPosition = prefs.getLong("progress_${selectedStory?.id}_${episode.id}", 0L)
            if (savedPosition > 0L) player.seekTo(savedPosition)
            playingTitle = "${selectedStory?.title.orEmpty()} — ${episode.title}"
            page = "player"
            if (showStartAd) {
                showInterstitialThen { player.play(); playing = true }
            } else {
                player.pause()
                playing = false
            }
        }
        prepareAndStart()
    }
    fun playAdjacent(direction: Int) {
        val next = currentEpisodeIndex + direction
        if (next in episodes.indices) playEpisode(episodes[next])
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            playbackPosition = player.currentPosition.coerceAtLeast(0L)
            playbackDuration = player.duration.takeIf { it > 0L } ?: 0L
            if (currentEpisodeIndex in episodes.indices && playbackPosition > 0L) {
                prefs.edit().putLong("progress_${selectedStory?.id}_${episodes[currentEpisodeIndex].id}", playbackPosition).apply()
            }
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            kotlinx.coroutines.delay(1000)
            if (playing && player.isPlaying) {
                listeningTimeSinceAd += 1000L
                if (listeningTimeSinceAd >= 300_000L) {
                    listeningTimeSinceAd = 0L
                    showInterstitialThen { }
                }
            }
        }
    }
    DisposableEffect(player, episodes, currentEpisodeIndex) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED &&
                    currentEpisodeIndex == episodes.lastIndex && currentEpisodeIndex in episodes.indices) {
                    playing = false
                    val episodeId = episodes[currentEpisodeIndex].id
                    if (lastEndAdEpisodeId != episodeId) {
                        lastEndAdEpisodeId = episodeId
                        showInterstitialThen { }
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    fun back() {
        when {
            adminPanel -> adminPanel = false
            page == "player" -> {
                player.pause(); player.stop(); playing = false; playingTitle = ""
                playbackPosition = 0L; playbackDuration = 0L; currentEpisodeIndex = -1
                page = "detail"
            }
            page == "detail" -> { page = "home"; selectedStory = null }
            page == "category" -> { page = "home"; selectedCategory = null }
            page == "latest-all" -> { page = "home"; homeTab = "updates" }
            page == "categories-all" -> { page = "home"; homeTab = "categories" }
            page == "actors" -> page = "home"
            page == "privacy" || page == "terms" || page == "cookies" || page == "disclaimer" -> page = "settings"
            else -> page = "home"
        }
    }

    DisposableEffect(page) {
        if (page != "player" && player.isPlaying) {
            player.pause()
            player.stop()
            playing = false
            playingTitle = ""
        }
        onDispose { }
    }

    BackHandler(enabled = adminPanel || page != "home") { back() }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp).fillMaxHeight(),
                drawerContainerColor = if (darkMode) Color(0xFF101116) else Color(0xFFF7F7F7)
            ) {
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                var drawerListsExpanded by remember { mutableStateOf(true) }
                fun closeDrawer() { scope.launch { drawerState.close() } }
                fun openList(label: String) {
                    selectedList = label
                    page = if (label == "المفضلة") "favorites" else "lists"
                    closeDrawer()
                }
                Column(
                    Modifier.fillMaxWidth().background(if (darkMode) Color(0xFF171820) else Color(0xFFE8E8E8)).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = Color(0xFFE8EDF1), fontSize = 34.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("تسجيل الدخول", color = if (darkMode) Color.White else Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { adminPanel = true; closeDrawer() })
                    }
                    Text("دخول المشرف لإدارة المحتوى", color = if (darkMode) Color(0xFFB9C4CC) else Color.DarkGray, fontSize = 13.sp)
                }
                Divider(color = Line)
                DrawerMenuRow("الرئيسية", { page = "home"; homeTab = "updates"; closeDrawer() })
                DrawerMenuRow("البحث عن قصة", { page = "search"; closeDrawer() })
                DrawerMenuRow("التصنيفات", { page = "home"; homeTab = "categories"; closeDrawer() })
                DrawerMenuRow("قائمتي", { drawerListsExpanded = !drawerListsExpanded }) {
                    Icon(if (drawerListsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "فتح قائمتي", tint = Gold)
                }
                if (drawerListsExpanded) {
                    listOf("المفضلة", "أرغب بالاستماع إليها", "أستمع إليها حالياً", "تم الاستماع إليها", "أكمل الاستماع لاحقاً").forEach { label ->
                        Row(Modifier.fillMaxWidth().height(46.dp).clickable { openList(label) }.padding(start = 30.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("•  $label", color = if (darkMode) Color(0xFFCFD9E1) else Color.DarkGray, fontSize = 15.sp)
                        }
                    }
                }
                DrawerMenuRow("سجل الاستماع", { page = "history"; closeDrawer() })
                DrawerMenuRow("الممثلون", { page = "actors"; closeDrawer() })
                Divider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                DrawerMenuRow("الإعدادات", { page = "settings"; closeDrawer() })
                Divider(color = Line, modifier = Modifier.padding(vertical = 8.dp))
                Text("© جميع الحقوق محفوظة — Dramio\nيُمنع نسخ أو إعادة نشر القصص والصور والصوتيات دون إذن.", color = if (darkMode) Muted else Color.DarkGray, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp))
                }
            }
        }
    ) {
    Column(Modifier.fillMaxSize().background(if (darkMode) Ink else Color(0xFFF8F8F8)).windowInsetsPadding(WindowInsets.systemBars)) {
    Column(Modifier.weight(1f).fillMaxWidth()) {
    LaunchedEffect(page, stories.size, visitorId) {
        if (page == "actors" && stories.isNotEmpty()) {
            actorVotesLoading = true
            runCatching { requestJson("/api/character-votes?visitor_id=$visitorId") }
                .onSuccess { response ->
                    val stats = mutableMapOf<String, Pair<Int, Int>>()
                    val mine = mutableMapOf<String, Int>()
                    response.optJSONArray("votes")?.toObjects()?.forEach { row ->
                        val key = "${row.optString("story_id")}::${row.optString("character_key")}"
                        stats[key] = row.optInt("likes") to row.optInt("dislikes")
                        mine[key] = row.optInt("my_vote")
                    }
                    actorVoteStats = stats
                    actorMyVotes = mine
                }
            actorVotesLoading = false
        }
    }

        if (!adminPanel && page == "home") {
            Row(Modifier.fillMaxWidth().height(62.dp).background(TopBar).border(width = 0.5.dp, color = Line).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "القائمة الجانبية", tint = Gold) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dramio", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                    Text("حكايات تُروى بالصوت", color = Muted, fontSize = 10.sp)
                }
                IconButton(onClick = { page = "search" }) { Icon(Icons.Default.Search, "بحث", tint = Color.White) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(TopBar).padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("الرئيسية", "التصنيفات", "الأكثر استماعاً", "الجديد", "المكتملة").forEachIndexed { index, label ->
                    val key = listOf("updates", "categories", "popular", "new", "completed")[index]
                    val selected = homeTab == key
                    Column(Modifier.clickable { homeTab = key; page = "home" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (selected) Gold else Color.White, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width(22.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Gold else Color.Transparent))
                    }
                }
            }
        } else if (page != "detail" && page != "player") {
            Row(Modifier.fillMaxWidth().background(TopBar).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }) { Icon(Icons.Default.ArrowBack, null, tint = Gold) }
                Text(if (adminPanel) "الإدارة" else if (page == "settings") "الإعدادات" else if (page == "category") selectedCategory?.title ?: "التصنيف" else "Dramio", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
            }
        }
        if (adminPanel) {
            AdminPanelScreen(onClose = { adminPanel = false }, onCatalogChanged = { loadCatalog() })
        } else {
            if (page !in setOf("detail", "player", "home")) NativeAdCard()
            when (page) {
            "player" -> {
                val story = selectedStory
                val episode = episodes.getOrNull(currentEpisodeIndex)
                if (story != null && episode != null) EpisodePlayerScreen(
                    story = story,
                    episode = episode,
                    episodes = episodes,
                    recommendations = stories.filter { it.id != story.id }.take(6),
                    position = playbackPosition,
                    duration = playbackDuration,
                    playing = playing,
                    speed = playbackSpeed,
                    onBack = { back() },
                    onSeek = { playbackPosition = it; player.seekTo(it) },
                    onToggle = { if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true } },
                    onPrevious = { playAdjacent(-1) },
                    onNext = { playAdjacent(1) },
                    onSpeed = { playbackSpeed = if (playbackSpeed >= 2f) 0.75f else playbackSpeed + 0.25f; player.setPlaybackSpeed(playbackSpeed) },
                    onSelectEpisode = { playEpisode(it) },
                    onOpenStory = { nextStory ->
                        player.pause(); player.stop(); playing = false; playingTitle = ""
                        openStory(nextStory)
                    }
                )
            }
            "detail" -> {
                val story = selectedStory
                if (story != null) StoryDetailScreen(story, categories, episodes, comments, commentName, { commentName = it; prefs.edit().putString("display_name", it).apply() }, commentText, { commentText = it }, message,
                    notificationsEnabled = prefs.getBoolean("story_notifications_${story.id}", false), notificationMessage = notificationMessage,
                    onToggleNotifications = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7341)
                            notificationMessage = "اسمح بالإشعارات من نافذة الهاتف، ثم اضغط الجرس مرة أخرى لتفعيل التنبيهات"
                        } else {
                            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                if (!task.isSuccessful || task.result.isNullOrBlank()) {
                                    notificationMessage = "تعذر تهيئة الإشعارات. تأكد من إضافة google-services.json وربط Firebase بالمشروع."
                                } else {
                                    scope.launch {
                                        try {
                                            requestJson("/api/notifications/subscribe", "POST", JSONObject()
                                                .put("visitor_id", visitorId).put("story_id", story.id).put("fcm_token", task.result).put("enabled", enable))
                                            prefs.edit().putBoolean("story_notifications_${story.id}", enable).apply()
                                            notificationMessage = if (enable) "تم تفعيل إشعارات هذه القصة" else "تم إلغاء متابعة إشعارات هذه القصة"
                                        } catch (e: Exception) { notificationMessage = e.localizedMessage ?: "تعذر حفظ إعداد الإشعارات" }
                                    }
                                }
                            }
                        }
                    },
                    onPlay = { playEpisode(it, false) }, onSendComment = {
                        if (commentText.isBlank()) message = "اكتبي تعليقك أولًا" else scope.launch {
                            busy = true
                            try {
                                val payload = JSONObject().put("visitor_id", visitorId).put("display_name", commentName.ifBlank { "زائر" }).put("content", commentText).put("avatar", "avatar-girl-1")
                                if (editingCommentId != null) {
                                    requestJson("/api/comment/$editingCommentId", "PUT", JSONObject().put("visitor_id", visitorId).put("content", commentText))
                                } else {
                                    replyParentId?.let { payload.put("parent_id", it) }
                                    requestJson("/api/story/${story.id}/comments", "POST", payload)
                                }
                                commentText = ""; replyParentId = null; editingCommentId = null
                                comments = requestJson("/api/story/${story.id}/comments", headers = mapOf("X-Visitor-ID" to visitorId)).optJSONArray("comments")?.toObjects() ?: emptyList()
                                message = "تم نشر تعليقك"
                            } catch (e: Exception) { message = e.localizedMessage ?: "تعذر نشر التعليق" }
                            busy = false
                        }
                    }, onLikeComment = { comment ->
                        scope.launch {
                            try {
                                requestJson("/api/comment/${comment.optString("id")}/like", "POST", JSONObject().put("visitor_id", visitorId))
                                comments = requestJson("/api/story/${story.id}/comments", headers = mapOf("X-Visitor-ID" to visitorId)).optJSONArray("comments")?.toObjects() ?: emptyList()
                            } catch (e: Exception) { message = e.localizedMessage ?: "تعذر تسجيل الإعجاب" }
                        }
                    }, onReplyComment = { comment -> editingCommentId = null; replyParentId = comment.optString("id"); message = "الرد على: ${comment.optString("display_name", "زائر")}" },
                    onEditComment = { comment -> editingCommentId = comment.optString("id"); replyParentId = null; commentText = comment.optString("content"); message = "تعديل تعليقك ثم اضغطي نشر" },
                    onDeleteComment = { comment -> scope.launch { try { requestJson("/api/comment/${comment.optString("id")}", "DELETE", JSONObject().put("visitor_id", visitorId)); comments = requestJson("/api/story/${story.id}/comments", headers = mapOf("X-Visitor-ID" to visitorId)).optJSONArray("comments")?.toObjects() ?: emptyList(); message = "تم حذف التعليق" } catch (e: Exception) { message = e.localizedMessage ?: "تعذر حذف التعليق" } } },
                    isFavorite = story.id in favorites, onFavorite = {
                        favorites = if (story.id in favorites) favorites - story.id else favorites + story.id
                        saveIds("favorites", favorites)
                    }, currentList = storyLists[story.id].orEmpty(), onSetList = { label ->
                        val updated = storyLists.toMutableMap()
                        if (label.isBlank()) updated.remove(story.id) else updated[story.id] = label
                        storyLists = updated.toMap()
                        prefs.edit().putString("story_lists", JSONObject().apply { updated.forEach { (id, value) -> put(id, value) } }.toString()).apply()
                    }, playingTitle = playingTitle, playing = playing, onTogglePlay = { if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true } },
                    onBack = { back() })
            }
            "category" -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "home"; homeTab = "categories"; selectedCategory = null }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Gold) }
                    Text(selectedCategory?.title ?: "التصنيف", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                val filtered = stories.filter { selectedCategory != null && selectedCategory!!.id in it.categoryIds }
                StoryList(filtered, onOpen = { openStory(it) })
            }
            "latest-all" -> {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "home"; homeTab = "updates" }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Gold) }
                    Text("أحدث المسلسلات", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                StoryList(stories.sortedByDescending { it.createdAt }, onOpen = { openStory(it) })
            }
            "categories-all" -> Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "home"; homeTab = "categories" }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Gold) }
                    Text("التصنيفات", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { gridItems(categories, key = { "category-all-${it.id}" }) { category -> CategoryCard(category) { selectedCategory = category; page = "category" } } }
            }
            "actors" -> {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { page = "home" }) { Icon(Icons.Default.ArrowBack, "رجوع", tint = Gold) }
                    Text("الممثلون والشخصيات", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                val actorRows = stories.flatMap { story -> listOf(
                    Triple("character_one", story.characterOneName, story.characterOneImage ?: story.image) to story,
                    Triple("character_two", story.characterTwoName, story.characterTwoImage ?: story.image) to story,
                    Triple("character_three", story.characterThreeName, story.characterThreeImage ?: story.image) to story
                ) }.filter { it.first.second.isNotBlank() }
                    .distinctBy { it.first.first + "::" + it.second.id }
                    .sortedWith(
                        compareByDescending<Pair<Triple<String, String, String?>, Story>> { row ->
                            actorVoteStats["${row.second.id}::${row.first.first}"]?.first ?: 0
                        }.thenByDescending { row ->
                            actorVoteStats["${row.second.id}::${row.first.first}"]?.second ?: 0
                        }
                    )
                if (actorVotesLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold)
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(actorRows, key = { it.first.first + "::" + it.second.id }) { (actor, actorStory) ->
                        val characterKey = actor.first
                        val actorName = actor.second
                        val actorImage = actor.third
                        val voteKey = "${actorStory.id}::$characterKey"
                        val stats = actorVoteStats[voteKey] ?: (0 to 0)
                        val myVote = actorMyVotes[voteKey] ?: 0
                        Card(colors = CardDefaults.cardColors(containerColor = if (darkMode) Panel else Color.White), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = mediaUrl(actorImage), contentDescription = actorName, contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).clickable { openStory(actorStory) })
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { openStory(actorStory) }) {
                                    Text(actorName, color = if (darkMode) Color.White else Color.Black, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text(actorStory.title, color = Muted, fontSize = 12.sp, maxLines = 2)
                                    Text("افتح القصة", color = Gold, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            runCatching { requestJson("/api/character-vote", "POST", JSONObject().put("story_id", actorStory.id.toIntOrNull() ?: 0).put("character_key", characterKey).put("visitor_id", visitorId).put("vote", if (myVote == 1) 0 else 1)) }
                                                .onSuccess { r ->
                                                    val newKey = "${r.optString("story_id")}::${r.optString("character_key")}"
                                                    actorVoteStats = actorVoteStats + (newKey to (r.optInt("likes") to r.optInt("dislikes")))
                                                    actorMyVotes = actorMyVotes + (newKey to r.optInt("my_vote"))
                                                }.onFailure { message = it.localizedMessage ?: "تعذر إرسال التصويت" }
                                        }
                                    }) { Icon(Icons.Default.ThumbUp, "إعجاب", tint = if (myVote == 1) Gold else Muted) }
                                    Text(stats.first.toString(), color = Muted, fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = {
                                        scope.launch {
                                            runCatching { requestJson("/api/character-vote", "POST", JSONObject().put("story_id", actorStory.id.toIntOrNull() ?: 0).put("character_key", characterKey).put("visitor_id", visitorId).put("vote", if (myVote == -1) 0 else -1)) }
                                                .onSuccess { r ->
                                                    val newKey = "${r.optString("story_id")}::${r.optString("character_key")}"
                                                    actorVoteStats = actorVoteStats + (newKey to (r.optInt("likes") to r.optInt("dislikes")))
                                                    actorMyVotes = actorMyVotes + (newKey to r.optInt("my_vote"))
                                                }.onFailure { message = it.localizedMessage ?: "تعذر إرسال التصويت" }
                                        }
                                    }) { Icon(Icons.Default.ThumbDown, "عدم إعجاب", tint = if (myVote == -1) Color.Red else Muted) }
                                    Text(stats.second.toString(), color = Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            "privacy" -> LegalPage("سياسة الخصوصية", "نحترم خصوصيتك. قد يحتفظ التطبيق بتفضيلاتك محلياً مثل المفضلة وسجل الاستماع، وقد يعالج بيانات ضرورية لتقديم الخدمة. لا تشارك معلومات حساسة عبر التعليقات. تواصل معنا لطلبات الخصوصية أو حذف البيانات المرتبطة بك.", darkMode)
            "terms" -> LegalPage("شروط الاستخدام", "باستخدام التطبيق، توافق على الالتزام بالقوانين واحترام حقوق الملكية الفكرية والخصوصية. يمنع نسخ المحتوى أو إعادة نشره أو استخدامه تجارياً دون موافقة مكتوبة. قد تتغير الخدمات أو تتوقف للصيانة، ويحق للإدارة إزالة المحتوى المخالف.", darkMode)
            "cookies" -> LegalPage("سياسة ملفات الارتباط", "قد يستخدم التطبيق أو موقعه ملفات تعريف الارتباط أو تقنيات مشابهة لتذكر التفضيلات وتحسين الأداء وقياس الاستخدام. يمكنك التحكم في ملفات الارتباط من إعدادات المتصفح عند استخدام الموقع. قد يؤدي تعطيلها إلى تأثر بعض الوظائف.", darkMode)
            "disclaimer" -> LegalPage("إخلاء المسؤولية", "المحتوى المنشور يهدف إلى الترفيه. نبذل جهداً للحفاظ على دقة المعلومات وتوفر الخدمة، لكن لا نضمن خلوها من الأخطاء أو الانقطاع. حقوق القصص والصور والصوتيات محفوظة لأصحابها. يرجى التواصل معنا عند وجود ادعاء حقوقي ليتم مراجعته.", darkMode)
            "search" -> {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp), label = { Text("ابحث عن قصة") }, singleLine = true)
                StoryList(stories.filter { it.title.contains(query, true) || it.description.contains(query, true) }, onOpen = { openStory(it) })
            }
            "account" -> {
                AccountScreen(
                    displayName = prefs.getString("google_display_name", null),
                    email = prefs.getString("google_email", null),
                    photoUrl = prefs.getString("google_photo", null),
                    onSignIn = {
                        if (!googleAuthBusy) scope.launch {
                            googleAuthBusy = true
                            googleAuthMessage = null
                            try {
                                val googleOption = GetGoogleIdOption.Builder()
                                    .setFilterByAuthorizedAccounts(false)
                                    .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                                    .setAutoSelectEnabled(false)
                                    .build()
                                val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
                                val result = CredentialManager.create(context).getCredential(context, request)
                                val credential = result.credential
                                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                    val googleToken = GoogleIdTokenCredential.createFrom(credential.data)
                                    pendingGoogleIdToken = googleToken.idToken
                                    pendingGooglePhoto = googleToken.profilePictureUri?.toString().orEmpty()
                                    newDisplayName = googleToken.displayName.orEmpty()
                                    showDisplayNameDialog = true
                                } else throw IllegalStateException("لم يتم استلام بيانات حساب Google")
                            } catch (e: Exception) {
                                googleAuthMessage = when (e) {
                                    is GoogleIdTokenParsingException -> "تعذر قراءة بيانات حساب Google"
                                    else -> e.localizedMessage ?: "تعذر تسجيل الدخول باستخدام Google"
                                }
                            } finally { googleAuthBusy = false }
                        }
                    },
                    onSignOut = { prefs.edit().remove("google_display_name").remove("google_email").remove("google_photo").remove("google_id_token").remove("google_sub").apply(); message = "تم تسجيل الخروج من هذا الجهاز" }
                )
                if (showDisplayNameDialog) {
                    AlertDialog(
                        onDismissRequest = { showDisplayNameDialog = false; pendingGoogleIdToken = null },
                        title = { Text("اختر اسمك في Dramio", color = Color.White) },
                        text = { Column {
                            Text("اكتب الاسم الذي سيظهر للآخرين داخل التطبيق.", color = Muted)
                            OutlinedTextField(value = newDisplayName, onValueChange = { newDisplayName = it.take(40) }, label = { Text("اسم المستخدم") }, singleLine = true)
                            googleAuthMessage?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
                        } },
                        confirmButton = { TextButton(enabled = newDisplayName.trim().length >= 2 && !googleAuthBusy, onClick = {
                            val token = pendingGoogleIdToken ?: return@TextButton
                            scope.launch {
                                googleAuthBusy = true
                                googleAuthMessage = null
                                try {
                                    val response = requestJson("/api/auth/google", "POST", JSONObject().put("id_token", token).put("display_name", newDisplayName.trim()))
                                    val user = response.optJSONObject("user") ?: throw IllegalStateException("استجابة الخادم غير مكتملة")
                                    prefs.edit().putString("google_display_name", user.optString("display_name", newDisplayName.trim()))
                                        .putString("google_email", user.optString("email"))
                                        .putString("google_sub", user.optString("google_sub")).putString("google_photo", pendingGooglePhoto.orEmpty()).apply()
                                    pendingGoogleIdToken = null
                                    showDisplayNameDialog = false
                                    message = "مرحباً بك في Dramio"
                                } catch (e: Exception) { googleAuthMessage = e.localizedMessage ?: "تعذر إنشاء الحساب" }
                                finally { googleAuthBusy = false }
                            }
                        }) { Text(if (googleAuthBusy) "جارٍ التحقق…" else "إنشاء الحساب", color = Gold) } },
                        dismissButton = { TextButton(onClick = { showDisplayNameDialog = false; pendingGoogleIdToken = null }) { Text("إلغاء", color = Muted) } },
                        containerColor = Panel
                    )
                }
                googleAuthMessage?.takeIf { !showDisplayNameDialog }?.let { Text(it, color = Color.Red, modifier = Modifier.padding(16.dp)) }
            }
            "settings" -> SettingsScreen(onOpenLegal = { page = it }, darkMode = darkMode)
            "favorites", "history" -> {
                Text(if (page == "favorites") "المفضلة" else "سجل الاستماع", color = Gold, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                val ids = if (page == "favorites") favorites else history
                StoryList(stories.filter { it.id in ids }, onOpen = { openStory(it) })
            }
            "lists" -> {
                Text("قائمتي", color = Gold, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("أرغب بالاستماع إليها", "أستمع إليها حالياً", "تم الاستماع إليها", "أكمل الاستماع لاحقاً").forEach { label ->
                        TextButton(onClick = { selectedList = label }) { Text(label, color = if (selectedList == label) Gold else Muted, fontSize = 10.sp) }
                    }
                }
                StoryList(stories.filter { storyLists[it.id] == selectedList }, onOpen = { openStory(it) })
            }
            else -> {
                if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                else if (error != null) Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(error!!, color = Color.White); Button(onClick = { loadCatalog() }) { Text("إعادة المحاولة") } }
                else Column(Modifier.fillMaxSize()) {
                    if (homeTab == "categories") {
                        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            gridItems(categories, key = { "category-${it.id}" }) { category -> CategoryCard(category) { selectedCategory = category; page = "category" } }
                        }
                    } else {
                        val featured = stories.firstOrNull { it.id == featuredStoryId } ?: stories.firstOrNull()
                        LaunchedEffect(stories.size) { if (stories.size > 1) { while (true) { kotlinx.coroutines.delay(6500); featuredStoryId = stories.filterNot { it.id == featuredStoryId }.randomOrNull()?.id } } }
                        PullToRefreshBox(isRefreshing = loading, onRefresh = { loadCatalog() }, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            item {
                                AnimatedContent(targetState = featured, transitionSpec = {
                                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                }, label = "featured-carousel") { current ->
                                    if (current != null) FeaturedSeriesCard(current) { openStory(current) }
                                }
                            }
                            if (homeTab == "updates" || homeTab == "new") {
                                item { SectionHeading(if (homeTab == "new") "الجديد على Dramio" else "أحدث القصص", "اكتشف حكايات جديدة", "عرض الكل") { page = "latest-all" } }
                                item {
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        stories.sortedByDescending { it.createdAt }.forEach { item -> CompactPosterCard(item) { openStory(item) } }
                                    }
                                }
                                item { SectionHeading("آخر التحديثات", "أحدث الحلقات المضافة") }
                                item {
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        latestUpdates.distinctBy { it.optString("story_id") + "-" + it.optString("episode_number") }.take(16).forEach { update ->
                                            val target = stories.firstOrNull { it.id == update.optString("story_id") }
                                            if (target != null) Box(Modifier.width(122.dp)) { LatestUpdateCard(update, target) { openStory(target) } }
                                        }
                                    }
                                }
                                item { NativeAdCard() }
                                categories.forEach { category ->
                                    val categoryStories = stories.filter { category.id in it.categoryIds }
                                    if (categoryStories.isNotEmpty()) {
                                        item { SectionHeading(category.title, "حكايات من تصنيف ${category.title}", "عرض الكل") { selectedCategory = category; page = "category" } }
                                        item {
                                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                                categoryStories.forEach { storyItem -> CompactPosterCard(storyItem) { openStory(storyItem) } }
                                            }
                                        }
                                    }
                                }
                            } else if (homeTab == "popular") {
                                item { SectionHeading("الأكثر استماعاً", "القصص التي يفضلها المستمعون") }
                                item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) { stories.sortedByDescending { it.views }.forEach { CompactPosterCard(it) { openStory(it) } } } }
                            } else if (homeTab == "completed") {
                                item { SectionHeading("قصص مكتملة", "استمتع بالحكاية من البداية إلى النهاية") }
                                items(stories.filter { it.status == "completed" }) { storyItem -> StoryCard(storyItem) { openStory(storyItem) } }
                            }
                            if (stories.isEmpty() && !loading) item { Text("لا توجد قصص منشورة حالياً", color = Muted, modifier = Modifier.padding(24.dp)) }
                        }
                        }
                    }
                }
            }
            }
        }
    } // end weighted page area; keeps the ad and bottom navigation visible
        if (!adminPanel && page !in setOf("player", "detail")) {
            AdMobBanner()
        }
        if (!adminPanel && page !in setOf("player", "detail")) {
            Row(Modifier.fillMaxWidth().height(62.dp).background(TopBar).border(0.5.dp, Line), verticalAlignment = Alignment.CenterVertically) {
                listOf("الرئيسية", "التصنيفات", "المفضلة", "بحث", "حسابي").forEachIndexed { index, label ->
                    Column(Modifier.weight(1f).fillMaxHeight().clickable {
                        when (index) {
                            0 -> { page = "home"; homeTab = "updates" }
                            1 -> { page = "home"; homeTab = "categories" }
                            2 -> page = "favorites"
                            3 -> page = "search"
                            else -> page = "account"
                        }
                    }.padding(top = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        val selected = when (index) { 0 -> page == "home" && homeTab == "updates"; 1 -> page == "home" && homeTab == "categories"; 2 -> page == "favorites"; 3 -> page == "search"; else -> page == "account" }
                        when (index) {
                            0 -> Icon(Icons.Default.Home, null, tint = if (selected) Gold else Muted)
                            1 -> Icon(Icons.Default.GridView, null, tint = if (selected) Gold else Muted)
                            2 -> Icon(Icons.Default.Favorite, null, tint = if (selected) Gold else Muted)
                            3 -> Icon(Icons.Default.Search, null, tint = if (selected) Gold else Muted)
                            else -> Icon(Icons.Default.Person, null, tint = if (selected) Gold else Muted)
                        }
                        Text(label, color = if (selected) Gold else Muted, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
    }
    LaunchedEffect(Unit) { loadCatalog() }
}


@Composable
private fun EpisodePlayerScreen(
    story: Story,
    episode: Episode,
    episodes: List<Episode>,
    recommendations: List<Story>,
    position: Long,
    duration: Long,
    playing: Boolean,
    speed: Float,
    onBack: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onOpenStory: (Story) -> Unit
) {
    var showAllEpisodes by remember(story.id) { mutableStateOf(false) }
    val visibleEpisodes = if (showAllEpisodes) episodes else episodes.take(4)
    val progress = if (duration > 0L) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF101116)).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "العودة", tint = Gold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("يُشغَّل الآن", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("DRAMIO AUDIO", color = Color.White, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Gold), contentAlignment = Alignment.Center) {
                    Text("D", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth(0.78f).aspectRatio(0.88f)
                        .clip(RoundedCornerShape(24.dp)).background(Panel)
                        .border(1.dp, Color(0xFF343640), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = mediaUrl(episode.image ?: story.image),
                        contentDescription = story.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(54.dp).background(Color(0x9908090D)))
                    Text("DRAMIO  •  قصة صوتية", Modifier.align(Alignment.BottomCenter).padding(bottom = 17.dp), color = Color.White, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                Text(story.title, color = Color.White, fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Spacer(Modifier.height(7.dp))
                Text("الحلقة ${episode.number}  ·  ${episode.title}", color = Muted, fontSize = 13.sp, maxLines = 1)
                Spacer(Modifier.height(18.dp))
                Slider(
                    value = progress,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Color(0xFF343640)),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(position), color = Color(0xFFD7D7DC), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(formatPlayerTime(duration), color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onSpeed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${speed}x", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("السرعة", color = Muted, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = onPrevious, enabled = episode.number > 1, modifier = Modifier.size(52.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "الحلقة السابقة", tint = if (episode.number > 1) Color.White else Muted, modifier = Modifier.size(29.dp))
                            Text("السابق", color = Muted, fontSize = 9.sp)
                        }
                    }
                    Box(
                        Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Gold).clickable(onClick = onToggle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (playing) "إيقاف مؤقت" else "تشغيل", tint = Ink, modifier = Modifier.size(43.dp))
                    }
                    IconButton(onClick = onNext, enabled = episode.number < episodes.size, modifier = Modifier.size(52.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SkipNext, contentDescription = "الحلقة التالية", tint = if (episode.number < episodes.size) Color.White else Muted, modifier = Modifier.size(29.dp))
                            Text("التالي", color = Muted, fontSize = 9.sp)
                        }
                    }
                    TextButton(onClick = { showAllEpisodes = !showAllEpisodes }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ViewList, contentDescription = "قائمة الحلقات", tint = Gold, modifier = Modifier.size(22.dp))
                            Text("الحلقات", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("قائمة الحلقات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("تابع الاستماع من الحلقة التي تفضلها", color = Muted, fontSize = 11.sp)
                }
                Text("${episodes.size} حلقات", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        visibleEpisodes.forEach { part ->
            item(key = "player-${part.id}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (part.id == episode.id) Color(0xFF29230F) else Panel)
                        .border(if (part.id == episode.id) 1.dp else 0.dp, if (part.id == episode.id) Gold.copy(alpha = 0.65f) else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onSelectEpisode(part) }.padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = mediaUrl(part.image ?: story.image), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(11.dp))
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("الحلقة ${part.number}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(part.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (part.id == episode.id) Text(if (playing) "قيد التشغيل الآن" else "الحلقة الحالية", color = Muted, fontSize = 10.sp)
                    }
                    Icon(if (part.id == episode.id && playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "تشغيل الحلقة", tint = Gold, modifier = Modifier.size(27.dp))
                }
            }
        }
        if (episodes.size > 4) {
            item {
                TextButton(onClick = { showAllEpisodes = !showAllEpisodes }, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Text(if (showAllEpisodes) "عرض حلقات أقل" else "عرض جميع الحلقات (${episodes.size})", color = Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (recommendations.isNotEmpty()) {
            item {
                Text("قد يعجبك أيضاً", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp))
            }
            items(recommendations, key = { "suggested-${it.id}" }) { suggested ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)
                        .clip(RoundedCornerShape(15.dp)).background(Panel)
                        .clickable { onOpenStory(suggested) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = mediaUrl(suggested.suggestedImage ?: suggested.image), contentDescription = suggested.title, contentScale = ContentScale.Crop, modifier = Modifier.size(68.dp, 82.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(suggested.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text("قصة صوتية", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Gold)
                }
            }
        }
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val total = (milliseconds.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun SectionHeading(title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, color = Gold, fontSize = 12.sp) }
    }
}

@Composable
private fun FeaturedSeriesCard(story: Story, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(350.dp).clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = mediaUrl(story.suggestedImage ?: story.image),
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.48f to Color.Transparent,
                        0.72f to Color(0x66000000),
                        1f to Color(0xE6000000)
                    )
                )
            )
        )
        Column(
            Modifier.align(Alignment.BottomEnd).fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("اختيار Dramio", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(
                story.title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "${story.totalEpisodes.coerceAtLeast(1)} حلقة  •  ${if (story.status == "completed") "مكتملة" else "مستمرة"}",
                color = Color(0xFFE4E1DC),
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactPosterCard(story: Story, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(12.dp))) {
            AsyncImage(model = mediaUrl(story.image), contentDescription = story.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xDD08090D)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("${story.totalEpisodes.coerceAtLeast(1)} حلقة", color = Gold, fontSize = 10.sp)
            }
        }
        Text(story.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 2, modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp).padding(top = 8.dp))
        Text(if (story.status == "completed") "مكتمل" else "مستمر", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HomeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
        Text(label, color = if (selected) Color(0xFFF2C94C) else Color(0xFFB9B9C0), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (selected) Color(0xFFF2C94C) else Color.Transparent))
    }
}

@Composable
private fun LatestUpdateCard(update: JSONObject, story: Story, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF15243A)).border(1.dp, Color(0xFF2A3C52), RoundedCornerShape(10.dp))) {
            AsyncImage(model = mediaUrl(update.optString("image_path").takeIf { it.isNotBlank() && it != "null" } ?: story.image), contentDescription = story.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text("الحلقة ${update.optInt("episode_number", 1)}", modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color(0xE6071220), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp), color = Color(0xFFF2F3F8), fontSize = 9.sp, maxLines = 1)
        }
        Text(story.title, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), color = Color(0xFFF4F5F9), fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, maxLines = 2)
        Text(update.optString("category_title", "غير مصنفة"), modifier = Modifier.fillMaxWidth(), color = Color(0xFF9EACC1), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun LatestUpdateListCard(update: JSONObject, story: Story, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = mediaUrl(update.optString("image_path").takeIf { it.isNotBlank() && it != "null" } ?: story.image), contentDescription = story.title, modifier = Modifier.width(92.dp).aspectRatio(3f / 4f).background(Panel, RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(story.title, color = Color(0xFFF4F5F9), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("الحلقة ${update.optInt("episode_number", 1)}", color = Gold, fontSize = 12.sp)
            Text(update.optString("category_title", "غير مصنفة"), color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.PlayArrow, null, tint = Gold)
    }
}

@Composable
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    Column(Modifier.width(148.dp).clip(RoundedCornerShape(16.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(bottom = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(model = mediaUrl(category.image), contentDescription = category.title, modifier = Modifier.fillMaxWidth().aspectRatio(1.15f), contentScale = ContentScale.Crop)
        Text(category.title, color = Color(0xFFF5F1E8), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 13.dp), maxLines = 2)
    }
}

@Composable
private fun StoryCard(story: Story, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = mediaUrl(story.image), contentDescription = null, modifier = Modifier.size(88.dp), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(story.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(if (story.status == "completed") "مكتملة" else "مستمرة", color = Gold, fontSize = 12.sp)
            Text("${story.totalEpisodes} حلقة  •  ${story.views} مشاهدة", color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.PlayArrow, null, tint = Gold)
    }
}

@Composable
private fun StoryList(storiesToShow: List<Story>, onOpen: (Story) -> Unit) {
    if (storiesToShow.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا توجد قصص هنا حاليًا", color = Muted) }
    else LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        storiesToShow.forEachIndexed { index, story ->
            item(key = "story-${story.id}") { StoryCard(story) { onOpen(story) } }
            if ((index + 1) % 6 == 0 && index < storiesToShow.lastIndex) {
                item(key = "native-story-ad-$index") { NativeAdCard() }
            }
        }
    }
}

@Composable
private fun StoryDetailScreen(
    story: Story, categories: List<Category>, episodes: List<Episode>, comments: List<JSONObject>, name: String, onName: (String) -> Unit,
    comment: String, onComment: (String) -> Unit, message: String?, onPlay: (Episode) -> Unit,
    onSendComment: () -> Unit, onLikeComment: (JSONObject) -> Unit, onReplyComment: (JSONObject) -> Unit,
    onEditComment: (JSONObject) -> Unit, onDeleteComment: (JSONObject) -> Unit,
    isFavorite: Boolean, onFavorite: () -> Unit, currentList: String, onSetList: (String) -> Unit,
    playingTitle: String, playing: Boolean, onTogglePlay: () -> Unit, onBack: () -> Unit,
    notificationsEnabled: Boolean, notificationMessage: String?, onToggleNotifications: (Boolean) -> Unit
) {
    var selectedTab by remember(story.id) { mutableStateOf("details") }
    var descriptionExpanded by remember(story.id) { mutableStateOf(false) }
    val detailBg = Color(0xFF090A0F)
    val detailText = Color(0xFFF6F4F1)
    val detailMuted = Color(0xFF9A9BA5)
    val teal = Gold
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFF08090D)), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().background(detailBg).heightIn(min = 66.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = detailText) }
                Text(story.title, color = detailText, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 2)
                IconButton(onClick = { onToggleNotifications(!notificationsEnabled) }) {
                    Icon(Icons.Default.Notifications, contentDescription = if (notificationsEnabled) "إلغاء إشعارات القصة" else "تفعيل إشعارات القصة", tint = if (notificationsEnabled) Gold else detailText)
                }
                Text(if (isFavorite) "♥" else "♡", color = Color(0xFFFF6B82), fontSize = 25.sp, modifier = Modifier.clickable { onFavorite() }.padding(start = 6.dp))
            }
        }
        if (notificationMessage != null) item {
            Text(notificationMessage, color = if (notificationsEnabled) Gold else detailMuted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 9.dp))
        }
        item {
            Box(Modifier.fillMaxWidth().height(215.dp)) {
                AsyncImage(model = mediaUrl(story.suggestedImage ?: story.image), contentDescription = story.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xF5000000)))))
                Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                    Text("مسلسل صوتي", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(story.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    Text("${story.totalEpisodes.coerceAtLeast(1)} حلقة  •  ${story.views} استماع", color = Color.White, fontSize = 11.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(detailBg).border(width = 0.5.dp, color = Line), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("details" to "التفاصيل", "episodes" to "الحلقات", "comments" to "التعليقات").forEach { (key, label) ->
                    Column(Modifier.weight(1f).clickable { selectedTab = key }.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = if (selectedTab == key) Color.White else Color(0xFFA9BAC4), fontSize = 15.sp, fontWeight = if (selectedTab == key) FontWeight.Bold else FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(0.72f).height(4.dp).background(if (selectedTab == key) Color.White else Color.Transparent, RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
        item { NativeAdCard() }
        if (selectedTab == "details") {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        AsyncImage(model = mediaUrl(story.image), contentDescription = story.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(112.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).border(1.dp, Line, RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f).padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(story.title, color = detailText, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (story.status == "completed") "مكتملة" else "مستمرة", color = Color.White, fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(Gold, RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 4.dp), fontSize = 12.sp)
                            Text("${story.totalEpisodes.coerceAtLeast(1)} حلقة", color = detailText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (story.createdAt.isNotBlank()) Text(story.createdAt.take(10), color = detailMuted, fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 82.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text("★", color = Color(0xFFFFB321), fontSize = 24.sp); Text("4.8", color = detailText, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("/5", color = detailText) }
                            Text("التقييم", color = detailMuted, fontSize = 12.sp)
                        }
                        Box(Modifier.width(1.dp).height(55.dp).background(Line))
                        var listMenu by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(Modifier.fillMaxWidth().clickable { listMenu = true }.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("＋", color = detailText, fontSize = 27.sp, lineHeight = 27.sp)
                                Text("قائمتي", color = detailText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(currentList.ifBlank { "إضافة إلى قائمتي" }, color = detailMuted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2)
                            }
                            DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                                listOf("أرغب بالاستماع إليها", "أستمع إليها حالياً", "تم الاستماع إليها", "أكمل الاستماع لاحقاً", "إزالة من قائمتي").forEach { label ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { onSetList(if (label == "إزالة من قائمتي") "" else label); listMenu = false })
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().background(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF171820), Color(0xFF101116))), shape = RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text("وصف القصة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(story.description.ifBlank { "لا يوجد وصف لهذه القصة حتى الآن." }, color = Color(0xFFE7EDF1), fontSize = 12.sp, lineHeight = 18.sp, maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3)
                        if (story.description.length > 120) {
                            TextButton(onClick = { descriptionExpanded = !descriptionExpanded }, contentPadding = PaddingValues(0.dp)) {
                                Text(if (descriptionExpanded) "▲ إخفاء" else "▼ عرض المزيد", color = teal, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        story.categoryIds.forEach { id ->
                            val categoryName = categories.firstOrNull { it.id == id }?.title ?: id
                            Text(categoryName, color = Color(0xFFE0E9EF), fontSize = 11.sp, modifier = Modifier.background(Color(0xFF29232A), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
            val characters = listOf(Triple(story.characterOneName, story.characterOneImage, "one"), Triple(story.characterTwoName, story.characterTwoImage, "two"), Triple(story.characterThreeName, story.characterThreeImage, "three")).filter { it.second != null }
            if (characters.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("الشخصيات الرئيسية", color = detailText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            characters.forEach { (characterName, image, _) ->
                                Column(Modifier.weight(1f)) {
                                    AsyncImage(model = mediaUrl(image), contentDescription = characterName, contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFF233C4C), RoundedCornerShape(8.dp)))
                                    Text(characterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (selectedTab == "episodes") {
            if (episodes.isEmpty()) item { Text("لا توجد حلقات منشورة لهذه القصة حتى الآن.", color = detailMuted, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            episodes.forEachIndexed { index, episode ->
                item(key = "detail-episode-${episode.id}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).background(Color(0xFF171820), RoundedCornerShape(13.dp)).clickable { onPlay(episode) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = mediaUrl(episode.image ?: story.image), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(62.dp).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, Line, RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("الحلقة ${episode.number}", color = teal, fontSize = 12.sp); Text(episode.title, color = detailText, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = { onPlay(episode) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل الحلقة", tint = teal, modifier = Modifier.size(30.dp))
                    }
                }
                }
                if ((index + 1) % 6 == 0 && index < episodes.lastIndex) {
                    item(key = "native-detail-episode-ad-$index") { NativeAdCard() }
                }
            }
        }
        if (selectedTab == "comments") {
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("التعليقات (${comments.size})", color = detailText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, onName, modifier = Modifier.fillMaxWidth(), label = { Text("اسمك") }, singleLine = true)
                    OutlinedTextField(comment, onComment, modifier = Modifier.fillMaxWidth(), label = { Text("اكتب تعليقك هنا...") }, minLines = 2)
                    Button(onClick = onSendComment, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = teal)) { Text("نشر التعليق", color = Color(0xFF06222B), fontWeight = FontWeight.Bold) }
                    message?.let { Text(it, color = Gold) }
                    if (comments.isEmpty()) Text("لا توجد تعليقات بعد", color = detailMuted)
                }
            }
            items(comments.filter { it.isNull("parent_id") || it.optString("parent_id").isBlank() || it.optString("parent_id") == "null" }, key = { it.optString("id") }) { cm ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).background(Color(0xFF171820), RoundedCornerShape(12.dp)).padding(13.dp)) {
                    Text(cm.optString("display_name", "زائر"), color = Gold, fontWeight = FontWeight.Bold)
                    Text(cm.optString("content"), color = detailText, lineHeight = 23.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("♥ ${cm.optInt("likes_count")}", color = detailMuted, fontSize = 12.sp)
                        TextButton(onClick = { onLikeComment(cm) }) { Text(if (cm.optBoolean("liked_by_me")) "أعجبني ✓" else "إعجاب", color = Gold, fontSize = 12.sp) }
                        TextButton(onClick = { onReplyComment(cm) }) { Text("رد", color = Gold, fontSize = 12.sp) }
                        if (cm.optBoolean("is_mine")) {
                            TextButton(onClick = { onEditComment(cm) }) { Text("تعديل", color = Gold, fontSize = 12.sp) }
                            TextButton(onClick = { onDeleteComment(cm) }) {
                                Text(
                                    text = "حذف",
                                    color = Color(0xFFFF9B9B),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    comments.filter { it.optString("parent_id") == cm.optString("id") }.forEach { reply ->
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 7.dp).background(Color(0xFF101116), RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Text(reply.optString("display_name", "زائر"), color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(reply.optString("content"), color = detailText, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SettingsScreen(onOpenLegal: (String) -> Unit, darkMode: Boolean) {
    val context = LocalContext.current
    val textColor = if (darkMode) Color(0xFFE2E8ED) else Color(0xFF222222)
    val cardColor = if (darkMode) Color(0xFF1A2B35) else Color(0xFFFFFFFF)
    LazyColumn(Modifier.fillMaxSize().background(if (darkMode) Ink else Color(0xFFF8F8F8)), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("الإعدادات", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("المعلومات والمساعدة", color = if (darkMode) Muted else Color.Gray, fontSize = 14.sp) }
        val links = listOf("سياسة الخصوصية" to "privacy", "شروط الاستخدام" to "terms", "سياسة ملفات الارتباط" to "cookies", "إخلاء المسؤولية" to "disclaimer")
        items(links) { (title, route) ->
            Row(Modifier.fillMaxWidth().background(cardColor, RoundedCornerShape(12.dp)).clickable { onOpenLegal(route) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Gold)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(cardColor, RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اتصل بنا", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("صفحتنا الرسمية على فيسبوك", color = textColor, fontSize = 14.sp)
                Text("https://www.facebook.com/share/1DzsRbjYZK/", color = Color(0xFF42C6C2), fontSize = 14.sp, modifier = Modifier.clickable {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.facebook.com/share/1DzsRbjYZK/"))) }
                })
            }
        }
        item { Text("© جميع الحقوق محفوظة — Dramio", color = if (darkMode) Muted else Color.Gray, fontSize = 12.sp) }
    }
}

@Composable
private fun LegalPage(title: String, body: String, darkMode: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(body, color = if (darkMode) Color.White else Color(0xFF222222), fontSize = 15.sp, lineHeight = 25.sp)
    }
}
