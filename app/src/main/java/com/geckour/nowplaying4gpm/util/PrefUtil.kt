package com.geckour.nowplaying4gpm.util

import android.content.Context
import android.content.SharedPreferences
import com.geckour.nowplaying4gpm.R
import com.geckour.nowplaying4gpm.activity.SettingsActivity

fun SharedPreferences.init(context: Context) {
    edit().apply {
        if (contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_USE_API.name).not())
            putBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_USE_API.name, false)
        if (contains(SettingsActivity.PrefKey.PREF_KEY_PATTERN_FORMAT_SHARE_TEXT.name).not())
            putString(SettingsActivity.PrefKey.PREF_KEY_PATTERN_FORMAT_SHARE_TEXT.name, context.getString(R.string.default_sharing_text_pattern))
        if (contains(SettingsActivity.PrefKey.PREF_KEY_CHOSEN_COLOR_INDEX.name).not())
            putInt(SettingsActivity.PrefKey.PREF_KEY_CHOSEN_COLOR_INDEX.name, SettingsActivity.paletteArray.indexOf(R.string.palette_light_vibrant))
        if (contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_RESIDE.name).not())
            putBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_RESIDE.name, true)
        if (contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_BUNDLE_ARTWORK.name).not())
            putBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_BUNDLE_ARTWORK.name, true)
        if (contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_COLORIZE_NOTIFICATION_BG.name).not())
            putBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_COLORIZE_NOTIFICATION_BG.name, true)
        if (contains(SettingsActivity.PrefKey.PREF_KEY_BILLING_DONATE.name).not())
            putBoolean(SettingsActivity.PrefKey.PREF_KEY_BILLING_DONATE.name, false)
    }.apply()
}

fun SharedPreferences.refreshCurrentMetadata(title: String?, artist: String?, album: String?) =
        edit().apply {
            if (title != null) putString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_TITLE.name, title)
            else remove(SettingsActivity.PrefKey.PREF_KEY_CURRENT_TITLE.name)

            if (artist != null) putString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ARTIST.name, artist)
            else remove(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ARTIST.name)

            if (album != null) putString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ALBUM.name, album)
            else remove(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ALBUM.name)
        }.apply()

fun SharedPreferences.getFormatPattern(context: Context): String =
        getString(SettingsActivity.PrefKey.PREF_KEY_PATTERN_FORMAT_SHARE_TEXT.name, context.getString(R.string.default_sharing_text_pattern))

fun SharedPreferences.getSharingText(context: Context): String? {
    val title = getCurrentTitle()
    val artist = getCurrentArtist()
    val album = getCurrentAlbum()

    if (title == null || artist == null || album == null) return null
    return getFormatPattern(context).getSharingText(title, artist, album)
}

fun SharedPreferences.getCurrentTitle(): String? =
        if (contains(SettingsActivity.PrefKey.PREF_KEY_CURRENT_TITLE.name)) getString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_TITLE.name, null)
        else null

fun SharedPreferences.getCurrentArtist(): String? =
        if (contains(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ARTIST.name)) getString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ARTIST.name, null)
        else null

fun SharedPreferences.getCurrentAlbum(): String? =
        if (contains(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ALBUM.name)) getString(SettingsActivity.PrefKey.PREF_KEY_CURRENT_ALBUM.name, null)
        else null

fun SharedPreferences.getChoseColorIndex(): Int =
        getInt(SettingsActivity.PrefKey.PREF_KEY_CHOSEN_COLOR_INDEX.name, SettingsActivity.paletteArray.indexOf(R.string.palette_light_vibrant))

fun SharedPreferences.getWhetherResideSummaryResId(): Int =
        if (getWhetherReside()) R.string.pref_item_summary_switch_on
        else R.string.pref_item_summary_switch_off

fun SharedPreferences.getWhetherUseApiSummaryResId(): Int =
        if (getWhetherUseApi()) R.string.pref_item_summary_switch_on
        else R.string.pref_item_summary_switch_off

fun SharedPreferences.getWhetherBundleArtworkSummaryResId(): Int =
        if (getWhetherBundleArtwork()) R.string.pref_item_summary_switch_on
        else R.string.pref_item_summary_switch_off

fun SharedPreferences.getWhetherColorizeNotificationBgSummaryResId(): Int =
        if (getWhetherColorizeNotificationBg()) R.string.pref_item_summary_switch_on
        else R.string.pref_item_summary_switch_off

fun SharedPreferences.getWhetherReside(): Boolean =
        contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_RESIDE.name).not()
                || getBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_RESIDE.name, true)

fun SharedPreferences.getWhetherUseApi(): Boolean =
        contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_USE_API.name)
                && getBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_USE_API.name, false)

fun SharedPreferences.getWhetherBundleArtwork(): Boolean =
        contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_BUNDLE_ARTWORK.name).not()
                || getBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_BUNDLE_ARTWORK.name, true)

fun SharedPreferences.getWhetherColorizeNotificationBg(): Boolean =
        contains(SettingsActivity.PrefKey.PREF_KEY_WHETHER_COLORIZE_NOTIFICATION_BG.name).not()
                || getBoolean(SettingsActivity.PrefKey.PREF_KEY_WHETHER_COLORIZE_NOTIFICATION_BG.name, true)

fun SharedPreferences.getDonateBillingState(): Boolean =
        contains(SettingsActivity.PrefKey.PREF_KEY_BILLING_DONATE.name)
                && getBoolean(SettingsActivity.PrefKey.PREF_KEY_BILLING_DONATE.name, false)