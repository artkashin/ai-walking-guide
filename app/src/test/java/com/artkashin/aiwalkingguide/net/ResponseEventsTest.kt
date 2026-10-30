package com.artkashin.aiwalkingguide.net

import org.junit.Assert.*
import org.junit.Test

class ResponseEventsTest {
    @Test fun completedStreamReturnsCombinedRussianText() {
        val s=ResponseEvents();s.accept("""{"type":"response.output_text.delta","delta":"Привет, "}""")
        s.accept("""{"type":"response.output_text.delta","delta":"путешественник!"}""")
        s.accept("""{"type":"response.completed"}""")
        assertEquals("Привет, путешественник!",s.result())
    }
    @Test fun truncatedOrDoneOnlyStreamsNeverNarratePartialText() {
        val s=ResponseEvents();s.accept("""{"type":"response.output_text.delta","delta":"Начало"}""");s.accept("[DONE]")
        assertThrows(ServiceFailure::class.java){s.result()}
    }
    @Test fun exhaustedSubscriptionAfterDeltaNeverCompletes() {
        val s=ResponseEvents();s.accept("""{"type":"response.output_text.delta","delta":"Начало"}""")
        assertThrows(ServiceFailure::class.java){s.accept("""{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}""")}
        assertFalse(s.completed)
    }
}
