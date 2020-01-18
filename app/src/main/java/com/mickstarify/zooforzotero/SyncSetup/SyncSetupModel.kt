package com.mickstarify.zooforzotero.SyncSetup


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mickstarify.zooforzotero.BuildConfig
import com.mickstarify.zooforzotero.LibraryActivity.LibraryActivity
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.ZooForZoteroApplication
import com.mickstarify.zooforzotero.ZoteroAPI.BASE_URL
import com.mickstarify.zooforzotero.ZoteroAPI.Model.KeyInfo
import com.mickstarify.zooforzotero.ZoteroAPI.ZoteroAPIService
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

class SyncSetupModel(val presenter: SyncSetupPresenter, val context: Context) :
    SyncSetupContract.Model {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun testAPIKey(apiKey: String) {
        if (apiKey.trim() == "") {
            return
        }
        val httpClient = OkHttpClient().newBuilder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    this.level = HttpLoggingInterceptor.Level.BODY
                })
            }
        }.build()

        val authenticationStorage = AuthenticationStorage(context)

        val service = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build().create(ZoteroAPIService::class.java)

        val call = service.getKeyInfo(apiKey).map { response ->
            if (response.code() == 200) {
                response.body()!!
            } else {
                throw(Exception("Got back server code ${response.code()}"))
            }
        }
        call.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<KeyInfo> {
                override fun onComplete() {
                    Log.d("zotero","got access level library: ${authenticationStorage.getLibraryAccess()}")
                    Log.d("zotero","got files level library: ${authenticationStorage.getFilesAccess()}")
                    Log.d("zotero","got notes level library: ${authenticationStorage.getNotesAccess()}")
                    Log.d("zotero","got write level library: ${authenticationStorage.getWriteAccess()}")
                    openLibrary()
                }

                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(keyInfo: KeyInfo) {
                    Log.d("zotero", "got keyinfo")
                    authenticationStorage.setCredentials(
                        keyInfo.username,
                        keyInfo.userID.toString(),
                        keyInfo.key
                    )
                    authenticationStorage.setLibraryAccess(keyInfo.userAccess.access.libraryAccess)
                    authenticationStorage.setFilesAccess(keyInfo.userAccess.access.fileAccess)
                    authenticationStorage.setNotesAccess(keyInfo.userAccess.access.notesAccess)
                    authenticationStorage.setWriteAccess(keyInfo.userAccess.access.write)
                }

                override fun onError(e: Throwable) {
                    presenter.createNetworkError("There was a network error Connecting to the Zotero API.")
                }
            })
    }

    //This function will be expanded when more apis are setup.
    override fun hasSyncSetup(): Boolean {
        val creds = AuthenticationStorage(context)
        return creds.hasCredentials()
    }

    override fun setupZoteroAPI() {
        presenter.startZoteroAPISetup()
    }

    fun openLibrary() {
        val intent = Intent(context, LibraryActivity::class.java)
        context.startActivity(intent)
        (context as SyncSetupView).finish()
    }

    init {
        ((context as Activity).application as ZooForZoteroApplication).component.inject(this)
    }
}