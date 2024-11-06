package me.me.discordrelay;

import me.me.discordrelay.discord.BatchMessageHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ShushCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof  Player)) {
            return false;
        }
        Player p = (Player) sender;
        PersistentDataContainer data = p.getPersistentDataContainer();
        NamespacedKey shushKey = new NamespacedKey(DiscordRelay.getPlugin(), "shhhhh");

        if (data.has(shushKey, PersistentDataType.STRING)) {
            // Already shushed
            data.remove(shushKey);
            p.sendMessage("Sneaky mode disabled!");
            return true;
        }

        data.set(shushKey, PersistentDataType.STRING, "shushed");
        DiscordRelay.getPlugin().removeLastJoinMessage(p);
        p.sendMessage("Sneaky mode enabled");
        return true;
    }
}
