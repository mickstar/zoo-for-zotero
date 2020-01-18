package com.mickstarify.zooforzotero.SyncSetup

import android.content.Context

class SyncSetupPresenter(private val view: SyncSetupContract.View, context: Context) :
    SyncSetupContract.Presenter {
    override fun createNetworkError(message: String) {
        view.createAlertDialog("Network Error", message)
    }

    override fun hasSyncSetup(): Boolean {
        return model.hasSyncSetup()
    }

    override fun startZoteroAPISetup() {
        view.startZoteroAPIActivity()
    }

    override fun acceptedTerms() {
        model.preferenceManager.setAcceptedTerms(true)
    }

    override fun selectSyncSetup(option: SyncOption) {
        if (!model.preferenceManager.hasAcceptedTerms()){
            // user must accept this before we can proceed.
            view.displayDisclaimer()
            return
        }

        when(option){
            SyncOption.ZoteroAPI -> {
                view.showHowToZoteroSyncDialog({
                    model.setupZoteroAPI()
                })
            }
            SyncOption.ZoteroAPIManual -> {
                view.createAPIKeyDialog({ apiKey: String ->
                    model.testAPIKey(apiKey)
                })
            }
            else -> view.createUnsupportedAlert()
        }
    }

    private val model = SyncSetupModel(this, context)


    init{
        view.initUI()
        if (model.preferenceManager.hasAcceptedTerms() == false){
            view.displayDisclaimer()
        }
    }
}