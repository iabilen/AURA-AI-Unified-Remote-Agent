package com.agent.ultra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AgentBackgroundServiceManifestTest {
    @Test
    fun backgroundService_usesConnectedDeviceForegroundServiceType() {
        val manifest = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val services = document.getElementsByTagName("service")
        val service = (0 until services.length)
            .map { services.item(it) }
            .firstOrNull {
                it.attributes?.getNamedItemNS(androidNs, "name")?.nodeValue == ".AgentBackgroundService"
            }

        assertNotNull("AgentBackgroundService must be declared", service)
        assertEquals(
            "connectedDevice",
            service!!.attributes.getNamedItemNS(androidNs, "foregroundServiceType")?.nodeValue,
        )

        val permissions = document.getElementsByTagName("uses-permission")
        fun hasPermission(name: String): Boolean = (0 until permissions.length).any {
            permissions.item(it).attributes.getNamedItemNS(androidNs, "name")?.nodeValue == name
        }

        assertEquals(true, hasPermission("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"))
        assertEquals(false, hasPermission("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertNull(service.attributes.getNamedItemNS(androidNs, "onTimeout"))
    }
}
