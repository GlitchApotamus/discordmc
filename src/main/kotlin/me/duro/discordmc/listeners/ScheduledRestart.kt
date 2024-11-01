package me.duro.discordmc.listeners

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import me.duro.discordmc.DiscordMC
import me.duro.discordmc.utils.Webhook
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

class ScheduledRestart(private val plugin: Plugin) : Listener {

    private val firstWarningBeforeRestart: Duration = Duration.ofMinutes(30)
    private val secondWarningBeforeRestart: Duration = Duration.ofMinutes(10)
    private val backupDurationBeforeRestart: Duration = Duration.ofMinutes(2)
    private var nextRestartTime: LocalDateTime = calculateNextRestartTime()
    private var lastCountdownMessageTime: LocalDateTime? = null
    private var isBackingUp = false

    init {

        plugin.let {
            object : BukkitRunnable() {
                        override fun run() {
                            checkForUpcomingRestart()
                        }
                    }
                    .runTaskTimerAsynchronously(plugin, 0L, 20L * 60)
        }
    }

    fun checkForServerStart() {
        sendWarning("Server has started back up. Checking for scheduled restarts...")
        checkForUpcomingRestart()
    }

    private fun calculateNextRestartTime(): LocalDateTime {
        val now = LocalDateTime.now()
        val nextNoon = now.with(LocalTime.NOON)
        val nextMidnight = now.with(LocalTime.MIDNIGHT).plusDays(1)

        return when {
            now.isBefore(nextNoon) -> nextNoon
            now.isBefore(nextMidnight) -> nextMidnight
            else -> nextNoon.plusDays(1)
        }
    }

    private fun performBackup() {
        val backupRunnable = Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ebackup backup")
        }

        Bukkit.getScheduler().runTask(plugin, backupRunnable)
    }

    private fun stopServer() {
        val stopRunnable = Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop")
        }

        Bukkit.getScheduler().runTask(plugin, stopRunnable)
    }

    private fun sendWarning(message: String) {
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage("§c[Server Notice] $message")
        }
    }

    private fun sendHook(msg: String) {
        val config = DiscordMC.instance.toml.restart
        Webhook.sendWebhook(config.webhook, config.embed, config.embedEnabled, msg)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        checkForUpcomingRestart(event.player)
    }

    private fun calculateWarningTimes(): Map<String, LocalDateTime> {

        val thirtyMinutesBefore =
                nextRestartTime.minusMinutes(firstWarningBeforeRestart.toMinutes())
        val tenMinutesBefore = nextRestartTime.minusMinutes(secondWarningBeforeRestart.toMinutes())
        val twoMinutesBefore = nextRestartTime.minusMinutes(backupDurationBeforeRestart.toMinutes())

        return mapOf(
                "thirtyMinutes" to thirtyMinutesBefore,
                "tenMinutes" to tenMinutesBefore,
                "twoMinutes" to twoMinutesBefore
        )
    }

    fun checkForUpcomingRestart(player: org.bukkit.entity.Player? = null) {

        val now = LocalDateTime.now()
        val timeToRestart = Duration.between(now, nextRestartTime)

        when {
            timeToRestart <= Duration.ofMinutes(2) -> {
                if (!isBackingUp) {
                    isBackingUp = true
                    sendWarning("Server backup is being performed now.")
                    performBackup()
                }

                if (timeToRestart <= Duration.ofSeconds(10)) {
                    sendWarning("Server will restart in 10 seconds!")
                    sendHook("Server will restart in 10 seconds!")

                    if (timeToRestart <= Duration.ZERO) {
                        stopServer()
                        return
                    }
                }
            }
            timeToRestart <= Duration.ofMinutes(10) -> {
                if (lastCountdownMessageTime == null ||
                                now.isAfter(lastCountdownMessageTime!!.plusMinutes(1))
                ) {
                    val minutesLeft = timeToRestart.toMinutes().toInt()
                    sendWarning("Server will restart in $minutesLeft minutes!")
                    sendHook("Server will restart in $minutesLeft minutes!")
                    lastCountdownMessageTime = now
                }
            }
            timeToRestart <= Duration.ofMinutes(30) -> {
                val msg = "Server will restart in 30 minutes!"
                sendWarning(msg)
                sendHook(msg)
            }
            now.isAfter(nextRestartTime) || now.isEqual(nextRestartTime) -> {
                val msg = "Server is restarting now!"
                sendWarning(msg)
                sendHook(msg)
                stopServer()
                nextRestartTime = calculateNextRestartTime()
                return
            }
            else -> {
                player?.sendMessage("§aNo scheduled restarts within the next 30 minutes.")
            }
        }
        if (timeToRestart > Duration.ofMinutes(2)) {
            isBackingUp = false
        }
    }
}
