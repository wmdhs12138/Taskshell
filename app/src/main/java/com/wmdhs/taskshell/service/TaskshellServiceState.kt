package com.wmdhs.taskshell.service

object TaskshellServiceState {
    @Volatile
    var running: Boolean = false

    @Volatile
    var lastError: String? = null

    @Volatile
    var lastEvent: String = "Service has not been started"
}
