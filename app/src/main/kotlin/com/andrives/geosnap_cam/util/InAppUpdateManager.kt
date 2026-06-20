package com.andrives.geosnap_cam.util

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateState {
    CHECKING,
    NO_UPDATE,
    UPDATE_AVAILABLE, // Triggers our custom banner
    DOWNLOADING,
    DOWNLOADED,       // Triggers "Install" action
    FAILED
}

@Singleton
class InAppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    
    private val _updateState = MutableStateFlow(UpdateState.CHECKING)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var appUpdateInfo: AppUpdateInfo? = null

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> _updateState.value = UpdateState.DOWNLOADING
            InstallStatus.DOWNLOADED -> _updateState.value = UpdateState.DOWNLOADED
            InstallStatus.FAILED -> _updateState.value = UpdateState.FAILED
            InstallStatus.CANCELED -> _updateState.value = UpdateState.UPDATE_AVAILABLE
            else -> {}
        }
    }

    init {
        appUpdateManager.registerListener(installStateUpdatedListener)
    }

    fun checkForUpdate() {
        _updateState.value = UpdateState.CHECKING
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { info ->
            this.appUpdateInfo = info
            val isUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            
            // Aquí podemos evaluar lógicas futuras para forzar actualización (IMMEDIATE)
            // Por ejemplo, si clientVersionStalenessDays() > 30 o info.availableVersionCode() es crítico.
            val isFlexibleUpdateAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

            if (isUpdateAvailable && isFlexibleUpdateAllowed) {
                _updateState.value = UpdateState.UPDATE_AVAILABLE
            } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _updateState.value = UpdateState.DOWNLOADED
            } else {
                _updateState.value = UpdateState.NO_UPDATE
            }
        }.addOnFailureListener {
            _updateState.value = UpdateState.FAILED
        }
    }

    fun startUpdateFlow(activity: Activity, updateType: Int = AppUpdateType.FLEXIBLE, requestCode: Int = UPDATE_REQUEST_CODE) {
        appUpdateInfo?.let { info ->
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateType,
                activity,
                requestCode
            )
        }
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun onDestroy() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    companion object {
        const val UPDATE_REQUEST_CODE = 1001
    }
}
