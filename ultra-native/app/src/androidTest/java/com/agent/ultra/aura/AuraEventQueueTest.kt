package com.agent.ultra.aura

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuraEventQueueTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearQueue()
    }

    @After
    fun tearDown() {
        clearQueue()
    }

    @Test
    fun enqueue_persists_across_queue_instances() {
        val first = AuraEventQueue(context)
        first.enqueue(event("persisted", timestamp = 10))

        val second = AuraEventQueue(context)
        assertEquals(listOf("persisted"), second.pending().map { it.optString("id") })
        assertFalse(second.pending().first().optBoolean("inFlight"))
    }

    @Test
    fun duplicate_id_is_not_enqueued_twice() {
        val queue = AuraEventQueue(context)
        queue.enqueue(event("same", timestamp = 10))
        queue.enqueue(event("same", timestamp = 20))

        assertEquals(1, queue.pending().size)
        assertEquals(10L, queue.pending().first().optLong("timestampMs"))
    }

    @Test
    fun noisy_state_events_coalesce_only_when_not_in_flight() {
        val queue = AuraEventQueue(context)
        queue.enqueue(event("battery-1", type = "battery", timestamp = 10))
        queue.enqueue(event("battery-2", type = "battery", timestamp = 20))

        assertEquals(listOf("battery-2"), queue.pending().map { it.optString("id") })

        queue.markInFlight("battery-2")
        queue.enqueue(event("battery-3", type = "battery", timestamp = 30))

        assertEquals(listOf("battery-2", "battery-3"), queue.pending().map { it.optString("id") })
        assertTrue(queue.pending().first().optBoolean("inFlight"))
    }

    @Test
    fun ack_removes_event_and_survives_new_queue_instance() {
        val queue = AuraEventQueue(context)
        queue.enqueue(event("acked", timestamp = 10))
        queue.enqueue(event("kept", timestamp = 20))
        queue.markInFlight("acked")
        queue.ack("acked")

        val reopened = AuraEventQueue(context)
        assertEquals(listOf("kept"), reopened.pending().map { it.optString("id") })
    }

    @Test
    fun pending_orders_priority_before_timestamp() {
        val queue = AuraEventQueue(context)
        queue.enqueue(event("low-new", priority = 0, timestamp = 30))
        queue.enqueue(event("high-old", priority = 5, timestamp = 10))
        queue.enqueue(event("medium", priority = 2, timestamp = 20))
        queue.enqueue(event("high-new", priority = 5, timestamp = 40))

        assertEquals(
            listOf("high-old", "high-new", "medium", "low-new"),
            queue.pending().map { it.optString("id") },
        )
    }

    @Test
    fun bound_trimming_preserves_in_flight_event() {
        val queue = AuraEventQueue(context)
        repeat(100) { index ->
            queue.enqueue(event("e-$index", priority = 0, timestamp = index.toLong()))
        }
        queue.markInFlight("e-0")
        queue.enqueue(event("e-100", priority = 0, timestamp = 100))

        val ids = queue.pending().map { it.optString("id") }
        assertEquals(100, ids.size)
        assertTrue(ids.contains("e-0"))
        assertFalse(ids.contains("e-1"))
        assertTrue(ids.contains("e-100"))
    }

    @Test
    fun all_in_flight_events_are_not_trimmed_away() {
        val queue = AuraEventQueue(context)
        repeat(100) { index ->
            val id = "flight-$index"
            queue.enqueue(event(id, timestamp = index.toLong()))
            queue.markInFlight(id)
        }
        queue.enqueue(event("flight-100", timestamp = 100))

        val ids = queue.pending().map { it.optString("id") }
        assertEquals(101, ids.size)
        assertTrue(ids.contains("flight-0"))
        assertTrue(ids.contains("flight-100"))
    }

    @Test
    fun different_connectivity_transports_do_not_coalesce() {
        val queue = AuraEventQueue(context)
        queue.enqueue(event("wifi", type = "connectivity", timestamp = 10, payload = "wifi"))
        queue.enqueue(event("mobile", type = "connectivity", timestamp = 20, payload = "mobile"))

        assertEquals(listOf("mobile", "wifi"), queue.pending().map { it.optString("id") })
    }

    private fun event(
        id: String,
        type: String = "custom",
        priority: Int = 0,
        timestamp: Long,
        payload: String = "",
    ) = AuraEvent(
        id = id,
        type = type,
        source = "test",
        timestampMs = timestamp,
        priority = priority,
        payload = org.json.JSONObject().apply {
            if (type == "connectivity") put("transport", payload)
        },
    )

    private fun clearQueue() {
        context.getSharedPreferences("aura_event_queue", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
