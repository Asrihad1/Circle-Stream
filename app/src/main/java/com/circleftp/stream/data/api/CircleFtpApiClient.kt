package com.circleftp.stream.data.api

import android.util.Log
import com.circleftp.stream.data.model.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class CircleFtpApiClient(
    val baseUrl: String = "http://new.circleftp.net:5000/api"
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
                coerceInputValues = true
            })
        }
    }

    suspend fun getHomePage(): HomePageResponse {
        return try {
            httpClient.get("$baseUrl/home-page/getHomePagePosts").body()
        } catch (e: ContentConvertException) {
            Log.e("CircleFtpApiClient", "Deserialization error in getHomePage: ${e.message}", e)
            HomePageResponse()
        } catch (e: Exception) {
            Log.e("CircleFtpApiClient", "Failed to fetch home page: ${e.message}", e)
            HomePageResponse()
        }
    }

    suspend fun getCategories(): List<CategoryItem> {
        return try {
            httpClient.get("$baseUrl/categories").body()
        } catch (e: Exception) {
            Log.e("CircleFtpApiClient", "Failed to fetch categories: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getCategoryPosts(categoryId: Long, page: Int = 1, limit: Int = 50): List<PostSummary> {
        return try {
            val res: CategoryPostsResponse = httpClient.get("$baseUrl/posts") {
                parameter("categoryExact", categoryId)
                parameter("page", page)
                parameter("order", "desc")
                parameter("limit", limit)
            }.body()
            res.posts
        } catch (e: Exception) {
            Log.e("CircleFtpApiClient", "Failed to fetch category posts for $categoryId: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun searchPosts(query: String, page: Int = 1): List<PostSummary> {
        return try {
            val res: CategoryPostsResponse = httpClient.get("$baseUrl/posts") {
                parameter("searchTerm", query)
                parameter("page", page)
                parameter("order", "desc")
                parameter("limit", 50)
            }.body()
            res.posts
        } catch (e: Exception) {
            Log.e("CircleFtpApiClient", "Failed to search posts for '$query': ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getPostDetail(postId: Long): ResolvedPostDetail? {
        if (postId <= 0L) {
            Log.w("CircleFtpApiClient", "getPostDetail called with invalid postId: $postId")
            return null
        }
        return try {
            val raw: PostDetailRaw = httpClient.get("$baseUrl/posts/$postId").body()
            ContentParser.parse(raw)
        } catch (e: ContentConvertException) {
            // Catches non-JSON / HTML error pages gracefully
            Log.e("CircleFtpApiClient", "ContentConvertException for post $postId (server returned HTML/invalid format): ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e("CircleFtpApiClient", "Error retrieving post details for $postId: ${e.message}", e)
            null
        }
    }
}
