package su.seemine.seekillfild;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class SeeKillFild extends JavaPlugin implements Listener {

    private Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private String bossBarMessage, actionBarMessage, titleMessage, subtitleMessage, message;
    private int bossBarTime, actionBarTime, titleTime, subtitleTime;
    private boolean bossBarEnabled, actionBarEnabled, titleEnabled, subtitleEnabled, messageEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("skf").setExecutor(new CommandReload());
    }

    @Override
    public void onDisable() {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.removeAll();
        }
        activeBossBars.clear();
    }

    private void loadConfig() {
        reloadConfig();

        bossBarEnabled = getConfig().getBoolean("settings.BossBar");
        bossBarTime = getConfig().getInt("settings.BossBarTime");
        bossBarMessage = getConfig().getString("settings.BossBar_message");

        actionBarEnabled = getConfig().getBoolean("settings.actionbar");
        actionBarTime = getConfig().getInt("settings.actionbarTime");
        actionBarMessage = getConfig().getString("settings.actionbar_message");

        titleEnabled = getConfig().getBoolean("settings.Title");
        titleTime = getConfig().getInt("settings.TitleTime");
        titleMessage = getConfig().getString("settings.Title_message");

        subtitleEnabled = getConfig().getBoolean("settings.SubTitle");
        subtitleTime = getConfig().getInt("settings.SubTitleTime");
        subtitleMessage = getConfig().getString("settings.SubTitle_message");

        messageEnabled = getConfig().getBoolean("settings.Message");
        message = getConfig().getString("settings.Message_message");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || !killer.isOnline()) {
            return;
        }

        if (bossBarEnabled) {
            displayBossBar(killer, victim);
        }
        if (actionBarEnabled) {
            displayActionBar(killer, victim);
        }
        if (titleEnabled || subtitleEnabled) {
            displayTitle(killer, victim);
        }
        if (messageEnabled) {
            sendMessageToPlayer(killer, victim);
        }
    }

    private void displayBossBar(Player killer, Player victim) {
        BossBar oldBossBar = activeBossBars.get(killer.getUniqueId());
        if (oldBossBar != null) {
            oldBossBar.removeAll();
        }

        String formattedMessage = ChatColor.translateAlternateColorCodes('&',
                bossBarMessage.replace("{dead}", victim.getName()));

        BossBar bossBar = Bukkit.createBossBar(formattedMessage, BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(killer);
        bossBar.setVisible(true);

        activeBossBars.put(killer.getUniqueId(), bossBar);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (bossBar != null) {
                bossBar.removeAll();
                activeBossBars.remove(killer.getUniqueId());
            }
        }, bossBarTime * 20L);
    }

    private void displayActionBar(Player killer, Player victim) {
        String formattedMessage = ChatColor.translateAlternateColorCodes('&',
                actionBarMessage.replace("{dead}", victim.getName()));

        killer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(formattedMessage));

        if (actionBarTime > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (killer.isOnline()) {
                    killer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(""));
                }
            }, actionBarTime * 20L);
        }
    }

    private void displayTitle(Player killer, Player victim) {
        String titleText = "";
        String subtitleText = "";

        if (titleEnabled) {
            titleText = ChatColor.translateAlternateColorCodes('&',
                    titleMessage.replace("{dead}", victim.getName()));
        }

        if (subtitleEnabled) {
            subtitleText = ChatColor.translateAlternateColorCodes('&',
                    subtitleMessage.replace("{dead}", victim.getName()));
        }

        int fadeIn = 10;
        int stay = Math.max(titleTime, subtitleTime) * 20;
        int fadeOut = 10;

        killer.sendTitle(titleText, subtitleText, fadeIn, stay, fadeOut);
    }

    private void sendMessageToPlayer(Player killer, Player victim) {
        String formattedMessage = ChatColor.translateAlternateColorCodes('&',
                message.replace("{dead}", victim.getName()));
        killer.sendMessage(formattedMessage);
    }

    public class CommandReload implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (sender.hasPermission("seekillfild.reload")) {
                loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Плагин перезагружен!");
                return true;
            }
            sender.sendMessage(ChatColor.RED + "У вас нет разрешения на эту команду!");
            return false;
        }
    }
}