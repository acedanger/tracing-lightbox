package com.acedanger.traceart.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PictureCatalogTest {

    @Test
    fun parsesValidManifest() {
        val json = """{"pictures":[
            {"id":"tiger","name":"Tiger","file":"tiger.svg"},
            {"id":"cat","name":"Cat","file":"cat.png"}]}"""
        assertEquals(
            listOf(Picture("tiger", "Tiger", "tiger.svg"), Picture("cat", "Cat", "cat.png")),
            parseManifest(json),
        )
    }

    @Test
    fun skipsEntryMissingField() {
        val json = """{"pictures":[
            {"id":"a","name":"A"},
            {"id":"b","name":"B","file":"b.svg"}]}"""
        assertEquals(listOf(Picture("b", "B", "b.svg")), parseManifest(json))
    }

    @Test
    fun skipsUnsupportedExtension() {
        val json = """{"pictures":[
            {"id":"a","name":"A","file":"x.gif"},
            {"id":"b","name":"B","file":"X.PNG"}]}"""
        assertEquals(listOf(Picture("b", "B", "X.PNG")), parseManifest(json))
    }

    @Test
    fun duplicateIdKeepsFirst() {
        val json = """{"pictures":[
            {"id":"a","name":"First","file":"1.svg"},
            {"id":"a","name":"Second","file":"2.svg"}]}"""
        assertEquals(listOf(Picture("a", "First", "1.svg")), parseManifest(json))
    }

    @Test
    fun brokenJsonGivesEmptyList() {
        assertEquals(emptyList<Picture>(), parseManifest("{not json"))
    }
}
