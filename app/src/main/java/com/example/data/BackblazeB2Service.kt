package com.example.data

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

interface BackblazeB2Service {

    @POST("api/upload/get-presigned-url")
    suspend fun getPresignedUrl(
        @Header("Authorization") bearerToken: String,
        @Body request: PresignedUrlRequest
    ): Response<PresignedUrlResponse>

    @PUT
    suspend fun uploadFileDirectly(
        @Url uploadUrl: String,
        @Header("Content-Type") contentType: String,
        @Body fileBody: RequestBody
    ): Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://backblaze-b-2-express-integration--sakinayesmin220.replit.app/"

        fun create(): BackblazeB2Service {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(BackblazeB2Service::class.java)
        }
    }
}

data class PresignedUrlRequest(
    val filename: String,
    val contentType: String
)

data class PresignedUrlResponse(
    val success: Boolean? = null,
    val uploadUrl: String? = null,
    val presignedUrl: String? = null,
    val url: String? = null,
    val fileName: String? = null,
    val filename: String? = null,
    val key: String? = null
)
