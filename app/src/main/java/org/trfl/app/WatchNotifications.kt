package org.trfl.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TrflApplication : Application() {
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "watchNotifications" || key == "watched") WatchNotifications.schedule(this)
        if (key == "watchNotifications" || key == "watched" || key?.startsWith("seen:") == true || key?.startsWith("total:") == true) {
            WatchNotifications.update(this)
        }
    }
    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("trfl", 0).registerOnSharedPreferenceChangeListener(listener)
        WatchNotifications.schedule(this)
        WatchNotifications.update(this)
    }
}

object WatchNotifications {
    private const val CHANNEL = "watched_replies"
    private const val ID = 101
    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("trfl", 0)
        val manager = WorkManager.getInstance(context)
        if (!shouldCheckWatchedThreads(prefs.getStringSet("watched", emptySet()).orEmpty())) {
            manager.cancelUniqueWork("watch-replies")
            return
        }
        val work = PeriodicWorkRequestBuilder<WatchRepliesWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork("watch-replies", ExistingPeriodicWorkPolicy.KEEP, work)
    }
    fun update(context: Context) {
        val prefs = context.getSharedPreferences("trfl", 0)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Watched replies", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "New replies in watched threads"
            setShowBadge(true)
        })
        val watched = prefs.getStringSet("watched", emptySet()).orEmpty().toSet()
        val unread = watched.sumOf { entry ->
            val total = prefs.getInt("total:$entry", 0)
            unreadReplies(total, if (prefs.contains("seen:$entry")) prefs.getInt("seen:$entry", total) else null)
        }
        if (!prefs.getBoolean("watchNotifications", false) || unread == 0) {
            manager.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val entry = watched.sorted().first { prefs.getInt("total:$it", 0) > prefs.getInt("seen:$it", prefs.getInt("total:$it", 0)) }
        val intent = Intent(context, MainActivity::class.java).apply {
            data = android.net.Uri.parse("https://boards.4chan.org/${entry.substringBefore('/')}/thread/${entry.substringAfter('/')}")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_watch_notification)
            .setContentTitle("New watched replies")
            .setContentText("$unread new replies in your watched threads")
            .setContentIntent(pending).setNumber(unread).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(false).build())
    }
}

class WatchRepliesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("trfl", 0)
        ApiRequests.initialize(applicationContext.cacheDir)
        val watched = prefs.getStringSet("watched", emptySet()).orEmpty().toSet()
        val dead = prefs.getStringSet("dead", emptySet()).orEmpty().toSet()
        for (entry in watched - dead) {
            if (isStopped) break
            if (!entry.matches(Regex("[a-z0-9]+/[0-9]+"))) continue
            try {
                val posts = JSONObject(request("${entry.substringBefore('/')}/thread/${entry.substringAfter('/')}.json", force = true)).getJSONArray("posts")
                if (entry in prefs.getStringSet("watched", emptySet()).orEmpty()) {
                    val count = posts.length()
                    val editor = prefs.edit().putInt("total:$entry", count)
                    if (!prefs.contains("seen:$entry")) editor.putInt("seen:$entry", count)
                    editor.apply()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the last known state on network failures. */ }
            delay(1100)
        }
        WatchNotifications.update(applicationContext)
        return Result.success()
    }
}

@Composable
fun WatchNotificationSetting() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trfl", 0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("watchNotifications", false)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = granted
        prefs.edit().putBoolean("watchNotifications", granted).apply()
    }
    SettingsToggle("Background reply notifications", "Watched threads are checked about every 15 minutes; notifications and launcher dots are optional", enabled) { value ->
        if (value && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enabled = value
            prefs.edit().putBoolean("watchNotifications", value).apply()
        }
    }
}
