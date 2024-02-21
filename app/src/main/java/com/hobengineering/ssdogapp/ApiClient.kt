package com.hobengineering.ssdogapp

import android.util.Log
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.Callback as RetrofitCallback
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


// The 'ApiClient' is a singleton object used to make calls to our backend and return their results
object ApiClient {

    private const val BACKEND_URL = "https://us-central1-ss-dog-app.cloudfunctions.net"
    private const val TAG = "ApiClient"

    // Add HttpLoggingInterceptor for detailed logs
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addNetworkInterceptor(StethoInterceptor())
        .addInterceptor(loggingInterceptor) // Add the logging interceptor here
        .build()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BACKEND_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val service: BackendService = retrofit.create(BackendService::class.java)

    @Throws(ConnectionTokenException::class)
    internal fun createConnectionToken(): String {
        try {
            val result = service.getConnectionToken().execute()
            if (result.isSuccessful && result.body() != null) {
                return result.body()!!.secret
            } else {
                Log.e(TAG, "Error fetching connection token: ${result.errorBody()?.string()}")
                throw ConnectionTokenException("Creating connection token failed")
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException when fetching connection token", e)
            throw ConnectionTokenException("Creating connection token failed", e)
        }
    }

    internal fun capturePaymentIntent(id: String) {
        service.capturePaymentIntent(id).execute()
    }

    fun createPaymentIntent(amount: Int, currency: String, callback: RetrofitCallback<PaymentIntentResponse>) {
        val request = PaymentIntentRequest(amount, currency)
        service.createPaymentIntent(request).enqueue(callback)
    }
}
