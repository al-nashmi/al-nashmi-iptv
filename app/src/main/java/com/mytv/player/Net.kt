package com.mytv.player

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object Net {
    // Some IPTV panels reject unknown clients, so send a common media-player agent.
    const val USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    /** Blocking GET; call from Dispatchers.IO. */
    fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }

    fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "تعذر الوصول للسيرفر. تأكد من الرابط ومن اتصال الإنترنت."
        is SocketTimeoutException -> "السيرفر ما رد في الوقت المحدد. حاول مرة ثانية."
        is IllegalArgumentException -> "الرابط غير صحيح."
        else -> "حدث خطأ: ${e.message ?: e.javaClass.simpleName}"
    }
}
