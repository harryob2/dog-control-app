package com.hobengineering.ssdogapp

import android.app.Application
//import com.google.firebase.FirebaseApp
//import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import com.stripe.stripeterminal.TerminalApplicationDelegate


/**
 * Created by Ali Kabiri on 13.04.20.
 */
@HiltAndroidApp
class MainApplication: Application() {

    override fun onCreate() {
//        FirebaseApp.initializeApp(this)
//        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        TerminalApplicationDelegate.onCreate(this)
        super.onCreate()
    }
}