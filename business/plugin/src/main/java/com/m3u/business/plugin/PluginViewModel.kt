package com.m3u.business.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.core.plugin.RemoteService
import com.m3u.core.plugin.PluginConst
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

data class App(
    val name: String,
    val icon: Drawable,
    val packageName: String,
    val mainClassName: String,
    val version: String,
    val description: String,
)

@HiltViewModel
class PluginViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    @SuppressLint("WrongConstant")
    val applications: StateFlow<List<App>> = flow {
        val pkgManager = context.packageManager
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pkgManager.getInstalledPackages(PACKAGE_FLAGS_LEGACY)
        }
        val apps = installedPkgs
            .mapNotNull { info ->
                val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        pkgManager.getApplicationInfo(
                            info.packageName,
                            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                        )
                    }.getOrElse { info.applicationInfo }
                } else {
                    info.applicationInfo
                } ?: return@mapNotNull null
                val isPlugin = applicationInfo.metaData?.getString(PLUGIN_FEATURE) != null ||
                        applicationInfo.metaData?.getString(EXT_FEATURE) != null
                if (!isPlugin) return@mapNotNull null
                val mainClass = applicationInfo.metaData?.getString(PLUGIN_MAIN_CLASS)
                    ?: applicationInfo.metaData?.getString(EXT_MAIN_CLASS)
                    ?: return@mapNotNull null
                val name = pkgManager.getApplicationLabel(applicationInfo).toString()
                val icon = pkgManager.getApplicationIcon(applicationInfo)
                val version = applicationInfo.metaData?.getString(PLUGIN_VERSION)
                    ?: applicationInfo.metaData?.getString(EXT_VERSION)
                    ?: ""
                val description = applicationInfo.metaData?.getString(PLUGIN_DESCRIPTION)
                    ?: applicationInfo.metaData?.getString(EXT_DESCRIPTION)
                    ?: ""
                App(
                    name = name,
                    icon = icon,
                    packageName = info.packageName,
                    mainClassName = mainClass.let {
                        if (it.startsWith(".")) info.packageName + it else it
                    },
                    version = version,
                    description = description,
                )
            }
            .toList()
        emit(apps)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun runPlugin(app: App) {
        val intent = Intent().apply {
            this.component = ComponentName(app.packageName, app.mainClassName)
            putExtra(PluginConst.PACKAGE_NAME, context.packageName)
            putExtra(PluginConst.CLASS_NAME, RemoteService::class.qualifiedName)
            putExtra(PluginConst.ACCESS_KEY, UUID.randomUUID().toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

}

@Suppress("DEPRECATION")
private val PACKAGE_FLAGS_LEGACY: Int = PackageManager.GET_META_DATA

private const val PLUGIN_FEATURE = "m3uandroid.plugin"
private const val PLUGIN_MAIN_CLASS = "m3uandroid.plugin.class"
private const val PLUGIN_VERSION = "m3uandroid.plugin.version"
private const val PLUGIN_DESCRIPTION = "m3uandroid.plugin.description"

private const val EXT_FEATURE = "m3uandroid.extension"
private const val EXT_MAIN_CLASS = "m3uandroid.extension.class"
private const val EXT_VERSION = "m3uandroid.extension.version"
private const val EXT_DESCRIPTION = "m3uandroid.extension.description"
