package com.alexclin.moonlink.android.home

import android.content.Intent
import android.widget.RemoteViewsService

import com.limelight.widget.GameListRemoteViewsFactory

class GameListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return GameListRemoteViewsFactory(applicationContext, intent)
    }
}
