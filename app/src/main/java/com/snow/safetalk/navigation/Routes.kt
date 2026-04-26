package com.snow.safetalk.navigation

object Routes {
    const val SPLASH       = "splash"
    const val HOME         = "home"
    const val SCAN         = "scan"
    const val SOURCES      = "sources"
    const val SETTINGS     = "settings"
    const val SECURITY     = "security"
    const val SECURITY_ROUTE = "security?focus={focus}"
    const val SUBSCRIPTION = "subscription"
    const val ABOUT        = "about"
    const val RESULT       = "result"
    const val HISTORY      = "history"
    const val NOTIFICATIONS = "notifications"
    const val STATISTICS   = "statistics"
    const val ONBOARDING   = "onboarding"
    const val ONBOARDING_LEGAL = "onboarding_legal"
    const val PRIVACY_POLICY = "privacy_policy"
    const val TERMS_OF_SERVICE = "terms_of_service"
    const val PERMISSION_DISCLOSURE = "permission_disclosure"
    fun security(focus: String) = "security?focus=$focus"
}
