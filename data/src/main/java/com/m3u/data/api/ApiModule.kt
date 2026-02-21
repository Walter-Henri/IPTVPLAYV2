@file:Suppress("unused")

package com.m3u.data.api

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Cookie
import okhttp3.CookieJar
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class OkhttpClient(val chucker: Boolean)

@Module
@InstallIn(SingletonComponent::class)
internal object ApiModule {
    @Provides
    @Singleton
    @OkhttpClient(true)
    fun provideChuckerOkhttpClient(
        @ApplicationContext context: Context,
        @OkhttpClient(false) okhttpClient: OkHttpClient
    ): OkHttpClient {
        return okhttpClient
            .newBuilder()
            .addInterceptor(
                ChuckerInterceptor.Builder(context)
                    .maxContentLength(10240)
                    .build()
            )
            .build()
    }

    @Provides
    @Singleton
    @OkhttpClient(false)
    fun provideOkhttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
                val key = url.host
                val list = cookieStore.getOrPut(key) { mutableListOf() }
                list.removeAll { c -> cookies.any { it.name == c.name } }
                list.addAll(cookies)
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        }
        val builder = OkHttpClient.Builder()
            .authenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host
                val uas = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
                    "Mozilla/5.0 (Android 14; Mobile; rv:122.0) Gecko/122.0 Firefox/122.0",
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1"
                )
                val ua = uas.random()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .header("DNT", "1") // Do Not Track
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"121\", \"Google Chrome\";v=\"121\"")
                    .header("Sec-Ch-Ua-Mobile", if (ua.contains("Mobile")) "?1" else "?0")
                    .header("Sec-Ch-Ua-Platform", when {
                        ua.contains("Windows") -> "\"Windows\""
                        ua.contains("Macintosh") -> "\"macOS\""
                        ua.contains("Android") -> "\"Android\""
                        ua.contains("iPhone") -> "\"iOS\""
                        else -> "\"Linux\""
                    })
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "cross-site")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                
                val request = requestBuilder.build()
                chain.proceed(request)
            }
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: Exception) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(999)
                        .message(e.message.orEmpty())
                        .body("{${e}}".toResponseBody())
                        .build()
                }
            }
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")?.toIntOrNull()
        if (!proxyHost.isNullOrBlank() && proxyPort != null) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        @OkhttpClient(true) okHttpClient: OkHttpClient
    ): Retrofit.Builder {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory(mediaType))
            .client(okHttpClient)
    }
}
