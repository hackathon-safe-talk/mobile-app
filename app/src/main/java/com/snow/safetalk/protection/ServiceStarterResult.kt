package com.snow.safetalk.protection

sealed class ServiceStarterResult {
    object Started : ServiceStarterResult()
    object AlreadyRunning : ServiceStarterResult()
    data class BlockedBySystem(val reason: String) : ServiceStarterResult()
    data class FailedUnexpectedly(val error: Throwable) : ServiceStarterResult()
}
