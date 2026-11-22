package com.acedanger.traceart.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {

    @Test
    fun parsesTags() {
        val json = """{"pictures":[{"id":"a","name":"A","file":"a.svg","tags":["cats","animals"]}]}"""
        assertEquals(listOf("cats", "animals"), parseManifest(json).single().tags)
    }

    @Test
    fun missingTagsMeansNoTags() {
        val json = """{"pictures":[{"id":"a","name":"A","file":"a.svg"}]}"""
        assertEquals(emptyList<String>(), parseManifest(json).single().tags)
    }

    @Test
    fun tagsAreTrimmedLowercasedAndDeduplicated() {
        val json = """{"pictures":[{"id":"a","name":"A","file":"a.svg","tags":[" Cats ","cats","ANIME"]}]}"""
        assertEquals(listOf("cats", "anime"), parseManifest(json).single().tags)
    }

    @Test
    fun badTagValuesAreSkippedButPictureIsKept() {
        val json = """{"pictures":[{"id":"a","name":"A","file":"a.svg","tags":["ok", 3, "", null, {"x":1}]}]}"""
        assertEquals(listOf("ok"), parseManifest(json).single().tags)
        val notAList = """{"pictures":[{"id":"a","name":"A","file":"a.svg","tags":"cats"}]}"""
        assertEquals(emptyList<String>(), parseManifest(notAList).single().tags)
    }

    private val pics = listOf(
        Picture("tiger", "Tiger", "t.svg", listOf("animals")),
        Picture("cat", "Cat", "c.svg", listOf("cats", "animals")),
        Picture("acat", "Anime Cat", "ac.svg", listOf("cats", "anime")),
        Picture("pizza", "Pizza", "p.svg", listOf("food")),
        Picture("plain", "Plain", "x.svg"),
    )

    @Test
    fun allTagsAreUniqueAndSortedByName() {
        assertEquals(listOf("animals", "anime", "cats", "food"), allTags(pics))
    }

    @Test
    fun filterByTagKeepsOrder() {
        assertEquals(listOf("tiger", "cat"), filterByTag(pics, "animals").map { it.id })
        assertEquals(listOf("cat", "acat"), filterByTag(pics, "cats").map { it.id })
    }

    @Test
    fun noTagShowsEverything() {
        assertEquals(pics, filterByTag(pics, null))
    }

    @Test
    fun bundledManifestLoadsEveryPictureWithTags() {
        val file = java.io.File("src/main/assets/pictures/manifest.json")
        val text = file.readText()
        val entries = Regex("\"id\"").findAll(text).count()
        val parsed = parseManifest(text)
        assertEquals(entries, parsed.size)
        parsed.forEach { assert(it.tags.isNotEmpty()) { "${it.id} has no tags" } }
    }

    @Test
    fun unknownTagShowsNothing() {
        assertEquals(emptyList<Picture>(), filterByTag(pics, "spaceships"))
    }
}
