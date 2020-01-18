package com.mickstarify.zooforzotero.SyncSetup.ZoteroAPISetup

import android.app.Activity
import android.content.Context
import android.util.Log
import com.mickstarify.zooforzotero.BuildConfig
import com.mickstarify.zooforzotero.SyncSetup.AuthenticationStorage
import com.mickstarify.zooforzotero.ZooForZoteroApplication
import com.mickstarify.zooforzotero.ZoteroAPI.ZoteroAPI
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.onComplete
import javax.inject.Inject

class ZoteroAPISetupModel(val presenter: Contract.Presenter, val context: Context) :
    Contract.Model {
    private val API_URL = "https://api.zotero.org"
    private val REQUEST_TOKEN_ENDPOINT = "https://www.zotero.org/oauth/request?" +
            "library_access=1&" +
            "notes_access=1&" +
            "write_access=1&" +
            "all_groups=write"

    private val ACCESS_TOKEN_ENDPOINT = "https://www.zotero.org/oauth/access?" +
            "library_access=1&" +
            "notes_access=1&" +
            "write_access=1&" +
            "all_groups=write"

    private val ZOTERO_AUTHORIZE_ENDPOINT = "https://www.zotero.org/oauth/authorize?" +
            "library_access=1&" +
            "notes_access=1&" +
            "write_access=1&" +
            "all_groups=write"

    // You can generate these two keys on the Zotero API website.
    // Put them in a file called apikeys.properties
    /*  PROJECT_ROOT/apikeys.properties
        zotero_api_key="insert_key"
        zotero_api_secret="insert_key"
    *
    * */
    private val client_key = BuildConfig.zotero_api_key
    private val client_secret = BuildConfig.zotero_api_secret

    val OAuthProvider = CommonsHttpOAuthProvider(
        REQUEST_TOKEN_ENDPOINT,
        ACCESS_TOKEN_ENDPOINT,
        ZOTERO_AUTHORIZE_ENDPOINT
    )

    val OAuthConsumer = CommonsHttpOAuthConsumer(
        client_key,
        client_secret
    )

    @Inject
    lateinit var attachmentStorageManager: AttachmentStorageManager

    override fun establishAPIConnection() {
        doAsync {
            val requestURL =
                OAuthProvider.retrieveRequestToken(OAuthConsumer, "zooforzotero://oauth_callback")

            onComplete {
                Log.d("zotero", "loading URL $requestURL")
                presenter.loadAuthorizationURL(requestURL)
            }
        }
    }

    override fun handleOAuthCallback(
        oauth_token: String,
        oauth_verifier: String,
        authenticationStorage: AuthenticationStorage
    ) {
        Completable.fromAction {
            OAuthProvider.retrieveAccessToken(OAuthConsumer, oauth_verifier)
        }.doOnComplete {
            val params = OAuthProvider.responseParameters
            Log.d("zotero", "userID ${params.getFirst("userID")}")
            val username = params.getFirst("username")
            val userID = params.getFirst("userID")
            val userkey = OAuthConsumer.token

            if (BuildConfig.DEBUG) {
                Log.d("zotero", "params ${params.toList()}")
                Log.d("zotero", "userkey ${OAuthConsumer.token}")
                Log.d("zotero", "user secret ${OAuthConsumer.tokenSecret}")
            }
            authenticationStorage.setCredentials(username, userID, userkey)
            testConnection(userkey, userID, username, authenticationStorage)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe()

    }

    fun testConnection(
        apiKey: String,
        userID: String,
        username: String,
        authenticationStorage: AuthenticationStorage
    ) {
        val zoteroAPI = ZoteroAPI(apiKey, userID, username, attachmentStorageManager)
        zoteroAPI.getKeyInfo().subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(Consumer {
                authenticationStorage.setLibraryAccess(it.userAccess.access.libraryAccess)
                authenticationStorage.setFilesAccess(it.userAccess.access.fileAccess)
                authenticationStorage.setNotesAccess(it.userAccess.access.notesAccess)
                authenticationStorage.setWriteAccess(it.userAccess.access.write)
                presenter.openLibraryView()
            })
    }

    init {
        ((context as Activity).application as ZooForZoteroApplication).component.inject(this)
    }
}
