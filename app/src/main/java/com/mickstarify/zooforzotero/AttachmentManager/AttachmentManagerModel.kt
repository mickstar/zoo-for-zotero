package com.mickstarify.zooforzotero.AttachmentManager

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.common.util.concurrent.Service
import com.google.firebase.analytics.FirebaseAnalytics
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.SyncSetup.AuthenticationStorage
import com.mickstarify.zooforzotero.ZooForZoteroApplication
import com.mickstarify.zooforzotero.ZoteroAPI.DownloadProgress
import com.mickstarify.zooforzotero.ZoteroAPI.ZoteroAPI
import com.mickstarify.zooforzotero.ZoteroAPI.ZoteroAPIDownloadAttachmentListener
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import com.mickstarify.zooforzotero.ZoteroStorage.Database.AttachmentInfo
import com.mickstarify.zooforzotero.ZoteroStorage.Database.GroupInfo
import com.mickstarify.zooforzotero.ZoteroStorage.Database.Item
import com.mickstarify.zooforzotero.ZoteroStorage.Database.ZoteroDatabase
import com.mickstarify.zooforzotero.ZoteroStorage.ZoteroDB.ZoteroDB
import io.reactivex.Completable
import io.reactivex.CompletableObserver
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.internal.operators.observable.ObservableFromIterable
import io.reactivex.internal.operators.observable.ObservableJust
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.Future
import javax.inject.Inject

class AttachmentManagerModel(val presenter: Contract.Presenter, val context: Context) :
    Contract.Model {

    lateinit var zoteroAPI: ZoteroAPI
    lateinit var zoteroDB: ZoteroDB
    @Inject
    lateinit var zoteroDatabase: ZoteroDatabase
    @Inject
    lateinit var attachmentStorageManager: AttachmentStorageManager
    @Inject
    lateinit var preferenceManager: PreferenceManager

    override var isDownloading = false // useful for button state.

    init {
        ((context as Activity).application as ZooForZoteroApplication).component.inject(this)
        val auth = AuthenticationStorage(context)
        if (auth.hasCredentials()) {
            zoteroAPI = ZoteroAPI(
                auth.getUserKey(),
                auth.getUserID(),
                auth.getUsername(),
                attachmentStorageManager
            )
        } else {
            presenter.createErrorAlert(
                "No credentials Available",
                "There is no credentials available to connect " +
                        "to the zotero server. Please relogin to the app (clear app data if necessary)"
            ) { (context.finish()) }
        }

    }

    data class downloadAllProgress(
        var status: Boolean,
        var currentIndex: Int,
        var attachment: Item
    )

    override fun cancelDownload() {
        Log.d("zotero", "canceling download")
        isDownloading = false
        downloadDisposable?.dispose()
        presenter.finishLoadingAnimation()
    }

    var downloadDisposable: Disposable? = null

    override fun downloadAttachments() {
        if (isDownloading) {
            Log.e("zotero", "Error already downloading")
            return
        }
        isDownloading = true
        val toDownload = LinkedList<Item>()

        Completable.fromAction({
            for (attachment in zoteroDB.items!!.filter { it.itemType == "attachment" && it.data["linkMode"] != "linked_file" }) {
                val contentType = attachment.data["contentType"]
                if (contentType != "application/pdf" && contentType != "image/vnd.djvu") {
                    continue
                }
                if (!attachmentStorageManager.checkIfAttachmentExists(
                        attachment,
                        checkMd5 = false
                    )
                ) {
                    toDownload.add(attachment)
                }
            }
        }
        ).andThen(ObservableFromIterable(toDownload.withIndex()).map {
            val i = it.index
            val attachment = it.value
            var status = true
            zoteroAPI.downloadItemRx(attachment, zoteroDB.groupID, context)
                .blockingSubscribe(object : Observer<DownloadProgress> {
                    var setMetadata = false
                    override fun onComplete() {
                        // do nothing.
                    }

                    override fun onSubscribe(d: Disposable) {
                        // do nothing.
                    }

                    override fun onNext(it: DownloadProgress) {
                        if (setMetadata == false && it.metadataHash != "") {
                            val err = zoteroDatabase.writeAttachmentInfo(
                                AttachmentInfo(
                                    attachment.itemKey,
                                    zoteroDB.groupID,
                                    it.metadataHash,
                                    it.mtime,
                                    if (preferenceManager.isWebDAVEnabled()) {
                                        AttachmentInfo.WEBDAV
                                    } else {
                                        AttachmentInfo.ZOTEROAPI
                                    }
                                )
                            ).blockingGet()
                            err?.let { throw(err) }
                        }
                    }

                    override fun onError(e: Throwable) {
                        Log.d("zotero", "got error on api download, $e")
                        status = false
                    }

                }
                )
            downloadAllProgress(status, i, attachment)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()))
            .subscribe(object : Observer<downloadAllProgress> {
                override fun onComplete() {
                    showMetaInformation()
                    isDownloading = false
                    presenter.finishLoadingAnimation()
                    presenter.createErrorAlert(
                        "Finished Downloading",
                        "All your attachments have been downloaded.",
                        {})
                }

                override fun onSubscribe(d: Disposable) {
                    presenter.updateProgress("Loading", 0, toDownload.size)
                    downloadDisposable = d
                }

                override fun onNext(progress: downloadAllProgress) {
                    Log.d("zotero", "got progress, disposeState= ${downloadDisposable?.isDisposed}")
                    if (progress.status) {
                        localAttachmentSize += attachmentStorageManager.getFileSize(
                            progress.attachment
                        )
                        nLocalAttachments++
                        presenter.displayAttachmentInformation(
                            nLocalAttachments,
                            localAttachmentSize,
                            nRemoteAttachments
                        )

                        presenter.updateProgress(
                            progress.attachment.data["filename"] ?: "unknown",
                            progress.currentIndex,
                            toDownload.size
                        )
                    } else {
                        presenter.makeToastAlert("Error downloading ${progress.attachment.getTitle()}")
                        presenter.updateProgress("", progress.currentIndex, toDownload.size)
                    }
                }

                override fun onError(e: Throwable) {
                    presenter.createErrorAlert(
                        "Error downloading attachments",
                        "The following error occurred: ${e}",
                        {})
                }

            })
    }

    override fun loadLibrary() {
        zoteroDB = ZoteroDB(context, groupID = GroupInfo.NO_GROUP_ID)
        zoteroDB.loadItemsFromDatabase().subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : CompletableObserver {
                override fun onComplete() {
                    showMetaInformation()
                    presenter.finishLoadingAnimation()
                }

                override fun onSubscribe(d: Disposable) {
                    presenter.displayLoadingAnimation()
                }

                override fun onError(e: Throwable) {
                    FirebaseAnalytics.getInstance(context).logEvent(
                        "error_att_loading_items",
                        Bundle().apply { putString("error_message", e.toString()) })
                    presenter.createErrorAlert(
                        "Error loading items",
                        "There was an error loading your items. " +
                                "Please verify that the library has synced fully first. Message: ${e.message}",
                        { presenter.displayErrorState() }
                    )
                }

            })
    }


    var localAttachmentSize: Long = 0L
    var nLocalAttachments: Int = -1
    var nRemoteAttachments = -1
    private fun showMetaInformation() {
//        /*Calculates the attachment size on disk as well as on remote server.*/
        val attachmentKeys = zoteroDB.attachmentInfo!!.keys

        val localAttachments = LinkedList<Item>()
        val allAttachments = LinkedList<Item>()
        for (attachment in zoteroDB.items!!.filter { it.itemType == "attachment" }) {
            if ((attachment.data["contentType"] != "application/pdf" && attachment.data["contentType"] != "image/vnd.djvu") || attachment.data["linkMode"] == "linked_file") {
                continue
            }
            allAttachments.add(attachment)
            if (attachmentStorageManager.checkIfAttachmentExists(attachment, checkMd5 = false)) {
                localAttachments.add(attachment)
            }
        }

        nLocalAttachments = localAttachments.size
        nRemoteAttachments = allAttachments.size

        localAttachmentSize = localAttachments.fold(
            0L,
            { acc, item ->
                acc + 1L + attachmentStorageManager.getFileSize(
                    item
                )
            })

        presenter.displayAttachmentInformation(
            localAttachments.size,
            localAttachmentSize,
            allAttachments.size
        )
    }


}