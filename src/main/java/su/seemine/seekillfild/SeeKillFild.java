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
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class SeeKillFild extends JavaPlugin implements Listener {

    private Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private String bossBarMessage, actionBarMessage, titleMessage, subtitleMessage, message;
    private int bossBarTime, actionBarTime, titleTime, subtitleTime;
    private boolean bossBarEnabled, actionBarEnabled, titleEnabled, subtitleEnabled, messageEnabled;
    private boolean isLegacyVersion;

    @Override
    public void onEnable() {
        checkServerVersion();
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

    private void checkServerVersion() {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            int majorVersion = Integer.parseInt(version.split("_")[1]);
            isLegacyVersion = majorVersion <= 12;
        } catch (Exception e) {
            isLegacyVersion = true;
        }
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

        new BukkitRunnable() {
            @Override
            public void run() {
                if (bossBar != null) {
                    bossBar.removeAll();
                    activeBossBars.remove(killer.getUniqueId());
                }
            }
        }.runTaskLater(this, bossBarTime * 20L);
    }

    private void displayActionBar(Player killer, Player victim) {
        String formattedMessage = ChatColor.translateAlternateColorCodes('&',
                actionBarMessage.replace("{dead}", victim.getName()));

        if (isLegacyVersion) {
            sendLegacyActionBar(killer, formattedMessage);
        } else {
            sendModernActionBar(killer, formattedMessage);
        }

        if (actionBarTime > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (killer.isOnline()) {
                        if (isLegacyVersion) {
                            sendLegacyActionBar(killer, "");
                        } else {
                            sendModernActionBar(killer, "");
                        }
                    }
                }
            }.runTaskLater(this, actionBarTime * 20L);
        }
    }

    private void sendModernActionBar(Player player, String message) {
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object chatMessageType = chatMessageTypeClass.getDeclaredField("ACTION_BAR").get(null);
            Object textComponent = textComponentClass.getConstructor(String.class).newInstance(message);

            Method sendMessageMethod = player.getClass().getMethod("spigot");
            Object spigot = sendMessageMethod.invoke(player);

            Method sendMessageMethod2 = spigot.getClass().getMethod("sendMessage", chatMessageTypeClass, textComponentClass);
            sendMessageMethod2.invoke(spigot, chatMessageType, textComponent);
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    private void sendLegacyActionBar(Player player, String message) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);

            Class<?> packetClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".PacketPlayOutChat");
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".IChatBaseComponent");
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".IChatBaseComponent$ChatSerializer");

            Method aMethod = chatSerializerClass.getMethod("a", String.class);
            Object component = aMethod.invoke(null, "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");

            Constructor<?> packetConstructor = packetClass.getConstructor(iChatBaseComponentClass, byte.class);
            Object packet = packetConstructor.newInstance(component, (byte) 2);

            Method getHandleMethod = craftPlayerClass.getMethod("getHandle");
            Object entityPlayer = getHandleMethod.invoke(craftPlayer);

            Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".PlayerConnection");
            Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".Packet"));

            Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
            sendPacketMethod.invoke(playerConnection, packet);
        } catch (Exception e) {
            player.sendMessage(message);
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

        if (isLegacyVersion) {
            sendLegacyTitle(killer, titleText, subtitleText, fadeIn, stay, fadeOut);
        } else {
            killer.sendTitle(titleText, subtitleText, fadeIn, stay, fadeOut);
        }
    }

    private void sendLegacyTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);

            Class<?> packetTitleClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".PacketPlayOutTitle");
            Class<?> iChatBaseComponentClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".IChatBaseComponent");
            Class<?> chatSerializerClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".IChatBaseComponent$ChatSerializer");
            Class<?> enumTitleActionClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".PacketPlayOutTitle$EnumTitleAction");

            Method aMethod = chatSerializerClass.getMethod("a", String.class);

            if (title != null && !title.isEmpty()) {
                Object titleComponent = aMethod.invoke(null, "{\"text\":\"" + title.replace("\"", "\\\"") + "\"}");
                Object titleAction = enumTitleActionClass.getField("TITLE").get(null);
                Constructor<?> titleConstructor = packetTitleClass.getConstructor(enumTitleActionClass, iChatBaseComponentClass, int.class, int.class, int.class);
                Object titlePacket = titleConstructor.newInstance(titleAction, titleComponent, fadeIn, stay, fadeOut);

                sendPacket(craftPlayer, titlePacket);
            }

            if (subtitle != null && !subtitle.isEmpty()) {
                Object subtitleComponent = aMethod.invoke(null, "{\"text\":\"" + subtitle.replace("\"", "\\\"") + "\"}");
                Object subtitleAction = enumTitleActionClass.getField("SUBTITLE").get(null);
                Constructor<?> subtitleConstructor = packetTitleClass.getConstructor(enumTitleActionClass, iChatBaseComponentClass, int.class, int.class, int.class);
                Object subtitlePacket = subtitleConstructor.newInstance(subtitleAction, subtitleComponent, fadeIn, stay, fadeOut);

                sendPacket(craftPlayer, subtitlePacket);
            }
        } catch (Exception e) {
            player.sendMessage(title + (subtitle.isEmpty() ? "" : "\n" + subtitle));
        }
    }

    private void sendPacket(Object craftPlayer, Object packet) throws Exception {
        Method getHandleMethod = craftPlayer.getClass().getMethod("getHandle");
        Object entityPlayer = getHandleMethod.invoke(craftPlayer);

        Class<?> playerConnectionClass = Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".PlayerConnection");
        Method sendPacketMethod = playerConnectionClass.getMethod("sendPacket", Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + ".Packet"));

        Object playerConnection = entityPlayer.getClass().getField("playerConnection").get(entityPlayer);
        sendPacketMethod.invoke(playerConnection, packet);
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