package com.mycompany.synapse

import android.app.Application
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.feedback.Feedback
import com.mycompany.synapse.reminder.Reminders

class SynapseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Feedback.init(this)
        Reminders.createChannel(this)
    }
}
