package com.qierong.dlnascreencastdemo.capture

class CapturePermissionCoordinator(
    private val requestProjectionPermission: () -> Unit,
) {
    fun start(
        needsNotificationPermission: Boolean,
        requestNotificationPermission: () -> Unit,
    ) {
        if (needsNotificationPermission) {
            requestNotificationPermission()
        } else {
            requestProjectionPermission()
        }
    }

    fun onNotificationPermissionResult(@Suppress("UNUSED_PARAMETER") isGranted: Boolean) {
        requestProjectionPermission()
    }
}
