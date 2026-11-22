package com.novatube.tv.data

import org.json.JSONArray
import org.json.JSONObject

/** A single playable video tile shown in the UI. */
data class VideoItem(
    val id: String,
    val title: String,
    val channel: String? = null,
    val meta: String? = null,
    val thumbnail: String? = null,
    val durationText: String? = null,
)

/** A horizontal row ("shelf") of videos, as used by the 10-foot YouTube TV layout. */
data class Shelf(
    val title: String?,
    val items: List<VideoItem>,
)

/** A parsed feed page: shelves plus an optional continuation token for infinite scroll. */
data class FeedPage(
    val shelves: List<Shelf>,
    val continuation: String?,
)

/**
 * InnerTube feeds are deeply nested and Google keeps renaming renderers. Instead of hard-coding
 * one exact shape, this walks the tree and picks up every video renderer it can recognise, keeping
 * the shelf (row) it appeared in. That survives most schema changes.
 */
object FeedParser {

    private val videoKeys = listOf(
        "videoRenderer", "gridVideoRenderer", "compactVideoRenderer", "tvVideoCardRenderer",
        "playlistVideoRenderer", "videoWithContextRenderer", "reelItemRenderer",
    )

    private val shelfKeys = listOf(
        "shelfRenderer", "tvShelfRenderer", "reelShelfRenderer", "horizontalListRenderer",
        "gridRenderer", "expandedShelfContentsRenderer", "tvCarouselRenderer",
    )

    // Renderers that are advertising or promotional slots. They are skipped entirely.
    private val adKeys = listOf(
        "adSlotRenderer", "inFeedAdLayoutRenderer", "displayAdRenderer", "promotedSparklesTextRenderer",
        "compactPromotedVideoRenderer", "brandVideoShelfRenderer", "brandVideoSingletonRenderer",
        "adBreakServiceRenderer",
    )

    fun parse(root: JSONObject): FeedPage {
        val shelves = ArrayList<Shelf>()
        val loose = ArrayList<VideoItem>()
        val continuations = ArrayList<String>()

        fun addContinuation(o: JSONObject) {
            val cmd = o.optJSONObject("continuationCommand") ?: return
            val token = cmd.optString("token")
            if (token.isNotEmpty()) continuations.add(token)
        }

        fun visit(node: Any?, bucket: MutableList<VideoItem>) {
            when (node) {
                is JSONObject -> {
                    if (adKeys.any { node.has(it) }) return
                    addContinuation(node)

                    // A nested shelf becomes its own row.
                    val shelfKey = shelfKeys.firstOrNull { node.has(it) }
                    if (shelfKey != null && shelfKey != "horizontalListRenderer" && shelfKey != "gridRenderer") {
                        val shelfNode = node.optJSONObject(shelfKey) ?: return
                        val title = shelfTitle(shelfNode)
                        val items = ArrayList<VideoItem>()
                        var consumedTitle = false
                        for (k in shelfNode.keys()) {
                            if (!consumedTitle && (k == "title" || k == "header")) {
                                consumedTitle = true
                                continue
                            }
                            visit(shelfNode.opt(k), items)
                        }
                        if (items.isNotEmpty()) shelves.add(Shelf(title, items))
                        return
                    }

                    val videoKey = videoKeys.firstOrNull { node.has(it) }
                    if (videoKey != null && videoKey != "reelItemRenderer") {
                        val v = node.optJSONObject(videoKey)
                        val item = v?.let { parseVideo(it) }
                        if (item != null) {
                            bucket.add(item)
                            return
                        }
                    }

                    val lockup = node.optJSONObject("lockupViewModel")
                    if (lockup != null) {
                        val item = parseLockup(lockup)
                        if (item != null) {
                            bucket.add(item)
                            return
                        }
                    }

                    // tileRenderers wrap cards inside TV grids.
                    val tile = node.optJSONObject("tileRenderer")
                    if (tile != null) {
                        visit(tile, bucket)
                        return
                    }

                    for (k in node.keys()) {
                        if (k == "title" || k == "header" || k == "thumbnail") continue
                        visit(node.opt(k), bucket)
                    }
                }

                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i), bucket)
            }
        }

        visit(root, loose)

        val merged = ArrayList<Shelf>()
        if (loose.isNotEmpty()) merged.add(Shelf(null, loose))
        merged.addAll(shelves.filter { it.items.isNotEmpty() })

        // De-duplicate while keeping order (feeds repeat items across rows).
        val seen = HashSet<String>()
        val cleaned = merged.mapNotNull { shelf ->
            val kept = shelf.items.filter { seen.add(it.id) }
            if (kept.isEmpty()) null else shelf.copy(items = kept)
        }

        return FeedPage(cleaned, continuations.firstOrNull())
    }

    private fun shelfTitle(shelf: JSONObject): String? {
        val title = shelf.optJSONObject("title") ?: shelf.optJSONObject("header") ?: return null
        return textOf(title)
    }

    /** Pulls a display string out of any InnerTube "text" node. */
    fun textOf(node: JSONObject?): String? {
        if (node == null) return null
        node.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        node.optString("content").takeIf { it.isNotEmpty() }?.let { return it }
        node.optString("text").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = node.optJSONArray("runs")
        if (runs != null) {
            val sb = StringBuilder()
            for (i in 0 until runs.length()) {
                sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        // Wrapper nodes such as {"text": {"content": "..."}}
        node.optJSONObject("text")?.let { inner ->
            return textOf(inner)
        }
        node.optJSONObject("content")?.let { return textOf(it) }
        return null
    }

    private fun firstThumbUrl(node: JSONObject?): String? {
        if (node == null) return null
        val thumbs = node.optJSONObject("thumbnails")?.optJSONArray("thumbnails")
            ?: node.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            // Prefer the largest available image.
            var best: String? = null
            var bestW = -1
            for (i in 0 until thumbs.length()) {
                val t = thumbs.optJSONObject(i) ?: continue
                val url = t.optString("url")
                val w = t.optInt("width", 0)
                if (url.isNotEmpty() && w >= bestW) {
                    best = url
                    bestW = w
                }
            }
            if (best != null) return best
        }
        val sources = node.optJSONObject("image")?.optJSONArray("sources")
            ?: node.optJSONArray("sources")
        if (sources != null) {
            var best: String? = null
            var bestW = -1
            for (i in 0 until sources.length()) {
                val s = sources.optJSONObject(i) ?: continue
                val url = s.optString("url")
                val w = s.optInt("width", 0)
                if (url.isNotEmpty() && w >= bestW) {
                    best = url
                    bestW = w
                }
            }
            return best
        }
        return null
    }

    private fun durationFromOverlays(node: JSONObject): String? {
        val overlays = node.optJSONArray("thumbnailOverlays") ?: return null
        for (i in 0 until overlays.length()) {
            val o = overlays.optJSONObject(i) ?: continue
            o.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.let { t ->
                textOf(t.optJSONObject("text"))?.let { return it }
            }
        }
        return null
    }

    private fun parseVideo(v: JSONObject): VideoItem? {
        val id = v.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        val title = textOf(v.optJSONObject("title"))
            ?: textOf(v.optJSONObject("headline"))
            ?: return null
        val channel = textOf(v.optJSONObject("longBylineText"))
            ?: textOf(v.optJSONObject("shortBylineText"))
            ?: textOf(v.optJSONObject("ownerText"))
            ?: v.optJSONObject("ownerText")?.let { textOf(it) }
        val views = textOf(v.optJSONObject("viewCountText"))
        val published = textOf(v.optJSONObject("publishedTimeText"))
        val meta = listOfNotNull(views, published).joinToString(" • ").ifEmpty { null }
        val duration = textOf(v.optJSONObject("lengthText")) ?: durationFromOverlays(v)
        return VideoItem(
            id = id,
            title = title,
            channel = channel,
            meta = meta,
            thumbnail = firstThumbUrl(v.optJSONObject("thumbnail")) ?: firstThumbUrl(v.optJSONObject("channelThumbnail"))
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationText = duration,
        )
    }

    private fun parseLockup(l: JSONObject): VideoItem? {
        val type = l.optString("contentType")
        if (type.isNotEmpty() && !type.contains("VIDEO", ignoreCase = true)) return null
        val id = l.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val metaVm = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = metaVm?.optJSONObject("title")?.let { textOf(it) } ?: return null

        var channel: String? = null
        var meta: String? = null
        val rows = metaVm?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        if (rows != null && rows.length() > 0) {
            val parts = rows.optJSONObject(0)?.optJSONArray("metadataParts")
            if (parts != null && parts.length() > 0) {
                channel = parts.optJSONObject(0)?.optJSONObject("text")?.let { textOf(it) }
                meta = (1 until parts.length())
                    .mapNotNull { parts.optJSONObject(it)?.optJSONObject("text")?.let { t -> textOf(t) } }
                    .joinToString(" • ")
                    .ifEmpty { null }
            }
        }

        val thumbVm = l.optJSONObject("contentImage")
            ?.optJSONObject("collectionThumbnailViewModel")
            ?.optJSONObject("primaryThumbnail")
            ?.optJSONObject("thumbnailViewModel")

        val image = thumbVm?.optJSONObject("image")
        val url = firstThumbUrl(image) ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

        var duration: String? = null
        val overlays = thumbVm?.optJSONArray("overlays")
        if (overlays != null) {
            outer@ for (i in 0 until overlays.length()) {
                val badges = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayBadgeViewModel")
                    ?.optJSONArray("thumbnailBadges") ?: continue
                for (j in 0 until badges.length()) {
                    val b = badges.optJSONObject(j)?.optJSONObject("thumbnailBadgeViewModel") ?: continue
                    val t = b.optString("text")
                    if (t.isNotEmpty()) {
                        duration = t
                        break@outer
                    }
                }
            }
        }

        return VideoItem(
            id = id,
            title = title,
            channel = channel,
            meta = meta,
            thumbnail = url,
            durationText = duration,
        )
    }
}
