package com.mickstarify.zooforzotero.ZoteroAPI.Model

import com.google.gson.annotations.SerializedName

data class KeyInfo(
    @SerializedName("key")
    val key: String,
    @SerializedName("userID")
    val userID: Int,
    @SerializedName("username")
    val username: String,
    @SerializedName("access") val userAccess: UserAccess
)

data class UserAccess(
    @SerializedName("user") val access: Access
)

data class Access(
    @SerializedName("library") val libraryAccess: Boolean,
    @SerializedName("files") val fileAccess: Boolean,
    @SerializedName("notes") val notesAccess: Boolean = false,
    @SerializedName("write") val write: Boolean = false
)