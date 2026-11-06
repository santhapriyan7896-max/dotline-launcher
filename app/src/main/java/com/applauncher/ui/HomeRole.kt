package com.applauncher.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.applauncher.LauncherViewModel

/** Returns a function that asks Android to make this app the home screen. */
@Composable
fun rememberHomeRequester(vm: LauncherViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.checkDefaultHome() }

    return {
        val roles = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            runCatching { launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { vm.startIntent(Intent(Settings.ACTION_HOME_SETTINGS)) }
        } else {
            vm.startIntent(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
