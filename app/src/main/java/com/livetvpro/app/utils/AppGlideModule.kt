package com.livetvpro.app.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.caverock.androidsvg.SVG
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.io.InputStream
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@GlideModule
class AppGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        
        val memoryCacheSizeBytes = (Runtime.getRuntime().maxMemory() * 0.25).toLong()
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
        
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, 100 * 1024 * 1024))
        builder.setDefaultRequestOptions(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .skipMemoryCache(false)
        )
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        val clientBuilder = OkHttpClient.Builder()
            .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)

        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            try {
                val trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                val x509TrustManager = trustManagers[0] as X509TrustManager
                val sslContext = SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, arrayOf(x509TrustManager), null)
                clientBuilder.sslSocketFactory(
                    Tls12SocketFactory(sslContext.socketFactory),
                    x509TrustManager
                )
            } catch (e: Exception) {
                
            }
        }

        val client = clientBuilder.build()
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(client))
        registry.register(SVG::class.java, Drawable::class.java, SvgDrawableTranscoder())
        registry.prepend(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun isManifestParsingEnabled() = false
}
