package com.livetvpro.app.di

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.livetvpro.app.BuildConfig
import com.livetvpro.app.utils.Tls12SocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .build()

        val builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.N_MR1) {
            val sslContext = SSLContext.getInstance("TLS").also { it.init(null, null, null) }
            builder.sslSocketFactory(
                Tls12SocketFactory(sslContext.socketFactory),
                okhttp3.internal.platform.Platform.get().trustManager(sslContext.socketFactory)
                    ?: javax.net.ssl.TrustManagerFactory
                        .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
                        .also { it.init(null as java.security.KeyStore?) }
                        .trustManagers
                        .first { it is javax.net.ssl.X509TrustManager }
                        as javax.net.ssl.X509TrustManager
            )
        }

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }
}
