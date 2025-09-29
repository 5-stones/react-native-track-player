package com.doublesymmetry.trackplayer.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Rating
import com.doublesymmetry.kotlinaudio.models.AudioItem
import com.doublesymmetry.kotlinaudio.models.AudioItemOptions
import com.doublesymmetry.kotlinaudio.models.MediaType
import com.doublesymmetry.trackplayer.utils.BundleUtils
import com.doublesymmetry.trackplayer.extensions.NumberExt.Companion.toMilliseconds
import com.facebook.react.bridge.ReadableType

@OptIn(UnstableApi::class)
class Track private constructor(
    // Network/File info
    val url: String?,
    val uri: Uri?,
    val resourceId: Int?,
    val type: MediaType,
    val contentType: String?,
    val userAgent: String?,
    val headers: Map<String, String>?,

    // Metadata
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: String?,
    val date: String?,
    val genre: String?,
    val duration: Double?,
    val rating: Rating?,
    val mediaId: String?
) {
    fun toAudioItem(): AudioItem {
        return AudioItem(
            audioUrl = uri?.toString() ?: "",
            type = type,
            artist = artist,
            title = title,
            albumTitle = album,
            artwork = artwork,
            duration = duration?.toMilliseconds(),
            options = AudioItemOptions(headers?.let { HashMap(it) }, userAgent, resourceId),
            mediaId = mediaId,
            track = this
        )
    }

    fun toBridge(): WritableMap {
        val map = Arguments.createMap()

        url?.let { map.putString("url", it) }
        type.name.lowercase().let { map.putString("type", it) }
        contentType?.let { map.putString("contentType", it) }
        userAgent?.let { map.putString("userAgent", it) }

        headers?.let { headerMap ->
            val headerWritableMap = Arguments.createMap()
            headerMap.forEach { (key, value) ->
                headerWritableMap.putString(key, value)
            }
            map.putMap("headers", headerWritableMap)
        }

        title?.let { map.putString("title", it) }
        artist?.let { map.putString("artist", it) }
        album?.let { map.putString("album", it) }
        artwork?.let { map.putString("artwork", it) }
        date?.let { map.putString("date", it) }
        genre?.let { map.putString("genre", it) }
        duration?.let { map.putDouble("duration", it) }
        mediaId?.let { map.putString("mediaId", it) }

        rating?.let { ratingValue ->
            BundleUtils.setRating(map, "rating", ratingValue)
        }

        return map
    }

    fun updateMetadata(
        title: String? = this.title,
        artist: String? = this.artist,
        album: String? = this.album,
        artwork: String? = this.artwork,
        date: String? = this.date,
        genre: String? = this.genre,
        duration: Double? = this.duration,
        rating: Rating? = this.rating,
        mediaId: String? = this.mediaId
    ): Track {
        return Track(
            url = url,
            uri = uri,
            resourceId = resourceId,
            type = type,
            contentType = contentType,
            userAgent = userAgent,
            headers = headers,
            title = title,
            artist = artist,
            album = album,
            artwork = artwork,
            date = date,
            genre = genre,
            duration = duration,
            rating = rating,
            mediaId = mediaId
        )
    }

    companion object {
        fun fromBridge(context: Context, map: ReadableMap, ratingType: Int): Track {
            val resourceId = BundleUtils.getRawResourceId(context, map, "url")
            val uri = if (resourceId == 0) {
                BundleUtils.getUri(context, map, "url")
            } else {
                Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .path(resourceId.toString()).build()
            }

            // Parse headers
            val headers = map.getMap("headers")?.let { headerMap ->
                val headerHashMap = HashMap<String, String>()
                val iterator = headerMap.keySetIterator()
                while (iterator.hasNextKey()) {
                    val key = iterator.nextKey()
                    headerHashMap[key] = headerMap.getString(key) ?: ""
                }
                headerHashMap
            }

            // Parse type
            val trackType = map.getString("type") ?: "default"
            var mediaType = MediaType.DEFAULT
            for (t in MediaType.entries) {
                if (t.name.equals(trackType, ignoreCase = true)) {
                    mediaType = t
                    break
                }
            }
            return Track(
                url = if (map.getType("url") == ReadableType.String) map.getString("url") else null,
                uri = uri,
                resourceId = if (resourceId == 0) null else resourceId,
                type = mediaType,
                contentType = map.getString("contentType"),
                userAgent = map.getString("userAgent"),
                headers = headers,
                title = map.getString("title"),
                artist = map.getString("artist"),
                album = map.getString("album"),
                artwork = BundleUtils.getUri(context, map, "artwork")?.toString(),
                date = map.getString("date"),
                genre = map.getString("genre"),
                duration = if (map.hasKey("duration")) map.getDouble("duration") else null,
                rating = BundleUtils.getRating(map, "rating", ratingType),
                mediaId = map.getString("mediaId")
            )
        }
    }
}

/**
 * Factory for creating Track objects with injected context and rating type.
 * Eliminates the need to pass context and ratingType parameters on every Track creation.
 */
class TrackFactory(private val context: Context, private val getRatingType: () -> Int) {
    fun fromBridge(map: ReadableMap): Track {
        return Track.fromBridge(context, map, getRatingType())
    }

    fun tracksFromBridge(tracks: ReadableArray): List<Track> {
      return (0 until tracks.size()).mapNotNull { i ->
        tracks.takeIf { it.getType(i) == ReadableType.Map }
          ?.getMap(i)
          ?.let { fromBridge(it) }
      }
    }
}
