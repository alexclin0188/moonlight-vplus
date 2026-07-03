package com.alexclin.moonlink.android.home

import android.content.Intent
import android.widget.RemoteViewsService

import com.alexclin.moonlink.android.home.GameListRemoteViewsFactory

class GameListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return GameListRemoteViewsFactory(applicationContext, intent)
    }
}
