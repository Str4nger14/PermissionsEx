/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.stellardrift.permissionsex.velocity

import ca.stellardrift.permissionsex.BaseDirectoryScope
import ca.stellardrift.permissionsex.ImplementationInterface
import ca.stellardrift.permissionsex.PermissionsEx
import ca.stellardrift.permissionsex.PermissionsEx.GLOBAL_CONTEXT
import ca.stellardrift.permissionsex.PermissionsEx.SUBJECTS_USER
import ca.stellardrift.permissionsex.config.FilePermissionsExConfiguration
import ca.stellardrift.permissionsex.logging.FormattedLogger
import ca.stellardrift.permissionsex.minecraft.MinecraftPermissionsEx
import ca.stellardrift.permissionsex.proxycommon.ProxyCommon.IDENT_SERVER_CONSOLE
import ca.stellardrift.permissionsex.proxycommon.ProxyCommon.SUBJECTS_SYSTEM
import ca.stellardrift.permissionsex.proxycommon.ProxyContextDefinition
import ca.stellardrift.permissionsex.sql.hikari.Hikari
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.permission.PermissionsSetupEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyReloadEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.permission.PermissionProvider
import com.velocitypowered.api.permission.Tristate
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.sql.DataSource
import org.slf4j.Logger
import org.spongepowered.configurate.hocon.HoconConfigurationLoader

private val SERVER_PATH = Paths.get(".")
private val PLUGINS_PATH = SERVER_PATH.resolve("plugins")

@Plugin(id = ProjectData.ARTIFACT_ID, name = ProjectData.NAME, version = ProjectData.VERSION, description = ProjectData.DESCRIPTION)
class PermissionsExPlugin @Inject constructor(rawLogger: Logger, internal val server: ProxyServer, @DataDirectory private val dataPath: Path) : ImplementationInterface {

    private val exec = Executors.newCachedThreadPool()

    override fun getBaseDirectory(scope: BaseDirectoryScope): Path {
        return when (scope) {
            BaseDirectoryScope.CONFIG -> dataPath
            BaseDirectoryScope.JAR -> PLUGINS_PATH
            BaseDirectoryScope.SERVER -> SERVER_PATH
            BaseDirectoryScope.WORLDS -> SERVER_PATH
        }
    }

    override fun getDataSourceForURL(url: String): DataSource {
        return Hikari.createDataSource(url, dataPath)
    }

    override fun getLogger(): FormattedLogger = logger

    override fun getAsyncExecutor(): Executor {
        return exec
    }

    override fun getVersion(): String {
        return ProjectData.VERSION
    }

    private val logger = FormattedLogger.forLogger(rawLogger, true)

    private var _manager: MinecraftPermissionsEx<*>? = null

    val manager: PermissionsEx<*> get() = requireNotNull(this._manager) { "PermissionsEx has not yet been initialized" }.engine()

    @Subscribe
    fun onProxyInit(event: ProxyInitializeEvent) {
        Files.createDirectories(dataPath)

        val configLoader = HoconConfigurationLoader.builder()
            .path(dataPath.resolve("permissionsex.conf"))
            .defaultOptions { FilePermissionsExConfiguration.decorateOptions(it) }
            .build()

        try {
            this._manager = MinecraftPermissionsEx(PermissionsEx(FilePermissionsExConfiguration.fromLoader(configLoader), this))
        } catch (e: Exception) {
            logger.error(Messages.PLUGIN_INIT_ERROR(), e)
            return
        }
        manager.getSubjects(SUBJECTS_USER).typeInfo = UserSubjectTypeDefinition(this)
        manager.getSubjects(SUBJECTS_SYSTEM).transientData().update(IDENT_SERVER_CONSOLE.value) {
            it.setDefaultValue(GLOBAL_CONTEXT, 1)
        }

        this.manager.registerContextDefinitions(ProxyContextDefinition.INSTANCE, RemoteIpContextDefinition,
            LocalIpContextDefinition, LocalHostContextDefinition, LocalPortContextDefinition)

        this.manager.registerCommandsTo {
            val aliases = it.aliases.map { alias -> "/$alias" }
            val meta = server.commandManager.metaBuilder(aliases.first())
                .aliases(*aliases.subList(1, aliases.size).toTypedArray())
                .build()
            server.commandManager.register(meta, VelocityCommand(this, it))
        }
        logger.info(Messages.PLUGIN_INIT_SUCCESS(ProjectData.NAME, ProjectData.VERSION))
    }

    @Subscribe
    fun onDisable(event: ProxyShutdownEvent) {
        if (this._manager != null) {
            this._manager?.close()
            this._manager = null
        }
        this.exec.shutdown()
        try {
            this.exec.awaitTermination(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.error(Messages.PLUGIN_DISABLE_TIMEOUT())
            exec.shutdownNow()
        }
        logger.info(Messages.PLUGIN_DISABLE_SUCCESS(ProjectData.NAME, ProjectData.VERSION))
    }

    @Subscribe
    fun onReload(event: ProxyReloadEvent) {
        this.manager.reload()
    }

    @Subscribe
    fun onPermissionSetup(event: PermissionsSetupEvent) {
        event.provider = PermissionProvider {
            val func = PEXPermissionFunction(this, it)
            if (this._manager != null) {
                func.subject.identifier
            }
            func
        }
    }

    @Subscribe
    fun uncachePlayer(event: DisconnectEvent) {
        manager.callbackController.clearOwnedBy(event.player.uniqueId)
        manager.getSubjects(SUBJECTS_USER).uncache(event.player.uniqueId.toString())
    }
}

fun Int.asTristate(): Tristate {
    return when {
        this < 0 -> Tristate.FALSE
        this > 0 -> Tristate.TRUE
        else -> Tristate.UNDEFINED
    }
}
