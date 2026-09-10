package com.quark.agent.backend

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Subset of the opencode Serve OpenAPI used by Quark V1.
// Full spec lives at http://<host>:4096/doc when the server runs.
interface OpenCodeService {

    @GET("global/health")
    suspend fun health(): Health

    @GET("session")
    suspend fun sessions(): List<SessionInfo>

    @POST("session")
    suspend fun createSession(@Body body: CreateSessionBody = CreateSessionBody()): SessionInfo

    @DELETE("session/{id}")
    suspend fun deleteSession(@Path("id") id: String): Boolean

    @POST("session/{id}/abort")
    suspend fun abortSession(@Path("id") id: String): Boolean

    @GET("session/{id}/message")
    suspend fun messages(@Path("id") id: String): List<MessageWithParts>

    @POST("session/{id}/message")
    suspend fun sendMessage(
        @Path("id") id: String,
        @Body body: SendMessageBody
    ): MessageWithParts

    @POST("session/{id}/prompt_async")
    suspend fun sendMessageAsync(
        @Path("id") id: String,
        @Body body: SendMessageBody
    ): Response<Unit>

    @GET("session/status")
    suspend fun statuses(): Map<String, SessionStatus>

    @GET("session/{id}/todo")
    suspend fun todos(@Path("id") id: String): List<ServerTodo>

    @POST("session/{id}/permissions/{permissionId}")
    suspend fun respondPermission(
        @Path("id") id: String,
        @Path("permissionId") permissionId: String,
        @Body body: PermissionResponse
    ): Boolean

    @GET("config/providers")
    suspend fun providers(): ProvidersResponse

    @GET("mcp")
    suspend fun mcpStatus(): Map<String, McpStatus>

    @POST("mcp")
    suspend fun addMcp(@Body body: McpAddBody): McpStatus
}
