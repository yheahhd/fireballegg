package com.yourdomain.fireballeg;

import org.bukkit.plugin.java.JavaPlugin;

public class FireballEggPlugin extends JavaPlugin {

    private static FireballEggPlugin instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        FireballEggListener listener = new FireballEggListener();
        getServer().getPluginManager().registerEvents(listener, this);

        listener.startVelocityProtectionTask();

        this.getCommand("fireballeg").setExecutor(new FireballEggCommand());

        getLogger().info("FireballEggPlugin enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("FireballEggPlugin disabled");
    }

    public static FireballEggPlugin getInstance() {
        return instance;
    }
}