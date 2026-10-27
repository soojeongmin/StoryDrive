package com.soojeongmin.storydrive

import android.app.Application

class StoryDriveApp : Application() {
    val engine: StoryEngine by lazy { StoryEngine(this) }
}
