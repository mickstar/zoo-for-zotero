package com.mickstarify.zooforzotero.LibraryActivity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.analytics.FirebaseAnalytics
import com.mickstarify.zooforzotero.PreferenceManager
import com.mickstarify.zooforzotero.SortMethod
import com.mickstarify.zooforzotero.SyncSetup.AuthenticationStorage
import com.mickstarify.zooforzotero.ZoteroAPI.*
import com.mickstarify.zooforzotero.ZoteroAPI.Database.GroupInfo
import com.mickstarify.zooforzotero.ZoteroAPI.Database.ZoteroDatabase
import com.mickstarify.zooforzotero.ZoteroAPI.Model.Collection
import com.mickstarify.zooforzotero.ZoteroAPI.Model.GroupPojo
import com.mickstarify.zooforzotero.ZoteroAPI.Model.Item
import com.mickstarify.zooforzotero.ZoteroAPI.Model.Note
import io.reactivex.MaybeObserver
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.onComplete
import java.io.File
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LibraryActivityModel(private val presenter: Contract.Presenter, val context: Context) :
    Contract.Model {

    // stores the current item being viewed by the user. (useful for refreshing the view)
    var selectedItem: Item? = null
    // just a flag to store whether we have shown the user a network error so that we don't
    // do it twice (from getCatalog & getItems
    private var shownNetworkError: Boolean = false
    var currentCollection: String = "unset"

    var loadingItems = false
    var loadingCollections = false

    private var itemsDownloadAttempt = 0
    private var collectionsDownloadAttempt = 0

    private var firebaseAnalytics: FirebaseAnalytics

    private lateinit var zoteroAPI: ZoteroAPI
    private val zoteroGroupDB = ZoteroGroupDB(context)
    private var zoteroDBPicker = ZoteroDBPicker(ZoteroDB(context), zoteroGroupDB)
    private val zoteroDatabase = ZoteroDatabase(context)
    private var groups: List<GroupInfo>? = null

    var isDisplayingItems = false

    val preferences = PreferenceManager(context)

    override fun refreshLibrary() {
        if (!usingGroup) {
            this.requestItems(useCaching = true)
            this.requestCollections(useCaching = true)
        } else {
            this.loadGroup(currentGroup!!)
        }
    }

    fun shouldIUpdateLibrary(): Boolean {
        if (!zoteroDBPicker.getZoteroDB().hasStorage()) {
            return true
        }
        val currentTimestamp = System.currentTimeMillis()
        val lastModified = zoteroDBPicker.getZoteroDB().getLastModifiedTimestamp()

        if (TimeUnit.MILLISECONDS.toHours(currentTimestamp - lastModified) >= 24) {
            return true
        }
        return false
    }

    val sortMethod = compareBy<Item> {
        when (preferences.getSortMethod()) {
            SortMethod.TITLE -> it.getTitle().toLowerCase(Locale.getDefault())
            SortMethod.DATE -> it.getSortableDateString()
            SortMethod.AUTHOR -> it.getAuthor().toLowerCase(Locale.getDefault())
            SortMethod.DATE_ADDED -> it.getSortableDateAddedString()
        }
    }.thenBy { it.getTitle().toLowerCase(Locale.getDefault()) }

    override fun isLoaded(): Boolean {
        return zoteroDBPicker.getZoteroDB().isPopulated()
    }

    private fun filterItems(items: List<Item>): List<Item> {
        val onlyNotes = preferences.getIsShowingOnlyNotes()
        val onlyPdfs = preferences.getIsShowingOnlyPdfs()

        var newItems = items
        if (onlyNotes) {
            newItems =
                newItems.filter { zoteroDBPicker.getZoteroDB().getNotes(it.ItemKey).isNotEmpty() }
        }
        if (onlyPdfs) {
            newItems = newItems.filter {
                getAttachments(it.ItemKey).fold(false, { acc, attachment ->
                    var result = acc
                    if (!result) {
                        result = attachment.data["contentType"] == "application/pdf"
                    }
                    result
                })
            }
        }
        return newItems
    }

    override fun getLibraryItems(): List<Item> {
        return filterItems(zoteroDBPicker.getZoteroDB().getDisplayableItems()).sortedWith(sortMethod)
    }

    override fun getItemsFromCollection(collectionName: String): List<Item> {
        val collectionKey = zoteroDBPicker.getZoteroDB().getCollectionId(collectionName)
        if (collectionKey != null) {
            val items = zoteroDBPicker.getZoteroDB().getItemsFromCollection(collectionKey)
            return filterItems(items).sortedWith(sortMethod)
        }
        throw (Exception("Error, could not find collection with name ${collectionName}"))
    }

    override fun getSubCollections(collectionName: String): List<Collection> {
        val collectionKey = zoteroDBPicker.getZoteroDB().getCollectionId(collectionName)
        if (collectionKey != null) {
            return zoteroDBPicker.getZoteroDB().getSubCollectionsFor(collectionKey)
        }
        throw (Exception("Error, could not find collection with name ${collectionName}"))
    }



    fun loadItemsLocally() {
        doAsync {
            zoteroDBPicker.getZoteroDB().loadItemsFromStorage()
            onComplete {
                finishGetItems()
            }
        }
    }

    private fun requestOnlyModifiedItems() {
        if (!zoteroDBPicker.getZoteroDB().hasStorage()) {
            Log.e(
                "zotero",
                "Error, attempting to load only changed items when there is no local items"
            )
            return
        }
        if (zoteroDBPicker.getZoteroDB().items == null) {
            Log.e("zotero", "Error, items cannot be null (in modified items request)")
            return
        }
        zoteroAPI.getItemsSinceModification(
            zoteroDBPicker.getZoteroDB().getLibraryVersion(),
            object : ZoteroAPIDownloadItemsListener {
                override fun onCachedComplete() {
                    //never happens
                }

                override fun onNetworkFailure() {
                    Log.e("zotero", "there was a network error downloading modifiedItems")
                }

                override fun onDownloadComplete(items: List<Item>, libraryVersion: Int) {
                    zoteroDBPicker.getZoteroDB().applyChangesToItems(items)
                    zoteroDBPicker.getZoteroDB().setItemsVersion(libraryVersion)
                    presenter.makeToastAlert("Updated ${items.size} Items")
                    finishGetItems()
                }

                override fun onProgressUpdate(progress: Int, total: Int) {
                    presenter.updateLibraryRefreshProgress(progress, total)
                }

            }
        )
    }

    override fun requestItems(useCaching: Boolean) {
        loadingItems = true
        itemsDownloadAttempt++

        if (useCaching && zoteroDBPicker.getZoteroDB().hasStorage()) {
            zoteroDBPicker.getZoteroDB().loadItemsFromStorage()
            if (zoteroDBPicker.getZoteroDB().items != null) {
                requestOnlyModifiedItems()
                return // we don't need to proceed.
            }
        }

        zoteroAPI.getItems(
            useCaching,
            zoteroDBPicker.getZoteroDB().getLibraryVersion(),
            object : ZoteroAPIDownloadItemsListener {
                override fun onDownloadComplete(items: List<Item>, libraryVersion: Int) {
                    zoteroDBPicker.getZoteroDB().writeDatabaseUpdatedTimestamp()
                    zoteroDBPicker.getZoteroDB().items = items
                    zoteroDBPicker.getZoteroDB().setItemsVersion(libraryVersion)
                    zoteroDBPicker.getZoteroDB().commitItemsToStorage()
                    finishGetItems()
                }

                override fun onCachedComplete() {
                    zoteroDBPicker.getZoteroDB().writeDatabaseUpdatedTimestamp()
                    try {
                        zoteroDBPicker.getZoteroDB().loadItemsFromStorage()
                        finishGetItems()
                    } catch (e: Exception) {
                        Log.d("zotero", "there was an error loading cached items copy.")
                        presenter.makeToastAlert("There was an error loading the cached copy of your library")
                        requestItems(useCaching = false)
                    }
                }

                override fun onNetworkFailure() {
                    if (itemsDownloadAttempt < 3) {
                        Log.d("zotero", "attempting another download of items")
                        requestItems(useCaching)
                    } else if (zoteroDBPicker.getZoteroDB().hasStorage()) {
                        Log.d("zotero", "no internet connection. Using cached copy")
                        presenter.makeToastAlert("No internet connection, using cached copy of library")
                        zoteroDBPicker.getZoteroDB().loadItemsFromStorage()
                    } else {
                        if (shownNetworkError == false) {
                            shownNetworkError = true
                            presenter.createErrorAlert(
                                "Network Error",
                                "There was a problem downloading your library from the Zotero API.\n" +
                                        "Please check your network connection and try again.",
                                { shownNetworkError = false }
                            )
                        }
                        zoteroDBPicker.getZoteroDB().items = LinkedList()
                    }
                    finishGetItems()
                }

                override fun onProgressUpdate(progress: Int, total: Int) {
                    Log.d("zotero", "updating items, got $progress of $total")
                    presenter.updateLibraryRefreshProgress(progress, total)
                }
            })
    }

    override fun loadCollectionsLocally() {
        doAsync {
            zoteroDBPicker.getZoteroDB().loadCollectionsFromStorage()
            onComplete {
                finishGetCollections()
            }
        }
    }

    override fun requestCollections(useCaching: Boolean) {
        loadingCollections = true
        collectionsDownloadAttempt++

        zoteroAPI.getCollections(
            useCaching,
            zoteroDBPicker.getZoteroDB().getLibraryVersion(),
            object : ZoteroAPIDownloadCollectionListener {
                override fun onProgressUpdate(progress: Int, total: Int) {
                    // do nothing, don't care.
                }

                override fun onCachedComplete() {
                    try {
                        zoteroDBPicker.getZoteroDB().loadCollectionsFromStorage()
                        finishGetCollections()
                    } catch (e: Exception) {
                        Log.d("zotero", "there was an error loading cached collections copy.")
                        requestCollections(useCaching = false)
                    }
                }


                override fun onDownloadComplete(collections: List<Collection>) {
                    zoteroDBPicker.getZoteroDB().collections = collections
                    zoteroDBPicker.getZoteroDB().commitCollectionsToStorage()
                    finishGetCollections()
                }

                override fun onNetworkFailure() {
                    if (collectionsDownloadAttempt < 3) {
                        Log.d("zotero", "attempting another download of collections")
                        requestCollections(useCaching)
                    } else if (zoteroDBPicker.getZoteroDB().hasStorage()) {
                        Log.d("zotero", "no internet connection. Using cached copy")
                        zoteroDBPicker.getZoteroDB().loadCollectionsFromStorage()
                    } else {
                        if (shownNetworkError == false) {
                            shownNetworkError = true
                            presenter.createErrorAlert(
                                "Network Error",
                                "There was a problem downloading your library from the Zotero API.\n" +
                                        "Please check your network connection and try again.",
                                { shownNetworkError = false }
                            )
                        }
                        val params = Bundle()
                        firebaseAnalytics.logEvent("error_loading_collections", params)

                        zoteroDBPicker.getZoteroDB().collections = LinkedList()
                    }
                    finishGetCollections()
                }
            })
    }

    private fun finishGetCollections() {
        if (zoteroDBPicker.getZoteroDB().collections != null) {
            loadingCollections = false
            if (!loadingItems) {
                finishLoading()
            }
        }
    }

    private fun finishGetItems() {
        if (zoteroDBPicker.getZoteroDB().items != null) {
            loadingItems = false
            if (!loadingCollections) {
                finishLoading()
            }
        }
    }

    private fun finishLoading() {
        presenter.hideLibraryLoadingAnimation()
        presenter.receiveCollections(getCollections())
        if (currentCollection == "unset") {
            presenter.setCollection("all")
        } else {
            presenter.redisplayItems()
        }
    }

    override fun getCollections(): List<Collection> {
        return zoteroDBPicker.getZoteroDB().collections ?: LinkedList()
    }

    override fun getAttachments(itemKey: String): List<Item> {
        return zoteroDBPicker.getZoteroDB().getAttachments(itemKey)
    }

    override fun getNotes(itemKey: String): List<Note> {
        return zoteroDBPicker.getZoteroDB().getNotes(itemKey)
    }

    override fun filterCollections(query: String): List<Collection> {
        val queryUpper = query.toUpperCase(Locale.getDefault())
        return zoteroDBPicker.getZoteroDB().collections?.filter {
            it.getName().toUpperCase(Locale.getDefault()).contains(queryUpper)
        } ?: LinkedList()
    }

    override fun openPDF(attachment: File) {
        var intent = Intent(Intent.ACTION_VIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val uri: Uri?
            try {
                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    attachment
                )
            } catch (exception: IllegalArgumentException) {
                val params = Bundle()
                params.putString("filename", attachment.name)
                firebaseAnalytics.logEvent("error_opening_pdf", params)
                presenter.makeToastAlert("Sorry an error occurred while trying to download your attachment.")
                return
            }
            Log.d("zotero", "${uri.query}")
            intent = Intent(Intent.ACTION_VIEW)
            intent.data = uri
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        } else {
            intent.setDataAndType(Uri.fromFile(attachment), "application/pdf")
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent = Intent.createChooser(intent, "Open File")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        }
        context.startActivity(intent)
    }

    override fun filterItems(query: String): List<Item> {
        val items = zoteroDBPicker.getZoteroDB().items?.filter {
            it.query(query)

        } ?: LinkedList()

        items.sortedWith(sortMethod)

        return items
    }

    var isDownloading: Boolean = false
    var task: Future<Unit>? = null
    override fun downloadAttachment(item: Item) {
        if (isDownloading) {
            Log.d("zotero", "not downloading ${item.getTitle()} because i am already downloading.")
            return
        }
        isDownloading = true

        zoteroAPI.downloadItem(context,
            item,
            object : ZoteroAPIDownloadAttachmentListener {
                override fun receiveTask(task: Future<Unit>) {
                    this@LibraryActivityModel.task = task
                    // we have to check if the user has stopped the download before this task has come back.
                    if (isDownloading == false) {
                        cancelAttachmentDownload()
                    }
                }

                override fun onNetworkFailure() {
                    presenter.attachmentDownloadError()
                    isDownloading = false
                }

                override fun onComplete(attachment: File) {
                    isDownloading = false
                    if (attachment.exists()) {
                        presenter.openPDF(attachment)
                    } else {
                        presenter.attachmentDownloadError()
                    }
                }

                override fun onFailure(message: String) {
                    presenter.attachmentDownloadError(message)
                    isDownloading = false
                }

                override fun onProgressUpdate(progress: Long, total: Long) {
                    if (task?.isCancelled != true) {
                        Log.d("zotero", "Downloading attachment. got $progress of $total")
                        presenter.updateAttachmentDownloadProgress(progress, total)
                    }
                }
            })
    }

    override fun getUnfiledItems(): List<Item> {
        if (!zoteroDBPicker.getZoteroDB().isPopulated()) {
            Log.e("zotero", "error zoteroDB not populated!")
            return LinkedList()
        }
        return filterItems(zoteroDBPicker.getZoteroDB().getItemsWithoutCollection()).sortedWith(
            sortMethod
        )
    }

    override fun cancelAttachmentDownload() {
        this.isDownloading = false
        task?.cancel(true)
    }

    override fun createNote(note: Note) {
        zoteroAPI.uploadNote(note)
    }

    override fun modifyNote(note: Note) {
        zoteroAPI.modifyNote(note, zoteroDBPicker.getZoteroDB().getLibraryVersion())
    }

    override fun deleteNote(note: Note) {
        zoteroAPI.deleteItem(note.key, note.version, object : DeleteItemListener {
            override fun success() {
                presenter.makeToastAlert("Successfully deleted your note.")
                zoteroDBPicker.getZoteroDB().deleteItem(note.key)
                presenter.refreshItemView()
            }

            override fun failedItemLocked() {
                presenter.createErrorAlert("Error Deleting Note", "The item is locked " +
                        "and you do not have permission to delete this note.", {})
            }

            override fun failedItemChangedSince() {
                presenter.createErrorAlert("Error Deleting Note",
                    "Your local copy of this note is out of date. " +
                            "Please refresh your library to delete this note.",
                    {})
            }

            override fun failed(code: Int) {
                presenter.createErrorAlert("Error Deleting Note", "There was an error " +
                        "deleting your note. The server responded : ${code}", {})
            }
        })
    }

    override fun deleteAttachment(item: Item) {
        zoteroAPI.deleteItem(item.ItemKey, item.version, object : DeleteItemListener {
            override fun success() {
                presenter.makeToastAlert("Successfully deleted your attachment.")
                zoteroDBPicker.getZoteroDB().deleteItem(item.ItemKey)
                presenter.refreshItemView()
            }

            override fun failedItemLocked() {
                presenter.createErrorAlert("Error Deleting Attachment", "The item is locked " +
                        "and you do not have permission to delete this attachment.", {})
            }

            override fun failedItemChangedSince() {
                presenter.createErrorAlert("Error Deleting Attachment",
                    "Your local copy of this note is out of date. " +
                            "Please refresh your library to delete this attachment.",
                    {})
            }

            override fun failed(code: Int) {
                presenter.createErrorAlert("Error Deleting Attachment", "There was an error " +
                        "deleting your attachment. The server responded : ${code}", {})
            }
        })
    }

    override fun updateAttachment(item: Item, attachment: File) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun uploadAttachment(parent: Item, attachment: File) {
        zoteroAPI.uploadPDF(parent, attachment)
    }

    fun displayGroupsOnUI() {
        val groups = zoteroDatabase.getGroups()
        groups.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .unsubscribeOn(Schedulers.io())
            .subscribe(object : MaybeObserver<List<GroupInfo>> {
                override fun onSuccess(groupInfo: List<GroupInfo>) {
                    Log.d("zotero", "complted")
                    this@LibraryActivityModel.groups = groupInfo
                    presenter.displayGroupsOnActionBar(groupInfo)
                }

                override fun onComplete() {
                    Log.d("zotero", "User has no groups.")
                }

                override fun onSubscribe(d: Disposable) {
                }

                override fun onError(e: Throwable) {
                    Log.e("zotero", "error loading groups.")
                    presenter.createErrorAlert(
                        "Error loading group data",
                        "Message: ${e.message}",
                        {})
                }

            })
    }

    fun loadGroups() {
        val groupsObserver = zoteroAPI.getGroupInfo()
        groupsObserver.subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .subscribe(object : Observer<List<GroupPojo>> {
                override fun onComplete() {
                    Log.d("zotero", "completed getting group info")
                    displayGroupsOnUI()
                }

                override fun onSubscribe(d: Disposable) {
                    Log.d("zotero", "subscribed to group info")
                }

                override fun onNext(groupInfoPOJO: List<GroupPojo>) {
                    val groupInfo = groupInfoPOJO.map { groupPojo ->
                        GroupInfo(
                            id = groupPojo.groupData.id,
                            version = groupPojo.version,
                            name = groupPojo.groupData.name,
                            description = groupPojo.groupData.description,
                            fileEditing = groupPojo.groupData.fileEditing,
                            libraryEditing = groupPojo.groupData.libraryEditing,
                            libraryReading = groupPojo.groupData.libraryReading,
                            owner = groupPojo.groupData.owner,
                            type = groupPojo.groupData.type,
                            url = groupPojo.groupData.url
                        )
                    }

                    groupInfo.forEach { groupInfo ->
                        val status = zoteroDatabase.addGroup(groupInfo)
                        status.blockingAwait()
                    }
                }

                override fun onError(e: Throwable) {
                }

            })
    }

    override fun loadGroup(group: GroupInfo) {
        loadingItems = true
        loadingCollections = true

        /* This method beings the network calls to download all items related to the user group. */
        val db = zoteroGroupDB.getGroup(group.id)

        val modifiedSince = if (db.hasStorage()) {
            db.getLibraryVersion() as Int
        } else {
            -1
        }
        val useCaching = modifiedSince != -1 // largely useless. a relic from before.

        val groupItemObservable = zoteroAPI.getItemsFromGroup(group.id, useCaching, modifiedSince)
        groupItemObservable.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .unsubscribeOn(Schedulers.io())
            .subscribe(object : Observer<ZoteroAPIItemsResponse> {
                val items = LinkedList<Item>()
                var libraryVersion = -1
                override fun onComplete() {
                    Log.d(
                        "zotero",
                        "got ${this.items.size} many items in group. lv=$libraryVersion"
                    )
                    db.items = items
                    db.setItemsVersion(libraryVersion)
                    db.commitItemsToStorage()
                    if (db.collections != null) {
                        finishLoadingGroups(group)
                    }
                }

                override fun onSubscribe(d: Disposable) {
                    presenter.showLibraryLoadingAnimation()
                }

                override fun onNext(response: ZoteroAPIItemsResponse) {
                    assert(response.isCached == false)
                    this.libraryVersion = response.LastModifiedVersion
                    Log.d("zotero", "lv=${response.LastModifiedVersion}")
                    this.items.addAll(response.items)
                    presenter.updateLibraryRefreshProgress(this.items.size, response.totalResults)
                    Log.d("zotero", "got ${items.size} of ${response.totalResults} items")
                }

                override fun onError(e: Throwable) {
                    if (e is UpToDateException) {
                        db.loadItemsFromStorage()
                        if (db.collections != null) {
                            finishLoadingGroups(group)
                        }
                    } else {
                        Log.e("zotero", "${e}")
                        Log.e("zotero", "${e.stackTrace}")
                    }
                }
            })

        val groupCollectionsObservable =
            zoteroAPI.getCollectionsFromGroup(group.id, modifiedSince, useCaching)
        groupCollectionsObservable.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .unsubscribeOn(Schedulers.io())
            .subscribe(object : Observer<List<Collection>> {
                val collections = LinkedList<Collection>()
                override fun onComplete() {
                    db.collections = collections
                    db.commitCollectionsToStorage()
                    if (db.items != null) {
                        finishLoadingGroups(group)
                    }
                }

                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(collections: List<Collection>) {
                    this.collections.addAll(collections)
                }

                override fun onError(e: Throwable) {
                    if (e is UpToDateException) {
                        db.loadCollectionsFromStorage()
                        if (db.items != null) {
                            finishLoadingGroups(group)
                        }
                    } else {
                        presenter.createErrorAlert(
                            "Error downloading Group Collections",
                            "Message: ${e}",
                            {})

                        if (db.hasStorage()) {
                            db.loadCollectionsFromStorage()
                            if (db.items != null) {
                                finishLoadingGroups(group)
                            }
                        }
                    }
                }
            })
    }

    var usingGroup: Boolean = false
    var currentGroup: GroupInfo? = null

    private fun finishLoadingGroups(group: GroupInfo) {
        loadingItems = false
        loadingCollections = false
        presenter.hideLibraryLoadingAnimation()
        usingGroup = true
        currentGroup = group
        zoteroDBPicker.groupId = group.id
        this.currentCollection = "group_all"
        presenter.redisplayItems()
    }

    override fun usePersonalLibrary() {
        usingGroup = false
        currentGroup = null
        this.zoteroDBPicker.stopGroup()
    }

    init {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        val auth = AuthenticationStorage(context)
        if (auth.hasCredentials()) {
            zoteroAPI = ZoteroAPI(
                auth.getUserKey()!!,
                auth.getUserID()!!,
                auth.getUsername()!!
            )
        } else {
            presenter.createErrorAlert(
                "Error with stored API",
                "The API Key we have stored in the application is invalid!" +
                        "Please re-authenticate the application"
            ) { (context as Activity).finish() }
            auth.destroyCredentials()
            zoteroDBPicker.getZoteroDB().clearItemsVersion()
        }
    }
}