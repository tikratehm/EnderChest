package com.tikrate.enderchest;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class EnderStorage {
    private final File dataFile;
    private final YamlConfiguration dataConfig;
    private final Logger logger;

    public EnderStorage(File dataFolder, Logger logger) {
        this.dataFile = new File(dataFolder, "enderchests.yml");
        this.logger = logger;

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                logger.severe("Не удалось создать файл enderchests.yml!");
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveEnder(Player player) {
        Inventory enderChest = player.getEnderChest();
        String playerKey = player.getUniqueId().toString();

        ItemStack[] contents = enderChest.getContents();
        dataConfig.set(playerKey, contents);

        saveConfig();
    }

    public void loadEnder(Player player) {
        String playerKey = player.getUniqueId().toString();

        if (dataConfig.contains(playerKey)) {
            ItemStack[] contents = ((java.util.List<ItemStack>) dataConfig.get(playerKey)).toArray(new ItemStack[0]);
            player.getEnderChest().setContents(contents);
        }
    }

    private void saveConfig() {
        try {
            dataConfig.save(dataFile);
            logger.info("Файл enderchests.yml сохранен.");
        } catch (IOException e) {
            logger.severe("Не удалось сохранить enderchests.yml!");
            e.printStackTrace();
        }
    }
}