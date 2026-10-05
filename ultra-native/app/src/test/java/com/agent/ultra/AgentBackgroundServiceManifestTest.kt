package com.agent.ultra

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentBackgroundServiceManifestTest {
    @Test
    fun backgroundService_usesConnectedDeviceForegroundServiceType() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val serviceStart = manifest.indexOf("android:name=\".AgentBackgroundService\"")
        val serviceEnd = manifest.indexOf("/>", serviceStart)
        val service = manifest.substring(serviceStart, serviceEnd)

        assertTrue(service.contains("android:foregroundServiceType=\"connectedDevice\""))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertFalse(manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertFalse(service.contains("android:onTimeout"))
    }
}
