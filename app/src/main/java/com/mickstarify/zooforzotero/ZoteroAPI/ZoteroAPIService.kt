package com.mickstarify.zooforzotero.ZoteroAPI

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mickstarify.zooforzotero.ZoteroAPI.Model.CollectionPOJO
import com.mickstarify.zooforzotero.ZoteroAPI.Model.GroupPojo
import com.mickstarify.zooforzotero.ZoteroAPI.Model.KeyInfo
import io.reactivex.Observable
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface ZoteroAPIService {
    @GET("users/{user}/items")
    fun getItems(
        @Path("user") user: String,
        @Query("start") index: Int
    ): Observable<Response<ResponseBody>>

    /* Gets items since last update. */
    @GET("users/{user}/items")
    fun getItemsSince(
        @Path("user") user: String,
        @Query("since") modificationSinceVersion: Int,
        @Query("start") index: Int
    ): Observable<Response<ResponseBody>>


    @GET("users/{user}/groups")
    fun getGroupInfo(
        @Path("user") userID: String
    ): Observable<List<GroupPojo>>

    // get items for the group ID
    @GET("groups/{groupID}/items")
    fun getItemsForGroup(
        @Path("groupID") groupID: Int,
        @Query("start") index: Int
    ): Observable<Response<ResponseBody>>

    // get items for the group ID
    @GET("groups/{groupID}/items")
    fun getItemsForGroupSince(
        @Path("groupID") groupID: Int,
        @Query("since") modificationSinceVersion: Int,
        @Query("start") index: Int
    ): Observable<Response<ResponseBody>>

    @GET("groups/{groupID}/collections")
    fun getCollectionsForGroup(
        @Path("groupID") groupID: Int,
        @Query("start") index: Int
    ): Observable<Response<List<CollectionPOJO>>>

    @Streaming
    @GET("groups/{groupID}/items/{itemKey}/file")
    fun getAttachmentFileFromGroup(
        @Path("groupID") user: Int,
        @Path("itemKey") itemKey: String
    ): Call<ResponseBody>

    @GET("keys/{key}")
    fun getKeyInfo(
        @Path("key") key: String
    ): Observable<Response<KeyInfo>>

    @GET("users/{user}/collections")
    fun getCollections(
        @Path("user") user: String,
        @Query("start") index: Int
    ): Observable<Response<List<CollectionPOJO>>>

    @Streaming
    @GET("users/{user}/items/{itemKey}/file")
    fun getItemFile(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String
    ): Call<ResponseBody>

    @Streaming
    @GET("users/{user}/items/{itemKey}/file")
    fun getItemFileRx(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String
    ): Observable<Response<ResponseBody>>

    @POST("users/{user}/items")
    fun writeItem(
        @Path("user") user: String,
        @Body json: JsonArray
    ): Call<ResponseBody>

    @POST("users/{user}/items")
    fun uploadNote(
        @Path("user") user: String,
        @Body json: JsonArray
    ): Observable<Response<ResponseBody>>

    @PATCH("users/{user}/items/{itemKey}")
    fun editNote(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String,
        @Body json: JsonObject
    ): Observable<Response<ResponseBody>>

    @PATCH("users/{user}/items/{itemKey}")
    fun patchItem(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String,
        @Body json: JsonObject
    ): Observable<Response<ResponseBody>>

    @DELETE("users/{user}/items/{itemKey}")
    fun deleteItem(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String
    ): Call<ResponseBody>

    @POST("users/{user}/items/{itemKey}/file")
    fun uploadAttachmentAuthorization(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String,
        @Query("md5") md5: String,
        @Query("filename") filename: String,
        @Query("filesize") filesize: Long,
        @Query("mtime") mtime: Long,
        @Query("params") params: Int = 1,
        @Body bodyText: String
    ): Observable<Response<ResponseBody>>

    @POST("users/{user}/items/{itemKey}/file")
    fun registerUpload(
        @Path("user") user: String,
        @Path("itemKey") itemKey: String,
        @Query("upload") uploadKey: String,
        @Body body: String
    ): Observable<Response<ResponseBody>>

    @POST
    fun uploadAttachmentToAmazon(
        @Url url: String,
        @Body data: RequestBody
    ): Observable<Response<ResponseBody>>

    @Multipart
    @POST
    fun uploadAttachmentToAmazonMulti(
        @Url url: String,
        @Part("key") key: RequestBody,
        @Part("acl") acl: RequestBody,
        @Part("Content-MD5") content_MD5: RequestBody,
        @Part("success_action_status") success_action_status: RequestBody,
        @Part("policy") policy: RequestBody,
        @Part("x-amz-algorithm") x_amz_algorithm: RequestBody,
        @Part("x-amz-credential") x_amz_credential: RequestBody,
        @Part("x-amz-date") x_amz_date: RequestBody,
        @Part("x-amz-signature") x_amz_signature: RequestBody,
        @Part("x-amz-security-token") x_amz_security_token: RequestBody,
        @Part("file") attachmentData: RequestBody
    ): Observable<Response<ResponseBody>>
}