package com.rate.sdk.ui.kit.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandPaletteTest {

    private fun cmd(id: String, title: String, subtitle: String = "", keywords: String = "") =
        AegisCommand(id = id, title = title, subtitle = subtitle, keywords = keywords, action = {})

    private val catalog = listOf(
        cmd("home", "Home", "Surface · dashboard"),
        cmd("plans", "Plans", "Surface · catalog", keywords = "products"),
        cmd("quotes", "Quotes", "Surface · pricing"),
    )

    @Test
    fun emptyQueryReturnsCatalogOrder() {
        val out = matchCommands("", catalog)
        assertEquals(listOf("home", "plans", "quotes"), out.map { it.id })
    }

    @Test
    fun prefixMatchOnTitleRanksFirst() {
        val out = matchCommands("pl", catalog)
        assertEquals("plans", out.first().id)
    }

    @Test
    fun keywordMatchIsFoundButRanksAfterTitle() {
        val out = matchCommands("products", catalog)
        assertEquals(listOf("plans"), out.map { it.id })
    }

    @Test
    fun recentsSurfaceAheadOfAllWhenQueryEmpty() {
        val sections = buildSections("", catalog, recentIds = listOf("quotes"))
        assertEquals("Recent", sections.first().title)
        assertEquals(listOf("quotes"), sections.first().commands.map { it.id })
        // The recent command is not duplicated in the "All" tail.
        val all = sections.last()
        assertTrue(all.commands.none { it.id == "quotes" })
    }

    @Test
    fun nonEmptyQueryIgnoresRecents() {
        val sections = buildSections("home", catalog, recentIds = listOf("quotes"))
        assertEquals(1, sections.size)
        assertEquals(null, sections.first().title)
        assertEquals(listOf("home"), sections.first().commands.map { it.id })
    }
}
