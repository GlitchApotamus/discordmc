package me.duro.discordmc

import me.duro.discordmc.commands.DiscordMCCommand
import me.duro.discordmc.listeners.ChatListener
import me.duro.discordmc.listeners.CommandListener
import me.duro.discordmc.listeners.DeathListener
import me.duro.discordmc.listeners.JoinQuitListener
import me.duro.discordmc.listeners.ScheduledRestart
import me.duro.discordmc.utils.Config
import me.duro.discordmc.utils.ConfigLoader
import org.bukkit.plugin.java.JavaPlugin


class DiscordMC : JavaPlugin() {
    lateinit var toml: Config

    override fun onEnable() {
        val events =
                arrayOf(
                        ChatListener(),
                        CommandListener(),
                        DeathListener(),
                        JoinQuitListener(),
                        ScheduledRestart(this)
                )
        val commands = arrayOf("discordmc" to DiscordMCCommand())

        events.forEach {
            server.pluginManager.registerEvents(it, this)
            // ScheduledRestart.checkForServerStart()
        }

        commands.forEach { this.getCommand(it.first)?.setExecutor(it.second) }

        logger.info("Scheduled Restart Plugin has been enabled!")

        instance = this
        toml = ConfigLoader.loadConfig(this)!!
    }

    override fun onDisable() {}

    companion object {

        lateinit var instance: DiscordMC
    }
}
