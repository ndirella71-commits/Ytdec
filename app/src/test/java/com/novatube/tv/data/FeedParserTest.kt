package com.novatube.tv.data

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses a real InnerTube /search response captured from YouTube so the renderer-walking parser is
 * checked against actual data rather than a hand-written fixture.
 */
class FeedParserTest {

    private fun load(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun parsesRealSearchResponse() {
        val page = FeedParser.parse(JSONObject(load("search.json")))
        val items = page.shelves.flatMap { it.items }

        assertTrue("expected a shelf of videos, got ${page.shelves.size}", items.size >= 10)
        assertTrue("every item needs an id and title", items.all { it.id.isNotEmpty() && it.title.isNotEmpty() })
        assertTrue("titles should look like text", items.first().title.length > 2)
        assertTrue("thumbnails should be populated", items.all { !it.thumbnail.isNullOrEmpty() })

        println("parsed ${items.size} videos in ${page.shelves.size} shelf/shelves")
        println("shelf titles: ${page.shelves.map { it.title }}")
        println("first item: ${items.first()}")
    }
}
