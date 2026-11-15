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
