package com.newsaggregator.utils

import android.content.Context
import android.util.Log
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS helper. Provides OkHttpClient with custom DNS via DoH.
 * Supports: System (default), Google DNS, Cloudflare DNS.
 */
object DnsHelper {

    const val DNS_SYSTEM = 0
    const val DNS_GOOGLE = 1
    const val DNS_CLOUDFLARE = 2

    private const val PREFS = "app_prefs"
    private const val KEY_DNS = "dns_mode"
    private const val TAG = "DnsHelper"

    fun getDnsMode(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DNS, DNS_SYSTEM)
    }

    fun setDnsMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DNS, mode).apply()
    }

    fun getDnsName(context: Context): String {
        return when (getDnsMode(context)) {
            DNS_GOOGLE -> "Google (8.8.8.8)"
            DNS_CLOUDFLARE -> "Cloudflare (1.1.1.1)"
            else -> "System Default"
        }
    }

    /** Apply the configured DNS resolver onto an existing builder. */
    fun applyDns(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder {
        return when (getDnsMode(context)) {
            DNS_GOOGLE -> builder.dns(DohDns("https://dns.google/resolve"))
            DNS_CLOUDFLARE -> builder.dns(DohDns("https://cloudflare-dns.com/dns-query"))
            else -> builder
        }
    }

    /** Build an OkHttpClient with DNS-over-HTTPS support. */
    fun buildClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return applyDns(builder, context).build()
    }

    /** Build a fast client (shorter timeouts for RSS fetching). */
    fun buildFastClient(context: Context): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return applyDns(builder, context).build()
    }

    /**
     * DNS-over-HTTPS resolver using Google/Cloudflare JSON API.
     * Falls back to system DNS if DoH fails.
     */
    private class DohDns(private val dohUrl: String) : Dns {
        private val dohClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        @Throws(UnknownHostException::class)
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                val req = Request.Builder()
                    .url("$dohUrl?name=$hostname&type=A")
                    .header("Accept", "application/dns-json")
                    .build()

                dohClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val results = ArrayList<InetAddress>()
                        var idx = 0
                        while (body.indexOf("\"data\":\"", idx).also { idx = it } != -1) {
                            val start = idx + 8
                            val end = body.indexOf('"', start)
                            if (end == -1) break
                            val ip = body.substring(start, end)
                            // Skip non-IP entries (CNAME, etc.)
                            if (Regex("\\d+\\.\\d+\\.\\d+\\.\\d+").matches(ip) || ip.contains(":")) {
                                try {
                                    results.add(InetAddress.getByName(ip))
                                } catch (_: Exception) {
                                }
                            }
                            idx = end + 1
                        }
                        if (results.isNotEmpty()) {
                            return results
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DoH lookup failed for $hostname, falling back to system", e)
            }
            // Fallback to system DNS
            return Dns.SYSTEM.lookup(hostname)
        }
    }
}
