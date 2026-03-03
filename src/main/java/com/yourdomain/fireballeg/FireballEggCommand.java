package com.yourdomain.fireballeg;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.NonNull;

public class FireballEggCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /fireballeg [get|reload]");
            return true;
        }

        if (args[0].equalsIgnoreCase("get")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Only players can execute this command.");
                return true;
            }

            Player player = (Player) sender;
            giveFireballEgg(player);
            player.sendMessage(ChatColor.GREEN + "Success!");
            return true;
        }
        else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("fireballeg.reload")) {
                sender.sendMessage(ChatColor.RED + "You have no permission!");
                return true;
            }

            FireballEggPlugin.getInstance().reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "config reloaded!");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "usage: /fireballeg [get|reload]");
        return true;
    }

    private void giveFireballEgg(Player player) {
        ItemStack fireballEgg = new ItemStack(Material.FIRE_CHARGE, 1);
        player.getInventory().addItem(fireballEgg);
    }
}