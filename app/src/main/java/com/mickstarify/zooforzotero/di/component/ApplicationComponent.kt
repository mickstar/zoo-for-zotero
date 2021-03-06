package com.mickstarify.zooforzotero.di.component

import android.content.Context
import com.mickstarify.zooforzotero.AttachmentManager.AttachmentManagerModel
import com.mickstarify.zooforzotero.LibraryActivity.Fragments.LibraryListFragment
import com.mickstarify.zooforzotero.LibraryActivity.LibraryActivityModel
import com.mickstarify.zooforzotero.SettingsActivity
import com.mickstarify.zooforzotero.ZoteroAPI.Syncing.SyncManager
import com.mickstarify.zooforzotero.ZoteroStorage.AttachmentStorageManager
import com.mickstarify.zooforzotero.ZoteroStorage.ZoteroDB.ZoteroDB
import com.mickstarify.zooforzotero.di.module.ApplicationModule
import com.mickstarify.zooforzotero.di.module.DatabaseModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(ApplicationModule::class, DatabaseModule::class))
interface ApplicationComponent {
    val context: Context
    fun inject(libraryActivityModel: LibraryActivityModel)
    fun inject(settingsActivity: SettingsActivity)
    fun inject(attachmentManagerModel: AttachmentManagerModel)
    fun inject(attachmentStorageManager: AttachmentStorageManager)
    fun inject(syncManager: SyncManager)
    fun inject(zoteroDB: ZoteroDB)
    fun inject(libraryListFragment: LibraryListFragment)
}