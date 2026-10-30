package com.artkashin.aiwalkingguide.tour

import org.junit.Assert.*
import org.junit.Test

class GuidePolicyTest {
    private val origin=Position(55.75,37.62)
    private fun poi(id: String,p: Position=origin)=Poi(id,id,p,"Достаточно подробное описание реального объекта. ".repeat(4),"https://example.org/$id")
    @Test fun offRouteObjectIsEligibleWithoutAnyGoals() {
        assertEquals("off",GuidePolicy.candidate(origin,listOf(poi("off")),emptySet())?.id)
    }
    @Test fun completedSkippedAndAliasesRemainSilent() {
        val p=poi("canonical").copy(aliases=setOf("old-provider-id"))
        assertNull(GuidePolicy.candidate(origin,listOf(p),setOf("canonical")))
        assertNull(GuidePolicy.candidate(origin,listOf(p),setOf("old-provider-id")))
    }
    @Test fun inaccurateFarEmptyAndUngroundedResultsAreSilent() {
        assertNull(GuidePolicy.candidate(origin.copy(accuracy=120f),listOf(poi("p")),emptySet()))
        assertNull(GuidePolicy.candidate(origin,listOf(poi("far",Position(56.0,38.0))),emptySet()))
        assertNull(GuidePolicy.candidate(origin,emptyList(),emptySet()))
        assertNull(GuidePolicy.candidate(origin,listOf(poi("short").copy(extract="name")),emptySet()))
    }
    @Test fun detourReordersRemainingGoalsFromActualPosition() {
        val a=poi("a",origin);val b=poi("b",Position(55.77,37.62));val c=poi("c",Position(55.78,37.62))
        assertEquals("a",GuidePolicy.route(origin,listOf(a,b,c),emptySet()).first().id)
        assertEquals(listOf("c","b"),GuidePolicy.route(c.position,listOf(a,b,c),setOf("a")).map{it.id})
        assertEquals("b",GuidePolicy.route(b.position,listOf(a,b),emptySet()).first().id)
    }
    @Test fun mapsDelegatesWalkingWithoutStaleOrigin() {
        val url=GuidePolicy.mapsUrl(poi("p"));assertTrue(url.contains("travelmode=walking"));assertTrue(url.contains("api=1"));assertFalse(url.contains("origin="))
    }
    @Test fun longSpeechKeepsEveryWordAndRespectsEngineLimit() {
        val text=(1..2000).joinToString(" "){"Слово$it."}
        val chunks=SpeechChunks.split(text,1800)
        assertTrue(chunks.size>1);assertTrue(chunks.all{it.length<=1800})
        assertEquals(text,chunks.joinToString(" "))
        assertTrue(SpeechChunks.split("").isEmpty())
    }
    @Test fun replayPassingCompletedPoiNeverSelectsItAgain() {
        val p=poi("p");val seen=mutableSetOf<String>()
        val fixes=listOf(origin.copy(lon=37.619),origin,origin.copy(lon=37.621),origin)
        val spoken=fixes.mapNotNull { fix -> GuidePolicy.candidate(fix,listOf(p),seen)?.also{seen.add(it.id)} }
        assertEquals(1,spoken.size)
    }
}
