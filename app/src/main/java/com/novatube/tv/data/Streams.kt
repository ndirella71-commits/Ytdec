package com.novatube.tv.data

import org.json.JSONArray
import org.json.JSONObject

data class StreamChoice(
    val title: String,
    val author: String,
    val description: String,
    val muxedUrl: String? = null,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val hlsUrl: String? = null,
    val error: String? = null,
) {
    val playable: Boolean get() = muxedUrl != null || hlsUrl != null || (videoUrl != null && audioUrl != null)
}

/** A single candidate format before it has been resolved into a directly playable URL. */
private data class Candidate(val url: String?, val signatureCipher: String?, val score: Int)

/**
 * Resolves a video id into playable stream URLs.
 *
 * Most formats YouTube returns need one or both of: a `signatureCipher` deciphered into a real
 * `url`, and an `n` query-parameter value fixed up (otherwise the download is throttled or
 * refused). [jsPlayer], when supplied, handles both by running YouTube's own (unmodified) player
 * script in a hidden WebView. Without it, only formats YouTube already handed back as plain,
 * unprotected URLs are used — which still works for some videos/clients, just fewer of them.
 */
object Streams {

    private val order = listOf(Clients.TV, Clients.ANDROID, Clients.IOS, Clients.WEB)

    suspend fun resolve(innerTube: InnerTube, videoId: String, jsPlayer: JsPlayer? = null): StreamChoice {
        var lastError: String? = null
        for (spec in order) {
            val response = try {
                innerTube.player(videoId, spec)
            } catch (t: Throwable) {
                lastError = t.message
                continue
            }
            val status = response.optJSONObject("playabilityStatus")
            val state = status?.optString("status").orEmpty()
            if (state.isNotEmpty() && state != "OK") {
                lastError = status?.optString("reason")
                    ?.ifEmpty { null }
                    ?: "This video cannot be played ($state)."
                continue
            }

            val details = response.optJSONObject("videoDetails")
            val streaming = response.optJSONObject("streamingData")
            if (streaming == null) continue

            val choice = try {
                build(details, streaming, jsPlayer, videoId)
            } catch (t: Throwable) {
                lastError = t.message
                null
            }
            if (choice != null && choice.playable) return choice
        }
        return StreamChoice(
            title = "",
            author = "",
            description = "",
            error = lastError ?: "No playable stream was returned for this video.",
        )
    }

    private suspend fun build(
        details: JSONObject?,
        streaming: JSONObject,
        jsPlayer: JsPlayer?,
        videoId: String,
    ): StreamChoice {
        val formats = streaming.optJSONArray("formats")
        val adaptive = streaming.optJSONArray("adaptiveFormats")

        val muxed = pickBest(formats, wantVideo = true, wantAudio = true, maxHeight = 1080)
        val video = pickBest(adaptive, wantVideo = true, wantAudio = false, maxHeight = 1080)
        val audio = pickBest(adaptive, wantVideo = false, wantAudio = true, maxHeight = 0)
        val hls = streaming.optString("hlsManifestUrl").takeIf { it.isNotEmpty() }

        val muxedUrl = muxed?.let { resolveCandidate(it, jsPlayer, videoId) }
        var videoUrl: String? = null
        var audioUrl: String? = null
        if (muxedUrl == null) {
            videoUrl = video?.let { resolveCandidate(it, jsPlayer, videoId) }
            audioUrl = audio?.let { resolveCandidate(it, jsPlayer, videoId) }
        }

        return StreamChoice(
            title = details?.optString("title").orEmpty(),
            author = details?.optString("author").orEmpty(),
            description = details?.optString("shortDescription").orEmpty(),
            muxedUrl = muxedUrl,
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            hlsUrl = if (muxedUrl == null && (videoUrl == null || audioUrl == null)) hls else null,
        )
    }

    private suspend fun resolveCandidate(candidate: Candidate, jsPlayer: JsPlayer?, videoId: String): String? {
        if (candidate.url != null && jsPlayer == null) return candidate.url
        if (jsPlayer == null) return candidate.url
        return try {
            jsPlayer.resolveUrl(videoId, candidate.url, candidate.signatureCipher)
        } catch (t: Throwable) {
            candidate.url
        }
    }

    private fun pickBest(formats: JSONArray?, wantVideo: Boolean, wantAudio: Boolean, maxHeight: Int): Candidate? {
        if (formats == null) return null
        var best: Candidate? = null
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val mime = f.optString("mimeType")
            val isVideo = mime.startsWith("video/")
            val isAudio = mime.startsWith("audio/")
            if (wantVideo && wantAudio) {
                // Muxed: must carry both tracks in one file; InnerTube signals that with no
                // explicit "audio only"/"video only" split, i.e. plain "video/..." progressive formats.
                if (!isVideo) continue
            } else if (wantVideo) {
                if (!isVideo) continue
            } else if (wantAudio) {
                if (!isAudio) continue
            }

            val url = f.optString("url").takeIf { it.isNotEmpty() }
            val cipher = f.optString("signatureCipher").takeIf { it.isNotEmpty() }
                ?: f.optString("cipher").takeIf { it.isNotEmpty() }
            if (url == null && cipher == null) continue

            val height = f.optInt("height", 0)
            if (maxHeight > 0 && height > maxHeight) continue

            val codecBonus = if (mime.contains("avc1") || mime.contains("mp4a")) 1_000_000 else 0
            val plainBonus = if (url != null) 500_000 else 0 // prefer formats we don't need to decipher
            val score = height * 10_000 + codecBonus + plainBonus + f.optInt("bitrate", 0) / 1000
            if (best == null || score > best.score) {
                best = Candidate(url, cipher, score)
            }
        }
        return best
    }
}
