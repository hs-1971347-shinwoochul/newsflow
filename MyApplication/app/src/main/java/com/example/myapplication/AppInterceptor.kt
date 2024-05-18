package com.example.myapplication

import okhttp3.Interceptor
import retrofit2.Response
import java.io.IOException

class AppInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain) : okhttp3.Response = with(chain) {
        val newRequest = request().newBuilder()
            .addHeader("(header Key)", "(header Value)")
            .build()
        proceed(newRequest)
    }
}