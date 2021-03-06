package com.geckour.nowplaying4gpm.util

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import com.geckour.nowplaying4gpm.BuildConfig
import com.geckour.nowplaying4gpm.R
import com.geckour.nowplaying4gpm.domain.model.MediaIdInfo
import com.geckour.nowplaying4gpm.domain.model.TrackInfo
import com.geckour.nowplaying4gpm.ui.settings.SettingsActivity
import io.fabric.sdk.android.Fabric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parse
import kotlinx.serialization.parseList
import kotlinx.serialization.stringify
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

enum class PaletteColor {
    LIGHT_VIBRANT {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                LIGHT_VIBRANT, VIBRANT, DARK_VIBRANT, LIGHT_MUTED, MUTED, DARK_MUTED
            )
    },
    VIBRANT {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                VIBRANT, LIGHT_VIBRANT, DARK_VIBRANT, MUTED, LIGHT_MUTED, DARK_MUTED
            )
    },
    DARK_VIBRANT {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                DARK_VIBRANT, VIBRANT, LIGHT_VIBRANT, DARK_MUTED, MUTED, LIGHT_MUTED
            )
    },
    LIGHT_MUTED {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                LIGHT_MUTED, MUTED, DARK_MUTED, LIGHT_VIBRANT, VIBRANT, DARK_VIBRANT
            )
    },
    MUTED {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                MUTED, LIGHT_MUTED, DARK_MUTED, VIBRANT, LIGHT_VIBRANT, DARK_VIBRANT
            )
    },
    DARK_MUTED {
        override val hierarchyList: List<PaletteColor>
            get() = listOf(
                DARK_MUTED, MUTED, LIGHT_MUTED, DARK_VIBRANT, VIBRANT, LIGHT_VIBRANT
            )
    };

    abstract val hierarchyList: List<PaletteColor>

    fun getSummaryResId(): Int = when (this) {
        LIGHT_VIBRANT -> R.string.palette_light_vibrant
        VIBRANT -> R.string.palette_vibrant
        DARK_VIBRANT -> R.string.palette_dark_vibrant
        LIGHT_MUTED -> R.string.palette_light_muted
        MUTED -> R.string.palette_muted
        DARK_MUTED -> R.string.palette_dark_muted
    }

    companion object {
        fun getFromIndex(index: Int): PaletteColor = values().getOrNull(index) ?: LIGHT_VIBRANT
    }
}

enum class Visibility {
    PUBLIC, UNLISTED, PRIVATE;

    fun getSummaryResId(): Int = when (this) {
        PUBLIC -> R.string.mastodon_visibility_public
        UNLISTED -> R.string.mastodon_visibility_unlisted
        PRIVATE -> R.string.mastodon_visibility_private
    }

    companion object {
        fun getFromIndex(index: Int): Visibility = values().getOrNull(index) ?: PUBLIC
    }
}

enum class FormatPattern(val value: String) {
    S_QUOTE("'"),
    S_QUOTE_DOUBLE("''"),
    TITLE("TI"),
    ARTIST("AR"),
    ALBUM("AL"),
    COMPOSER("CO"),
    PLAYER_NAME("PN"),
    SPOTIFY_URL("SU"),
    NEW_LINE("\\n");

    companion object {
        val replaceablePatterns: List<FormatPattern> = values().filter {
            it !in listOf(S_QUOTE, S_QUOTE_DOUBLE, NEW_LINE)
        }
    }
}

inline fun <reified T> withCatching(
    onError: (Throwable) -> Unit = {}, block: () -> T
) = try {
    block()
} catch (t: Throwable) {
    Timber.e(t)
    Crashlytics.logException(t)
    onError(t)
    null
}

fun String.getSharingText(trackInfo: TrackInfo?, modifiers: List<FormatPatternModifier>): String? =
    trackInfo?.let { info ->
        this.splitConsideringEscape().joinToString("") {
            return@joinToString Regex("^'(.+)'$").let { regex ->
                if (it.matches(regex)) it.replace(regex, "$1")
                else when (it) {
                    FormatPattern.S_QUOTE.value -> ""
                    FormatPattern.S_QUOTE_DOUBLE.value -> "'"
                    FormatPattern.TITLE.value -> info.coreElement.title?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.ARTIST.value -> info.coreElement.artist?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.ALBUM.value -> info.coreElement.album?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.COMPOSER.value -> info.coreElement.composer?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.PLAYER_NAME.value -> info.playerAppName?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.SPOTIFY_URL.value -> info.spotifyUrl?.getReplacerWithModifier(
                        modifiers, it
                    ) ?: ""
                    FormatPattern.NEW_LINE.value -> "\n"
                    else -> it
                }
            }
        }
    }

fun String.getReplacerWithModifier(
    modifiers: List<FormatPatternModifier>, identifier: String
): String = "${modifiers.getPrefix(identifier)}$this${modifiers.getSuffix(identifier)}"

fun List<FormatPatternModifier>.getPrefix(value: String): String =
    this.firstOrNull { m -> m.key.value == value }?.prefix ?: ""

fun List<FormatPatternModifier>.getSuffix(value: String): String =
    this.firstOrNull { m -> m.key.value == value }?.suffix ?: ""

fun String.containsPattern(pattern: FormatPattern): Boolean =
    this.splitConsideringEscape().contains(pattern.value)

val String.containedPatterns: List<FormatPattern>
    get() = this.splitConsideringEscape().mapNotNull { delimiter ->
        FormatPattern.values().firstOrNull { it.value == delimiter }
    }

private fun String.splitConsideringEscape(): List<String> = this.splitIncludeDelimiter(
    FormatPattern.S_QUOTE_DOUBLE.value,
    FormatPattern.S_QUOTE.value,
    FormatPattern.TITLE.value,
    FormatPattern.ARTIST.value,
    FormatPattern.ALBUM.value,
    FormatPattern.COMPOSER.value,
    FormatPattern.PLAYER_NAME.value,
    FormatPattern.SPOTIFY_URL.value,
    "\\\\n"
).let { splitList ->
    val escapes = splitList.mapIndexed { i, s -> Pair(i, s) }.filter { it.second == "'" }
        .apply { if (lastIndex < 0) return@let splitList }

    return@let ArrayList<String>().apply {
        for (i in 0 until escapes.lastIndex step 2) {
            this.addAll(
                splitList.subList(
                    if (i == 0) 0 else escapes[i - 1].first + 1, escapes[i].first
                )
            )

            this.add(
                splitList.subList(
                    escapes[i].first, escapes[i + 1].first + 1
                ).joinToString("")
            )
        }

        this.addAll(
            splitList.subList(
                if (escapes[escapes.lastIndex].first + 1 < splitList.lastIndex) escapes[escapes.lastIndex].first + 1
                else splitList.lastIndex, splitList.size
            )
        )
    }
}

fun String.splitIncludeDelimiter(vararg delimiters: String) =
    delimiters.joinToString("|").let { pattern ->
        this.split(Regex("(?<=$pattern)|(?=$pattern)"))
    }

fun AlertDialog.Builder.generate(
    view: View,
    title: String,
    message: String? = null,
    callback: (dialog: DialogInterface, which: Int) -> Unit = { _, _ -> }
): AlertDialog {
    setTitle(title)
    if (message != null) setMessage(message)
    setView(view)
    setPositiveButton(R.string.dialog_button_ok) { dialog, which -> callback(dialog, which) }
    setNegativeButton(R.string.dialog_button_ng) { dialog, _ -> dialog.dismiss() }

    return create()
}

fun Context.checkStoragePermission(
    onNotGranted: ((context: Context) -> Unit)? = null, onGranted: (context: Context) -> Unit = {}
) {
    if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        onGranted(this)
    } else {
        onNotGranted?.invoke(this) ?: startActivity(SettingsActivity.getIntent(this).apply {
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}

fun String.getUri(): Uri? = withCatching { Uri.parse(this) }

private fun Palette.getColorFromPaletteColor(paletteColor: PaletteColor): Int =
    when (paletteColor) {
        PaletteColor.LIGHT_VIBRANT -> getLightVibrantColor(Color.TRANSPARENT)
        PaletteColor.VIBRANT -> getVibrantColor(Color.TRANSPARENT)
        PaletteColor.DARK_VIBRANT -> getDarkVibrantColor(Color.TRANSPARENT)
        PaletteColor.LIGHT_MUTED -> getLightMutedColor(Color.TRANSPARENT)
        PaletteColor.MUTED -> getMutedColor(Color.TRANSPARENT)
        PaletteColor.DARK_MUTED -> getDarkMutedColor(Color.TRANSPARENT)
    }

@ColorInt
fun Palette.getOptimizedColor(context: Context): Int {
    val paletteColorHierarchies =
        PreferenceManager.getDefaultSharedPreferences(context).getChosePaletteColor().hierarchyList

    return paletteColorHierarchies.firstOrNull {
        getColorFromPaletteColor(it) != Color.TRANSPARENT
    }?.let { getColorFromPaletteColor(it) } ?: Color.WHITE
}

fun Context.setCrashlytics() {
    if (BuildConfig.DEBUG.not()) Fabric.with(this, Crashlytics())
}

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Any> Json.parseOrNull(
    json: String?,
    deserializationStrategy: DeserializationStrategy<T>? = null,
    onError: Throwable.() -> Unit = {}
): T? = withCatching(onError) {
    deserializationStrategy?.let { this.parse(it, json!!) } ?: this.parse(json!!)
}

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Any> Json.parseListOrNull(
    json: String?, onError: Throwable.() -> Unit = {}
): List<T>? = withCatching(onError) { this.parseList<T>(json!!) }

fun String.foldBreak(): String = this.replace(Regex("[\r\n]"), " ")

fun String.getAppName(context: Context): String? = withCatching {
    context.packageManager.let {
        it.getApplicationLabel(it.getApplicationInfo(this, 0)).toString()
    }
}

fun <T> MutableList<T>.swap(from: Int, to: Int) {
    val tmp = this[to]
    this[to] = this[from]
    this[from] = tmp
}

fun ViewModel.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch(context, start, block)
}


fun Context.getAlbumIdFromDevice(
    trackCoreElement: TrackInfo.TrackCoreElement
): MediaIdInfo? {
    if (trackCoreElement.isAllNonNull.not()) return null

    return contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID),
        contentQuerySelection,
        trackCoreElement.contentQueryArgs,
        null
    )?.use { it.getAlbumIdFromDevice() }
}

fun Cursor?.getAlbumIdFromDevice(): MediaIdInfo? = this?.let {
    (if (it.moveToFirst()) {
        MediaIdInfo(
            it.getLong(it.getColumnIndex(MediaStore.Audio.Media._ID)),
            it.getLong(it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
        )
    } else null)
}

fun Context.getArtworkUriFromDevice(mediaIdInfo: MediaIdInfo?): Uri? = mediaIdInfo?.let {
    withCatching {
        val contentUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.mediaTrackId
        )
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(this@getArtworkUriFromDevice, contentUri)
        }
        retriever.embeddedPicture?.toBitmap()?.refreshArtworkUri(this)
            ?: ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), it.mediaAlbumId
            ).also { uri ->
                contentResolver.openInputStream(uri)?.close() ?: throw IllegalStateException()
                PreferenceManager.getDefaultSharedPreferences(this).refreshTempArtwork(uri)
            }
    }
}

fun Context.getArtworkUriFromDevice(trackCoreElement: TrackInfo.TrackCoreElement): Uri? =
    getArtworkUriFromDevice(getAlbumIdFromDevice(trackCoreElement))

fun ByteArray.toBitmap(): Bitmap? =
    withCatching { BitmapFactory.decodeByteArray(this, 0, this.size) }

fun Bitmap.refreshArtworkUri(context: Context): Uri? {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val dirName = "images"
    val fileName = "temp_artwork.png"
    val dir = File(context.filesDir, dirName)
    val file = File(dir, fileName)

    if (file.exists()) file.delete()
    if (dir.exists().not()) dir.mkdir()

    FileOutputStream(file).use {
        compress(Bitmap.CompressFormat.PNG, 100, it)
        it.flush()
    }

    return FileProvider.getUriForFile(context, BuildConfig.FILES_AUTHORITY, file).apply {
        sharedPreferences.refreshTempArtwork(this)
    }
}

fun Bitmap.toByteArray(): ByteArray? = ByteArrayOutputStream().apply {
    compress(Bitmap.CompressFormat.PNG, 100, this)
}.toByteArray()

@OptIn(ImplicitReflectionSerializer::class)
fun Serializable.asString(): String =
    ByteArrayOutputStream().use { byteArrayStream ->
        ObjectOutputStream(byteArrayStream).writeObject(this)
        json.stringify(byteArrayStream.toByteArray())
    }

@OptIn(ImplicitReflectionSerializer::class)
inline fun <reified T : Serializable> String.toSerializableObject(): T? =
    withCatching {
        json.parseOrNull<ByteArray>(this).let { byteArray ->
            ByteArrayInputStream(byteArray).use { ObjectInputStream(it).readObject() as T }
        }
    }