package com.artkashin.aiwalkingguide.audio

import org.junit.Assert.*
import org.junit.Test

class PlaybackTimelineTest {
    @Test fun seeksAcrossSegmentsInBothDirectionsAndClamps() {
        val durations=listOf(2000L,3000L,1000L)
        assertEquals(0 to 500L,PlaybackTimeline.locate(durations,500))
        assertEquals(1 to 0L,PlaybackTimeline.locate(durations,2000))
        assertEquals(1 to 1500L,PlaybackTimeline.locate(durations,3500))
        assertEquals(0 to 0L,PlaybackTimeline.locate(durations,-100))
        assertEquals(2 to 1000L,PlaybackTimeline.locate(durations,999999))
    }
    @Test fun formatsActualPlaybackTimesIncludingHours() {
        assertEquals("0:00",PlaybackTimeline.time(-1))
        assertEquals("1:05",PlaybackTimeline.time(65000))
        assertEquals("1:01:01",PlaybackTimeline.time(3661000))
    }
}
