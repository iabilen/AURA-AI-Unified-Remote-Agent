package com.agent.ultra.aura

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Best-effort on-device location monitor. Location is stored locally and emitted as an AURA event. */
class AuraLocationPresence(
    context: Context,
    private val publish: (AuraEvent) -> Unit,
    private val store: AuraPresenceStore
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val provider = location.provider ?: "unknown"
            store.update(
                mapOf(
                    "lastLocationAt" to location.time,
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracyMeters" to location.accuracy,
                    "provider" to provider
                )
            )
            publish(
                AuraEvent(
                    id = "location:${location.time}:${location.latitude}:${location.longitude}",
                    type = "location",
                    source = "android.location",
                    priority = 1,
                    payload = JSONObject()
                        .put("latitude", location.latitude)
                        .put("longitude", location.longitude)
                        .put("accuracyMeters", location.accuracy)
                        .put("provider", provider)
                        .put("timestampMs", location.time)
                )
            )
        }
    }

    fun start() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
                .forEach { provider ->
                    locationManager.requestLocationUpdates(provider, 60_000L, 50f, listener)
                    locationManager.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
                }
        }
    }

    fun stop() = runCatching { locationManager.removeUpdates(listener) }.getOrNull()
}
