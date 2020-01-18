package com.mickstarify.zooforzotero.SyncSetup.ZoteroAPISetup

import android.content.Context
import android.net.Uri
import com.mickstarify.zooforzotero.SyncSetup.AuthenticationStorage

class ZoteroAPISetupPresenter (val view : Contract.View, context: Context): Contract.Presenter {
    override fun openLibraryView() {
        view.openLibraryView()
    }

    override fun handleOAuthCallback(uri: Uri?) {
            val oauth_token = uri?.getQueryParameter("oauth_token")
            val oauth_verifier = uri?.getQueryParameter("oauth_verifier")

            if (oauth_token != null && oauth_verifier != null) {
                model.handleOAuthCallback(oauth_token, oauth_verifier, authenticationStorage)
            }
            else{
                view.makeErrorAlert("Authorization Error", "There was an error receiving authorization from the Zotero Server")
            }
    }

    override fun loadAuthorizationURL(authorizationURL: String) {
        view.loadURL(authorizationURL)
    }

    private val model = ZoteroAPISetupModel(this, context)
    val authenticationStorage = AuthenticationStorage(context)
    init {
        view.startLoadingAnimation()
        model.establishAPIConnection()
    }
}