package icu.guestliang.nfcworkflow

import icu.guestliang.nfcworkflow.logging.AppLogger
import android.app.Application

class NFCWorkFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.info(this, "NFCWorkFlowApp created", "App")
    }
}
