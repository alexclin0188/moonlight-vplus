package com.limelight.nvstream.mdns

import android.content.Context
import android.net.wifi.WifiManager

import com.limelight.LimeLog

import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.InetAddress
import java.net.NetworkInterface

import javax.jmdns.JmmDNS
import javax.jmdns.NetworkTopologyDiscovery
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import javax.jmdns.impl.NetworkTopologyDiscoveryImpl

class JmDNSDiscoveryAgent(
    context: Context,
    listener: MdnsDiscoveryListener
) : MdnsDiscoveryAgent(listener), ServiceListener {

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryThread: Thread? = null
    private val pendingResolution = HashSet<String>()

    init {
        try {
            val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiMgr.createMulticastLock("Limelight mDNS")
            multicastLock!!.setReferenceCounted(false)
        } catch (e: SecurityException) {
            // Android 10+ (API 29) 需要 ACCESS_COARSE_LOCATION 运行时权限才能创建多播锁。
            // 如果没有权限，multicastLock 保持 null，JmDNS 仍会尝试工作（部分路由/热点环境
            // 即使没有多播锁也能收到响应）。用户授权权限后重新 startDiscovery 即可。
            LimeLog.severe("mDNS: Cannot create multicast lock - missing location permission: ${e.message}")
        }
    }

    private fun handleResolvedServiceInfo(info: ServiceInfo) {
        synchronized(pendingResolution) {
            pendingResolution.remove(info.name)
        }

        try {
            handleServiceInfo(info)
        } catch (e: UnsupportedEncodingException) {
            LimeLog.info("mDNS: Invalid response for machine: ${info.name}")
        }
    }

    private fun handleServiceInfo(info: ServiceInfo) {
        reportNewComputer(info.name, info.port, info.inet4Addresses, info.inet6Addresses)
    }

    override fun startDiscovery(discoveryIntervalMs: Int) {
        stopDiscovery()

        multicastLock?.acquire()

        synchronized(listeners) {
            listeners.add(this)
        }

        discoveryThread = Thread {
            val resolver = referenceResolver()

            try {
                while (!Thread.interrupted()) {
                    resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs.toLong())

                    val pendingNames: ArrayList<String>
                    synchronized(pendingResolution) {
                        pendingNames = ArrayList(pendingResolution)
                    }
                    for (name in pendingNames) {
                        LimeLog.info("mDNS: Retrying service resolution for machine: $name")
                        val infos = resolver.getServiceInfos(SERVICE_TYPE, name, 500)
                        if (infos != null && infos.isNotEmpty()) {
                            LimeLog.info("mDNS: Resolved (retry) with ${infos.size} service entries")
                            for (svcinfo in infos) {
                                handleResolvedServiceInfo(svcinfo)
                            }
                        }
                    }

                    try {
                        Thread.sleep(discoveryIntervalMs.toLong())
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            } finally {
                dereferenceResolver()
            }
        }.apply {
            name = "mDNS Discovery Thread"
            start()
        }
    }

    override fun stopDiscovery() {
        multicastLock?.release()

        synchronized(listeners) {
            listeners.remove(this)
        }

        discoveryThread?.interrupt()
        discoveryThread = null
    }

    override fun serviceAdded(event: ServiceEvent) {
        LimeLog.info("mDNS: Machine appeared: ${event.info.name}")

        val info = event.dns.getServiceInfo(SERVICE_TYPE, event.info.name, 500)
        if (info == null) {
            synchronized(pendingResolution) {
                pendingResolution.add(event.info.name)
            }
            return
        }

        LimeLog.info("mDNS: Resolved (blocking)")
        handleResolvedServiceInfo(info)
    }

    override fun serviceRemoved(event: ServiceEvent) {
        LimeLog.info("mDNS: Machine disappeared: ${event.info.name}")
    }

    override fun serviceResolved(event: ServiceEvent) {
        // We handle this synchronously
    }

    class MyNetworkTopologyDiscovery : NetworkTopologyDiscoveryImpl() {
        override fun useInetAddress(networkInterface: NetworkInterface, interfaceAddress: InetAddress): Boolean {
            return try {
                if (!networkInterface.isUp) return false
                // Omit multicast check - some devices lie about not supporting multicast
                if (networkInterface.isLoopback) return false
                // 跳过 VPN 虚拟网卡（TUN、PPP、rmnet 等），确保 mDNS 套接字绑定到
                // 物理 WiFi/以太网接口，避免 EasyTier 等 VPN 将多播流量路由到虚拟网络。
                val name = networkInterface.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") ||
                    name.startsWith("rmnet") || name.startsWith("pdp") ||
                    name.startsWith("wwan")
                ) return false
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_nvstream._tcp.local."

        private var resolverRefCount = 0
        private val listeners = HashSet<ServiceListener>()
        private val nvstreamListener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                val localListeners: HashSet<ServiceListener>
                synchronized(listeners) {
                    localListeners = HashSet(listeners)
                }
                for (l in localListeners) {
                    l.serviceAdded(event)
                }
            }

            override fun serviceRemoved(event: ServiceEvent) {
                val localListeners: HashSet<ServiceListener>
                synchronized(listeners) {
                    localListeners = HashSet(listeners)
                }
                for (l in localListeners) {
                    l.serviceRemoved(event)
                }
            }

            override fun serviceResolved(event: ServiceEvent) {
                val localListeners: HashSet<ServiceListener>
                synchronized(listeners) {
                    localListeners = HashSet(listeners)
                }
                for (l in localListeners) {
                    l.serviceResolved(event)
                }
            }
        }

        init {
            NetworkTopologyDiscovery.Factory.setClassDelegate { MyNetworkTopologyDiscovery() }
        }

        private fun referenceResolver(): JmmDNS {
            synchronized(JmDNSDiscoveryAgent::class.java) {
                val instance = JmmDNS.Factory.getInstance()
                if (++resolverRefCount == 1) {
                    instance.addServiceListener(SERVICE_TYPE, nvstreamListener)
                }
                return instance
            }
        }

        private fun dereferenceResolver() {
            synchronized(JmDNSDiscoveryAgent::class.java) {
                if (--resolverRefCount == 0) {
                    try {
                        JmmDNS.Factory.close()
                    } catch (e: IOException) {
                        // ignored
                    }
                }
            }
        }
    }
}
