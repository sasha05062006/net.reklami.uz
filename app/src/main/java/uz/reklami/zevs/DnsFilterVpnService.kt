package uz.reklami.zevs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only VPN: routes DNS packets into this service and leaves all other traffic direct.
 * This is intentionally separate from a full-tunnel VPN. A later WireGuard integration
 * must be composed into this same VPN owner because Android allows one VPN per user.
 */
class DnsFilterVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFilter()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (running.compareAndSet(false, true)) worker.execute { establishTunnel() }
        return START_STICKY
    }

    private fun establishTunnel() {
        try {
            val builder = Builder()
                .setSession("Zevs DNS Protection")
                .setMtu(1500)
                .addAddress("10.111.0.1", 32)
                .addDnsServer("10.111.0.2")
                .addRoute("10.111.0.2", 32)
            tun = builder.establish()
            val fd = tun ?: throw IllegalStateException("VPN interface was not created")
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val packet = ByteArray(32767)
            while (running.get()) {
                val length = input.read(packet)
                if (length <= 0) continue
                val response = processDnsPacket(packet.copyOf(length))
                if (response != null) output.write(response)
            }
        } catch (_: Exception) {
            running.set(false)
            stopSelf()
        } finally {
            tun?.close()
            tun = null
        }
    }

    private fun processDnsPacket(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        if (version != 4) return null // IPv4 DNS path; IPv6 support is a later compatibility task.
        val ihl = (packet[0].toInt() and 0x0f) * 4
        if (packet.size < ihl + 8 || packet[9].toInt() != 17) return null
        val udpLength = u16(packet, ihl + 4)
        if (udpLength < 8 || packet.size < ihl + udpLength) return null
        val destPort = u16(packet, ihl + 2)
        if (destPort != 53) return null
        val dnsStart = ihl + 8
        val dns = packet.copyOfRange(dnsStart, ihl + udpLength)
        if (dns.size < 17) return null
        val domain = readQuestionName(dns) ?: return null
        val answer = if (isBlocked(domain)) blockedReply(dns) else queryDoh(dns) ?: blockedReply(dns, servfail = true)
        return buildIpv4UdpReply(packet, ihl, dnsStart, answer)
    }

    private fun isBlocked(domain: String): Boolean {
        val d = domain.trimEnd('.').lowercase()
        val blocked = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adnxs.com", "app-measurement.com",
            "facebook.net", "scorecardresearch.com", "adform.net",
            "adsrvr.org", "taboola.com", "outbrain.com", "unityads.unity3d.com"
        )
        return blocked.any { d == it || d.endsWith(".$it") }
    }

    private fun queryDoh(query: ByteArray): ByteArray? {
        // DNS-over-TLS uses resolver IPs and TLS SNI, avoiding recursive DNS lookups.
        // Platform certificate validation remains enabled.
        val endpoints = listOf("1.1.1.1" to "cloudflare-dns.com", "8.8.8.8" to "dns.google")
        for ((ip, host) in endpoints) {
            var socket: SSLSocket? = null
            try {
                socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
                if (!protect(socket)) throw IllegalStateException("Unable to protect DNS-over-TLS socket from VPN routing")
                socket.connect(InetSocketAddress(ip, 853), 5000)
                val params = socket.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                params.endpointIdentificationAlgorithm = "HTTPS"
                socket.sslParameters = params
                socket.soTimeout = 6000
                socket.startHandshake()
                val output = socket.outputStream
                output.write((query.size ushr 8) and 0xff)
                output.write(query.size and 0xff)
                output.write(query)
                output.flush()
                val input = socket.inputStream
                val hi = input.read()
                val lo = input.read()
                if (hi < 0 || lo < 0) continue
                val size = (hi shl 8) or lo
                if (size !in 12..65535) continue
                val response = ByteArray(size)
                var offset = 0
                while (offset < size) {
                    val count = input.read(response, offset, size - offset)
                    if (count < 0) break
                    offset += count
                }
                if (offset == size) return response
            } catch (_: Exception) {
                // Try the next public DNS-over-TLS endpoint.
            } finally {
                try { socket?.close() } catch (_: Exception) { }
            }
        }
        return null
    }

    private fun readQuestionName(dns: ByteArray): String? {
        if (dns.size < 17) return null
        var p = 12
        val labels = mutableListOf<String>()
        while (p < dns.size) {
            val n = dns[p++].toInt() and 0xff
            if (n == 0) break
            if (n > 63 || p + n > dns.size) return null
            labels += String(dns, p, n, Charsets.US_ASCII)
            p += n
        }
        return if (labels.isEmpty()) null else labels.joinToString(".")
    }

    private fun blockedReply(query: ByteArray, servfail: Boolean = false): ByteArray {
        val out = query.copyOf()
        val flags = u16(query, 2)
        put16(out, 2, (flags or 0x8000 or 0x0080) and 0xfff0 or if (servfail) 2 else 3)
        put16(out, 6, 0)
        put16(out, 8, 0)
        put16(out, 10, 0)
        return out
    }

    private fun buildIpv4UdpReply(original: ByteArray, ihl: Int, dnsStart: Int, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val totalLen = ihl + udpLen
        val out = ByteArray(totalLen)
        original.copyInto(out, 0, 0, ihl)
        // Swap IPv4 addresses.
        for (i in 0..3) {
            out[12 + i] = original[16 + i]
            out[16 + i] = original[12 + i]
        }
        put16(out, 2, totalLen)
        out[10] = 0; out[11] = 0
        put16(out, 10, checksum(out, 0, ihl))
        put16(out, ihl, u16(original, ihl + 2))
        put16(out, ihl + 2, u16(original, ihl))
        put16(out, ihl + 4, udpLen)
        out[ihl + 6] = 0; out[ihl + 7] = 0
        dns.copyInto(out, ihl + 8)
        put16(out, ihl + 6, udpChecksum(out, ihl, udpLen))
        return out
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += u16(data, i).toLong()
            i += 2
        }
        if (i < offset + length) sum += ((data[i].toInt() and 0xff) shl 8).toLong()
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun udpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        for (i in 12..18 step 2) sum += u16(packet, i).toLong()
        sum += 17
        sum += udpLength
        var i = udpOffset
        while (i + 1 < udpOffset + udpLength) {
            sum += u16(packet, i).toLong()
            i += 2
        }
        if (i < udpOffset + udpLength) sum += ((packet[i].toInt() and 0xff) shl 8).toLong()
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        val result = sum.inv().toInt() and 0xffff
        return if (result == 0) 0xffff else result
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
    private fun put16(b: ByteArray, i: Int, value: Int) {
        b[i] = (value ushr 8).toByte()
        b[i + 1] = value.toByte()
    }

    private fun buildNotification(): Notification {
        val channelId = "zevs_protection"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channelId, "Zevs protection", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Zevs защищает DNS")
            .setContentText("DNS-фильтрация активна; остальной трафик идёт напрямую")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun stopFilter() {
        running.set(false)
        tun?.close()
        tun = null
    }

    override fun onDestroy() {
        stopFilter()
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "uz.reklami.zevs.STOP_DNS"
        private const val NOTIFICATION_ID = 7301
    }
}
