package com.kachat.app.di

import android.content.Context
import android.util.Log
import com.kachat.app.BuildConfig
import com.kachat.app.util.ApiLogging
import com.kachat.app.util.forLog
import com.kachat.app.util.redactingAddressesForLog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.kachat.app.repository.AppSettingsRepository
import com.kachat.app.services.database.KaChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.kachat.app.services.CoinGeckoApi
import com.kachat.app.services.KaspaRestApi
import com.kachat.app.services.KnsApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

// DataStore extension — creates a single instance per app
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kachat_settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * DataStore for app settings (network endpoints, preferences).
     * Replaces UserDefaults from iOS.
     */
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    /**
     * App settings repository — wraps DataStore with typed accessors.
     */
    @Provides
    @Singleton
    fun provideAppSettingsRepository(dataStore: DataStore<Preferences>): AppSettingsRepository {
        return AppSettingsRepository(dataStore)
    }

    /**
     * Room database - the Portfolio's two tables only. The message, contact, group, broadcast
     * and swap tables went with the chats (iOS wallet 301ebfb): v43 drops them and keeps the
     * Portfolio ([KaChatDatabase.MIGRATION_42_43]); anything older is rebuilt empty.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaChatDatabase {
        return Room.databaseBuilder(
            context,
            KaChatDatabase::class.java,
            "kachat.db"
        )
            .addMigrations(KaChatDatabase.MIGRATION_42_43)
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * OkHttp client for REST API calls (Kaspa REST, Kasia Indexer, KNS API).
     * Phase 3 will add auth interceptors and node-failover logic.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // Per-request success logging is deliberately absent: the chat sync loop fires a request
        // roughly every 2 seconds, so success lines are pure logcat noise. Two interceptors below:
        //
        // 1. An always-on line for failures (exceptions, non-2xx) and slow requests (> 2s, tagged
        //    SLOW) — the only per-request logging in normal operation, matching iOS.
        // 2. HttpLoggingInterceptor, gated behind the persisted "Verbose API Logging" toggle
        //    (Settings > Connection Settings > Diagnostics, default OFF — see ApiLogging).
        //    Request/response bodies include encrypted message payloads and signed transaction
        //    data — BODY level is only useful for local debugging and must never ship in a
        //    release build (logcat is readable by anything with log access on a rooted device,
        //    or captured in bug reports), so release verbose logging caps at BASIC (one line per
        //    request/response, no bodies).
        //
        // Every line here leaves out the wallet's addresses: REST paths and queries carry them,
        // and the diagnostics export bundles logcat. URLs go through HttpUrl.forLog (addresses
        // cut to their last 8 characters, no query string) and the verbose logger's lines are
        // redacted the same way.
        val verboseLogger = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                val line = message.redactingAddressesForLog()
                // Request/response lines ("--> GET https://...", "<-- 200 https://...") lose
                // their query string; body lines keep their text.
                val logged = if (line.startsWith("-->") || line.startsWith("<--")) {
                    line.replace(Regex("\\?[^\\s]*"), "?…")
                } else line
                Log.i("OkHttp", logged)
            }
        }).apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val startNs = System.nanoTime()
                val response = try {
                    chain.proceed(request)
                } catch (e: Exception) {
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                    Log.w(API_LOG_TAG, "FAIL ${request.method} ${request.url.forLog()} after ${elapsedMs}ms: ${e.toString().redactingAddressesForLog()}")
                    throw e
                }
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                if (!response.isSuccessful) {
                    Log.w(API_LOG_TAG, "HTTP ${response.code} ${request.method} ${request.url.forLog()} in ${elapsedMs}ms")
                } else if (elapsedMs > SLOW_REQUEST_THRESHOLD_MS) {
                    Log.w(API_LOG_TAG, "SLOW ${request.method} ${request.url.forLog()} took ${elapsedMs}ms")
                }
                response
            }
            .addInterceptor { chain ->
                if (ApiLogging.verbose) verboseLogger.intercept(chain) else chain.proceed(chain.request())
            }
            // A rate-limited GET (HTTP 429) is retried once after the server's Retry-After,
            // capped so a send never waits long, instead of failing the caller outright. The
            // same allowance iOS makes for its message fetches and price lookups. Reads only:
            // a POST (a transaction submit, a push registration) must not be replayed blind.
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (response.code != 429 || request.method != "GET") return@addInterceptor response
                val retryAfterMs = response.header("Retry-After")?.trim()?.toLongOrNull()
                    ?.let { it * 1000L }
                    ?.coerceIn(500L, MAX_RATE_LIMIT_WAIT_MS)
                    ?: DEFAULT_RATE_LIMIT_WAIT_MS
                Log.w(API_LOG_TAG, "HTTP 429 ${request.url.forLog()} - retrying once in ${retryAfterMs}ms")
                response.close()
                try {
                    Thread.sleep(retryAfterMs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("interrupted while waiting out a rate limit")
                }
                chain.proceed(request)
            }
            .build()
    }

    private const val API_LOG_TAG = "ApiLog"

    /** How long a rate-limited GET waits before its one retry when the server gives no
     *  Retry-After, and the most it will honor when it does - a send cannot sit for a minute. */
    private const val DEFAULT_RATE_LIMIT_WAIT_MS = 2_000L
    private const val MAX_RATE_LIMIT_WAIT_MS = 5_000L

    /** Anything slower than this on a healthy endpoint is worth a line even on success. */
    private const val SLOW_REQUEST_THRESHOLD_MS = 2_000L

    @Provides
    @Singleton
    fun provideKaspaRestApi(okHttpClient: OkHttpClient): KaspaRestApi {
        return Retrofit.Builder()
            .baseUrl("https://api.kaspa.org/") // Default, will be updated by a wrapper
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KaspaRestApi::class.java)
    }

    @Provides
    @Singleton
    fun provideKnsApi(okHttpClient: OkHttpClient): KnsApi {
        return Retrofit.Builder()
            .baseUrl("https://api.kns.kaspa.org/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KnsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(okHttpClient: OkHttpClient): CoinGeckoApi {
        return Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }
}
