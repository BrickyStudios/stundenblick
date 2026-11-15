package io.github.brickystudios.stundenblick

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

/**
 * K7: once an hour, only with network, only the own plan, only the next 7 days.
 * Untis sees one request per week of the plan per hour, less than the official app's refresh.
 */
class ChangeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "stundenblick-changes") {
            val repo = Repo.get(this)
            val messages = runCatching { repo.checkChanges() }.getOrDefault(emptyList()) // offline: try again next hour, no false alarm
            if (messages.isNotEmpty()) notifyChanges(this, messages)
            DayWidget.update(this)
            BreakInfo.schedule(this)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val ID = 47
        private const val CHANNEL = "changes"

        fun schedule(ctx: Context, on: Boolean) {
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (!on) return js.cancel(ID)
            if (js.getPendingJob(ID) != null) return // already running hourly; rescheduling would reset its timer
            js.schedule(
                JobInfo.Builder(ID, ComponentName(ctx, ChangeJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(60 * 60_000L)
                    .setPersisted(true)
                    .build(),
            )
        }

        fun notifyChanges(ctx: Context, messages: List<String>) {
            if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33
            ) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Änderungen im Stundenplan", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val title = if (messages.size == 1) "Stundenplan geändert" else "${messages.size} Änderungen im Stundenplan"
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(messages.first())
                .setStyle(Notification.BigTextStyle().bigText(messages.joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), n)
        }
    }
}

/** K8: today's lessons from the cache (tomorrow's after school), with cancellations and changes marked. */
class DayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) = render(ctx, manager, ids)

    companion object {
        private val dayName = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
        private val clock = DateTimeFormatter.ofPattern("HH:mm")

        fun update(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, DayWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, m, ids)
        }

        /** pure text for the widget, so it can be checked without a launcher */
        fun text(repo: Repo, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Pair<String, String> {
            val own = repo.own.value ?: return "Stundenblick" to "Kein eigener Plan gesetzt. Öffne die App."
            val master = repo.master.value ?: return "Stundenblick" to "Öffne Stundenblick einmal, dann steht hier dein Tag."
            val hidden = repo.hidden(own)
            // after the last lesson the widget turns to the next school day within a week
            for (offset in 0..7) {
                val day = now.toLocalDate().plusDays(offset.toLong())
                val ps = Filter.apply(repo.cachedPeriods(own, Parse.monday(day)) ?: continue, hidden).filter { it.start.toLocalDate() == day }
                if (ps.isEmpty() || (offset == 0 && ps.all { !it.end.isAfter(now) })) continue
                val lines = ps.map { p ->
                    val n = master.unitNumber(p.start.toLocalTime())?.let { "$it." } ?: clock.format(p.start)
                    val subject = p.of(Kind.SUBJECT).firstOrNull()?.let { master.find(Kind.SUBJECT, it.id)?.name } ?: p.lessonText.ifBlank { "Stunde" }
                    val room = p.of(Kind.ROOM).joinToString(", ") { master.find(Kind.ROOM, it.id)?.name ?: "" }.trim(',', ' ')
                    val mark = when {
                        p.cancelled -> "  ✕ entfällt"
                        p.isExam -> "  ● Klausur"
                        p.irregular -> "  ↻ geändert"
                        else -> ""
                    }
                    "$n  $subject  $room$mark"
                }
                val title = when (offset) {
                    0 -> "Heute"
                    1 -> "Morgen"
                    else -> dayName.format(day).substringBefore(',')
                } + " · " + dayName.format(day).substringAfter(", ")
                return title to lines.joinToString("\n")
            }
            return "Stundenblick" to "In den nächsten Tagen nichts im Speicher. Öffne die App, um zu laden."
        }

        private fun render(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val (title, body) = text(Repo.get(ctx))
            val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(ctx.packageName, R.layout.widget).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_body, body)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }

    }
}

/**
 * J3, like BetterUntis' break notifications: at the end of each lesson that is followed by another one today,
 * a silent, private notification names the next lesson that takes place. Only the cache is read, nothing is fetched.
 * One alarm at a time; each firing plans the next break.
 */
class BreakInfo : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val repo = Repo.get(ctx)
        if (repo.breakInfo) post(ctx, repo, java.time.LocalDateTime.now())
        schedule(ctx)
    }

    companion object {
        private const val CHANNEL = "pause"
        private const val NOTE = 4711

        private fun ownDay(repo: Repo, day: java.time.LocalDate): List<Period> {
            val own = repo.own.value ?: return emptyList()
            val ps = repo.cachedPeriods(own, Parse.monday(day)) ?: return emptyList()
            return Derive.blocks(Filter.apply(ps, repo.hidden(own)).filter { it.start.toLocalDate() == day })
        }

        fun schedule(ctx: Context) {
            val repo = Repo.get(ctx)
            val am = ctx.getSystemService(android.app.AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, BreakInfo::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            am.cancel(pi)
            if (!repo.breakInfo) return
            val now = java.time.LocalDateTime.now()
            val day = ownDay(repo, now.toLocalDate()).filter { !it.cancelled }
            val end = day.map { it.end }.filter { it.isAfter(now) }.sorted().firstOrNull { e -> day.any { !it.start.isBefore(e) } } ?: return
            // inexact on purpose: a minute late is fine, and Android 14 restricts exact alarms
            am.setWindow(android.app.AlarmManager.RTC, end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), 60_000, pi)
        }

        private fun post(ctx: Context, repo: Repo, at: java.time.LocalDateTime) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val master = repo.master.value ?: return
            val next = Derive.now(ownDay(repo, at.toLocalDate()), at).next
            val p = next.firstOrNull() ?: return
            val subject = p.of(Kind.SUBJECT).firstOrNull()?.let { master.find(Kind.SUBJECT, it.id)?.longName?.ifBlank { null } ?: master.find(Kind.SUBJECT, it.id)?.name } ?: p.lessonText.ifBlank { "Stunde" }
            val room = p.of(Kind.ROOM).joinToString(", ") { master.find(Kind.ROOM, it.id)?.name ?: "?" }
            val teacher = p.of(Kind.TEACHER).joinToString(", ") { master.find(Kind.TEACHER, it.id)?.name ?: "?" }
            val label = Derive.unitLabel(p, master) ?: clock.format(p.start)
            val change = when {
                p.of(Kind.ROOM).any { it.changed } -> "Raumänderung"
                p.of(Kind.TEACHER).any { it.changed } -> "Vertretung"
                p.isExam -> p.exam ?: "Klausur"
                else -> null
            }
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pausen-Info", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val public = Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle("Nächste Stunde").build()
            val until = java.time.Duration.between(at, p.start).toMillis().coerceAtLeast(60_000)
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Gleich, $label: $subject")
                .setContentText(listOfNotNull(room.ifBlank { null }?.let { "Raum $it" }, teacher.ifBlank { null }, change).joinToString(", "))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setContentIntent(open)
                .setTimeoutAfter(until)
                .setOnlyAlertOnce(true)
                .build()
            nm.notify(NOTE, n)
        }

        private val clock = DateTimeFormatter.ofPattern("HH:mm")
    }
}
