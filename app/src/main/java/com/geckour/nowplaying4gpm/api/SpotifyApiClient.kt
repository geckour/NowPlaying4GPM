package com.geckour.nowplaying4gpm.api

import android.content.Context
import androidx.preference.PreferenceManager
import com.crashlytics.android.Crashlytics
import com.geckour.nowplaying4gpm.BuildConfig
import com.geckour.nowplaying4gpm.api.model.SpotifyUser
import com.geckour.nowplaying4gpm.domain.model.SpotifyUserInfo
import com.geckour.nowplaying4gpm.domain.model.TrackInfo
import com.geckour.nowplaying4gpm.util.getSpotifyUserInfo
import com.geckour.nowplaying4gpm.util.moshi
import com.geckour.nowplaying4gpm.util.storeSpotifyUserInfoImmediately
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber

class SpotifyApiClient(private val context: Context) {

    companion object {

        const val SPOTIFY_CALLBACK = "np4gpm://spotify.callback"
        private const val SPOTIFY_ENCODED_CALLBACK = "np4gpm%3A%2F%2Fspotify.callback"
        const val OAUTH_URL =
            "https://accounts.spotify.com/authorize?client_id=${BuildConfig.SPOTIFY_CLIENT_ID}&response_type=code&redirect_uri=$SPOTIFY_ENCODED_CALLBACK"
    }

    private val authService = Retrofit.Builder()
        .client(OkHttpProvider.spotifyAuthClient)
        .baseUrl("https://accounts.spotify.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SpotifyAuthService::class.java)

    private val service: SpotifyApiService
        get() = Retrofit.Builder()
            .client(OkHttpProvider.getSpotifyApiClient(context))
            .baseUrl("https://api.spotify.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SpotifyApiService::class.java)

    suspend fun storeSpotifyUserInfo(code: String): SpotifyUserInfo? = withContext(Dispatchers.IO) {
        val token = try {
            authService.getToken(code)
        } catch (t: Throwable) {
            Timber.e(t)
            Crashlytics.logException(t)
            null
        }
        return@withContext token?.let {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            sharedPreferences.storeSpotifyUserInfoImmediately(SpotifyUserInfo(it, ""))
            val userName = getUser().displayName
            SpotifyUserInfo(token, userName).apply {
                sharedPreferences.storeSpotifyUserInfoImmediately(this)
            }
        }
    }

    private suspend fun refreshTokenIfNeeded() {
        val token =
            PreferenceManager.getDefaultSharedPreferences(context).getSpotifyUserInfo()?.token
                ?: return
        if (System.currentTimeMillis() > token.expiresIn) {
            storeSpotifyUserInfo(token.codeForRefreshToken)
        }
    }

    private suspend fun getUser(): SpotifyUser = service.getUser()

    suspend fun getSpotifyUrl(trackCoreElement: TrackInfo.TrackCoreElement): String? {
        return trackCoreElement.spotifySearchQuery?.let {
            try {
                refreshTokenIfNeeded()
                service.searchSpotifyItem(it).tracks?.items?.firstOrNull()?.urls?.get("spotify")
            } catch (t: Throwable) {
                Timber.e(t)
                Crashlytics.logException(t)
                null
            }
        }
    }
}