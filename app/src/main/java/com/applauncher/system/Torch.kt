package com.applauncher.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Flashlight on/off. Needs no permission. */
class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on.asStateFlow()

    val available: Boolean get() = cameraId != null

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _on.value = enabled
        }
    }

    init {
        if (cameraId != null) cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun toggle() {
        val id = cameraId ?: return
        runCatching { cameras.setTorchMode(id, !_on.value) }
    }

    fun release() {
        runCatching { cameras.unregisterTorchCallback(callback) }
    }
}
