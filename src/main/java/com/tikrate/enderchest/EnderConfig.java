package com.tikrate.enderchest;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class EnderConfig extends JavaPlugin {

    private FileConfiguration dataConfig;
    private File dataConfigFile;

    private EnderUpgrade enderUpgrade;
    private EnderStorage enderStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadDataConfig();

        this.enderStorage = new EnderStorage(getDataFolder(), getLogger());

        this.enderUpgrade = new EnderUpgrade(this);
        getServer().getPluginManager().registerEvents(enderUpgrade, this);

        registerCommand("ec", "Открывает интерфейс эндер-сундука.");
        registerCommand("enderchest", "Открывает интерфейс эндер-сундука.");

        getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                getLogger().info("Пропускаем загрузку сохранённых данных для " + event.getPlayer().getName());
            }
        }, this);

        getLogger().info("EnderChest плагин активирован.");
    }

    private void registerCommand(String name, String description) {
        PluginCommand command = getCommand(name);
        if (command == null) return;

        command.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Команда доступна только для игроков!");
                return false;
            }

            Player player = (Player) sender;

            if (!player.hasPermission("enderchest.ec")) {
                player.sendMessage("§cУ вас нет прав для выполнения этой команды!");
                return false;
            }

            this.enderUpgrade.ecCommand(player);
            return true;
        });

        command.setDescription(description);
        command.setUsage("/" + name);
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            enderStorage.saveEnder(player);
        }

        getLogger().info("Данные эндер-сундуков сохранены. EnderChest плагин выключён.");
    }

    public FileConfiguration getDataConfig() {
        if (dataConfig == null) {
            reloadDataConfig();
        }
        return dataConfig;
    }

    public void reloadDataConfig() {
        if (dataConfigFile == null) {
            dataConfigFile = new File(getDataFolder(), "data.yml");
        }

        if (!dataConfigFile.exists()) {
            try {
                dataConfigFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать data.yml!");
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataConfigFile);
    }

    public void saveDataConfig() {
        if (dataConfig == null || dataConfigFile == null) return;
        try {
            dataConfig.save(dataConfigFile);
            getLogger().info("Data.yml успешно сохранен!");
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить data.yml!");
            e.printStackTrace();
        }
    }
}