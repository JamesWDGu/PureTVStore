package com.puretv.store.data

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkAddress {
    /** Prefer Wi-Fi, then Ethernet, over other interfaces. */
    fun localIpv4(): String? {
        val candidates = mutableListOf<Pair<Int, String>>()
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                val score = when {
                    nif.name.startsWith("wlan") -> 0
                    nif.name.startsWith("eth") -> 1
                    else -> 2
                }
                nif.inetAddresses.toList().forEach { address ->
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        address.isSiteLocalAddress
                    ) {
                        address.hostAddress?.let { candidates += score to it }
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return candidates.sortedBy { it.first }.firstOrNull()?.second
    }
}
