package com.mickstarify.zooforzotero.SyncSetup

import android.content.Context
import android.content.SharedPreferences

class AuthenticationStorage(context: Context) {
    private var sharedPreferences: SharedPreferences =
        context.getSharedPreferences("credentials", Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean {
        return (sharedPreferences.contains("userkey") && sharedPreferences.getString(
            "userkey",
            ""
        ) != "")
    }

    fun getUsername(): String {
        return sharedPreferences.getString("username", "error")!!
    }

    fun setCredentials(username: String, userID: String, userkey: String) {
        val editor = sharedPreferences.edit()
        editor.putString("username", username)
        editor.putString("userID", userID)
        editor.putString("userkey", userkey)
        editor.apply()
    }

    fun getUserKey(): String {
        return sharedPreferences.getString("userkey", "error")!!
    }

    fun getUserID(): String {
        return sharedPreferences.getString("userID", "error")!!
    }

    fun destroyCredentials() {
        val editor = sharedPreferences.edit()
        editor.remove("username")
        editor.remove("userID")
        editor.remove("userkey")
        editor.apply()
    }

    fun setLibraryAccess(libraryAccess: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("user_library_access", libraryAccess)
        editor.apply()
    }

    fun setFilesAccess(fileAccess: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("user_file_access", fileAccess)
        editor.apply()
    }

    fun setNotesAccess(notesAccess: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("user_notes_access", notesAccess)
        editor.apply()
    }

    fun setWriteAccess(writeAccess: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean("user_write_access", writeAccess)
        editor.apply()
    }

    fun getLibraryAccess(): Boolean {
        return sharedPreferences.getBoolean("user_library_access", true)
    }

    fun getFilesAccess(): Boolean {
        return sharedPreferences.getBoolean("user_file_access", true)
    }

    fun getNotesAccess(): Boolean {
        return sharedPreferences.getBoolean("user_notes_access", true)
    }
    fun getWriteAccess(): Boolean {
        return sharedPreferences.getBoolean("user_write_access", true)
    }

    fun hasAccess(): Boolean {
        return sharedPreferences.contains("user_library_access")
    }

}