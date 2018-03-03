package com.geckour.nowplaying4gpm.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.widget.RemoteViews
import com.geckour.nowplaying4gpm.R
import com.geckour.nowplaying4gpm.activity.SettingsActivity
import com.geckour.nowplaying4gpm.activity.SharingActivity
import com.geckour.nowplaying4gpm.util.*
import kotlinx.coroutines.experimental.Job

class ShareWidgetProvider: AppWidgetProvider() {

    enum class Action {
        SHARE,
        OPEN_SETTING
    }

    companion object {
        fun getPendingIntent(context: Context, action: Action): PendingIntent =
                PendingIntent.getBroadcast(context, 0, Intent(context, ShareWidgetProvider::class.java).apply { setAction(action.name) }, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val jobs: ArrayList<Job> = ArrayList()

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)

        if (context == null) return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val summary = sharedPreferences.getSharingText(context)
        updateWidget(context, summary)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)

        if (context == null || intent == null) return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val summary = sharedPreferences.getSharingText(context)
        when (intent.action) {
            Action.SHARE.name -> {
                summary?.let {
                    ui(jobs) {
                        val artworkUri = async {
                            getArtworkUri(context,
                                    sharedPreferences.getCurrentTitle(),
                                    sharedPreferences.getCurrentArtist(),
                                    sharedPreferences.getCurrentAlbum())
                        }.await()
                        context.startActivity(SharingActivity.getIntent(context, it, artworkUri))
                    }
                }
            }

            Action.OPEN_SETTING.name -> context.startActivity(SettingsActivity.getIntent(context))
        }
    }

    private fun updateWidget(context: Context, summary: String?) =
            AppWidgetManager.getInstance(context).apply {
                val ids = getAppWidgetIds(ComponentName(context, ShareWidgetProvider::class.java)).firstOrNull() ?: return@apply

                updateAppWidget(
                        ids,
                        RemoteViews(context.packageName, R.layout.widget_share).apply {
                            setTextViewText(R.id.widget_summary_share, summary ?: context.getString(R.string.dialog_message_alert_no_metadata))

                            setOnClickPendingIntent(R.id.widget_share_root, ShareWidgetProvider.getPendingIntent(context, ShareWidgetProvider.Action.SHARE))
                            setOnClickPendingIntent(R.id.widget_button_setting, ShareWidgetProvider.getPendingIntent(context, ShareWidgetProvider.Action.OPEN_SETTING))
                        }
                )
            }
}