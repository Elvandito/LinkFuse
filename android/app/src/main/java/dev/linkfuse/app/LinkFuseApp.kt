package dev.linkfuse.app

import android.app.Application
import dev.linkfuse.app.service.NotificationHub

class LinkFuseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the channel early so the service can promote itself within the deadline.
        NotificationHub.createChannel(this)
    }
}
