package com.mickstarify.zooforzotero.SyncSetup.ZoteroAPISetup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.mickstarify.zooforzotero.LibraryActivity.LibraryActivity
import com.mickstarify.zooforzotero.R
import com.mickstarify.zooforzotero.SyncSetup.AuthenticationStorage
import kotlinx.android.synthetic.main.activity_zotero_api_setup.*


class ZoteroAPISetup : AppCompatActivity(), Contract.View {
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun openLibraryView() {
        val intent = Intent(this, LibraryActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun startLoadingAnimation() {
        val progressLayout = findViewById<ConstraintLayout>(R.id.constraintLayout_progressdialog)
        progressLayout.visibility = View.VISIBLE
    }

    override fun stopLoadingAnimation() {
        val progressLayout = findViewById<ConstraintLayout>(R.id.constraintLayout_progressdialog)
        progressLayout.visibility = View.GONE
    }

    override fun makeErrorAlert(title: String, message: String) {
        val alert = AlertDialog.Builder(this)
        alert.setIcon(R.drawable.ic_error_black_24dp)
        alert.setTitle(title)
        alert.setMessage(message)
        alert.setPositiveButton("Ok") { _, _ -> }
        alert.show()
    }

    override fun loadURL(url: String) {
        val webView = findViewById<WebView>(R.id.webview_zotero_API_setup)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                Log.d(packageName, "got url $url")
                if (URLUtil.isNetworkUrl(url)) {
                    Log.d(packageName, "is network url $url")
                    return false
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                stopLoadingAnimation()
            }
        }
        webView.loadUrl(url)
    }

    private lateinit var presenter : Contract.Presenter;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zotero_api_setup)
        setSupportActionBar(toolbar)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        presenter = ZoteroAPISetupPresenter(this, this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleZoteroOAuthIntent(intent)
    }

    private fun handleZoteroOAuthIntent(intent: Intent?) {
        if (intent != null){
            val uri = intent.data
            Log.d(this.packageName, "got intent $uri")
            Log.d(packageName, "token ${uri?.getQueryParameter("oauth_token")}")
            Log.d(packageName, "verifier ${uri?.getQueryParameter("oauth_verifier")}")
            presenter.handleOAuthCallback(uri)
        }
    }


}
