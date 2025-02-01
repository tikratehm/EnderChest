package com.tikrate.enderchest;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class EnderUpgrade implements Listener {

    private final EnderConfig plugin;
    private final PlayerPointsAPI pointsAPI;

    private final String title;
    private final int xpCost;
    private final int pointsCost;
    private final String unlockName;
    private final List<String> unlockLore;
    private final String lockedName;
    private final String xpMessage;
    private final String pointsMessage;
    private final String successMessage;

    private static final int TOTAL_SLOTS = 54;
    private static final int UNLOCKED_SLOTS = 27;

    private final Map<UUID, List<Integer>> unlockedSlots = new HashMap<>();
    private final Map<UUID, Integer> nextUnlockableSlot = new HashMap<>();
    private final Map<UUID, ItemStack[]> inventories = new HashMap<>();


    public EnderUpgrade(EnderConfig plugin) {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();

        this.title = config.getString("enderchest.title", "Эндер Сундук");
        this.xpCost = config.getInt("enderchest.first_unlock_cost_xp", 100);
        this.pointsCost = config.getInt("enderchest.first_unlock_cost_points", 50);

        this.unlockName = color(config.getString("enderchest.lore.unlockable_slot.displayname", "§6Разблокировать слот за"));
        this.unlockLore = Lore(config.getStringList("enderchest.lore.unlockable_slot.lore"));

        this.lockedName = color(config.getString("enderchest.lore.locked_slot.displayname", "§c▶ &fРазблокировать слоты можно только &cпоследовательно"));

        this.xpMessage = color(config.getString("enderchest.messages.no_xp", "§c▶ &fНедостаточно &cуровня &fопыта"));
        this.pointsMessage = color(config.getString("enderchest.messages.no_points", "§c▶ &fНедостаточно &cрублей"));
        this.successMessage = color(config.getString("enderchest.messages.unlock_success", "§a▶ &fСлот §aразблокирован"));

        if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            this.pointsAPI = PlayerPoints.getInstance().getAPI();
        } else {
            this.pointsAPI = null;
            Bukkit.getLogger().warning("[EnderChest] PlayerPoints не найден! Разблокировка за рубли недоступна.");
        }

        loadData();
    }


    @EventHandler
    public void InventoryClose(InventoryCloseEvent event) {
        if (Objects.equals(event.getView().getTitle(), title)) {
            Player player = (Player) event.getPlayer();
            Inventory inventory = event.getInventory();

            inventories.put(player.getUniqueId(), inventory.getContents());
            saveData();
        }
    }


    @EventHandler
    public void PlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.ENDER_CHEST) {
            if (!event.getAction().toString().contains("RIGHT_CLICK")) return;

            event.setCancelled(true);

            Player player = event.getPlayer();
            Inventory customInventory;

            if (inventories.containsKey(player.getUniqueId())) {
                customInventory = Bukkit.createInventory(player, TOTAL_SLOTS, title);
                ItemStack[] savedContents = inventories.get(player.getUniqueId());
                if (savedContents != null) {
                    customInventory.setContents(savedContents);
                }
            } else {
                customInventory = Bukkit.createInventory(player, TOTAL_SLOTS, title);
                unlockedSlots.putIfAbsent(player.getUniqueId(), new ArrayList<>());
                nextUnlockableSlot.putIfAbsent(player.getUniqueId(), UNLOCKED_SLOTS);
            }

            fillInventory(customInventory, player);

            player.openInventory(customInventory);
        }
    }


    @EventHandler
    public void InventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            Player player = (Player) event.getPlayer();

            Inventory customInventory = Bukkit.createInventory(player, TOTAL_SLOTS, title);

            unlockedSlots.putIfAbsent(player.getUniqueId(), new ArrayList<>());
            nextUnlockableSlot.putIfAbsent(player.getUniqueId(), UNLOCKED_SLOTS);

            fillInventory(customInventory, player);

            player.openInventory(customInventory);

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void InventoryClick(InventoryClickEvent event) {
        if (!Objects.equals(event.getView().getTitle(), title)) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= TOTAL_SLOTS) return;

        if (!unlockedSlots.getOrDefault(player.getUniqueId(), new ArrayList<>()).contains(slot)
                && slot >= UNLOCKED_SLOTS) {
            event.setCancelled(true);

            if (slot == nextUnlockableSlot.get(player.getUniqueId())) {
                if (event.isLeftClick()) {
                    if (player.getLevel() >= xpCost) {
                        player.setLevel(player.getLevel() - xpCost);
                        unlockSlot(player, slot);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    } else {
                        player.sendMessage(xpMessage);
                    }
                } else if (event.isRightClick()) {
                    if (pointsAPI == null) {
                        player.sendMessage("§c▶ &fРазблокировка за рубли недоступна. Установите PlayerPoints!");
                        return;
                    }

                    int points = pointsAPI.look(player.getUniqueId());
                    if (points >= pointsCost && pointsAPI.take(player.getUniqueId(), pointsCost)) {
                        unlockSlot(player, slot);
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                    } else {
                        player.sendMessage(pointsMessage);
                    }
                }
            }
        }
    }

    private void fillInventory(Inventory inventory, Player player) {
        UUID playerId = player.getUniqueId();

        List<Integer> playerUnlockedSlots = unlockedSlots.getOrDefault(playerId, new ArrayList<>());

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (inventory.getItem(i) != null) continue;

            if (i < UNLOCKED_SLOTS || playerUnlockedSlots.contains(i)) {
                inventory.setItem(i, null);
            } else if (i == nextUnlockableSlot.getOrDefault(playerId, -1)) {
                inventory.setItem(i, getItem(Material.ORANGE_STAINED_GLASS_PANE, unlockLore, unlockName, xpCost, pointsCost));
            } else {
                inventory.setItem(i, getItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, null, lockedName, 0, 0));
            }
        }
    }

    private void unlockSlot(Player player, int slot) {
        UUID playerId = player.getUniqueId();
        unlockedSlots.computeIfAbsent(playerId, k -> new ArrayList<>()).add(slot);

        Inventory inventory = player.getOpenInventory().getTopInventory();
        inventory.setItem(slot, null);

        int nextSlot = slot + 1;
        while (nextSlot < TOTAL_SLOTS && unlockedSlots.get(playerId).contains(nextSlot)) {
            nextSlot++;
        }
        nextUnlockableSlot.put(playerId, nextSlot < TOTAL_SLOTS ? nextSlot : -1);

        if (nextSlot < TOTAL_SLOTS) {
            inventory.setItem(nextSlot, getItem(Material.ORANGE_STAINED_GLASS_PANE, unlockLore, unlockName, xpCost, pointsCost));
        }

        player.sendMessage(successMessage.replace("%slot%", String.valueOf(slot + 1)));

        saveData();
    }

    private String color(String message) {
        return message == null ? "" : message.replace("&", "§");
    }

    private List<String> Lore(List<String> list) {
        return list.stream().map(this::color).collect(Collectors.toList());
    }

    private ItemStack getItem(Material material, List<String> lore, String title, int xp, int points) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (title != null && !title.trim().isEmpty()) {
                meta.setDisplayName(title);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream()
                        .map(line -> line.replace("%xp%", String.valueOf(xp)).replace("%points%", String.valueOf(points)))
                        .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void saveData() {
        FileConfiguration dataConfig = plugin.getDataConfig();

        unlockedSlots.forEach((uuid, slots) -> {
            dataConfig.set("players." + uuid.toString() + ".slots", slots);
        });

        inventories.forEach((uuid, items) -> {
            if (items != null) {
                List<Map<String, Object>> serializedItems = Arrays.stream(items)
                        .map(item -> item != null ? item.serialize() : null)
                        .collect(Collectors.toList());
                dataConfig.set("players." + uuid.toString() + ".inventory", serializedItems);
            }
        });

        plugin.saveDataConfig();
    }


    private void loadData() {
        FileConfiguration dataConfig = plugin.getDataConfig();

        if (dataConfig.contains("players")) {
            dataConfig.getConfigurationSection("players").getKeys(false).forEach(uuidString -> {
                try {
                    UUID uuid = UUID.fromString(uuidString);

                    List<Integer> slots = dataConfig.getIntegerList("players." + uuidString + ".slots");
                    unlockedSlots.put(uuid, slots);

                    if (dataConfig.contains("players." + uuidString + ".inventory")) {
                        List<?> rawList = dataConfig.getList("players." + uuidString + ".inventory");
                        if (rawList != null) {
                            ItemStack[] items = rawList.stream()
                                    .map(map -> (map instanceof Map<?, ?>) ? ItemStack.deserialize((Map<String, Object>) map) : null)
                                    .toArray(ItemStack[]::new);
                            inventories.put(uuid, items);
                        }
                    } else {
                        inventories.put(uuid, new ItemStack[TOTAL_SLOTS]);
                    }


                    int nextSlot = slots.isEmpty() ? UNLOCKED_SLOTS : Collections.max(slots) + 1;
                    nextUnlockableSlot.put(uuid, nextSlot < TOTAL_SLOTS ? nextSlot : -1);

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Некорректный UUID найден в data.yml: " + uuidString);
                }
            });
        }
    }

    public void ecCommand(Player player) {
        UUID playerId = player.getUniqueId();

        Inventory customInventory;

        if (inventories.containsKey(playerId)) {
            customInventory = Bukkit.createInventory(player, TOTAL_SLOTS, title);
            ItemStack[] savedContents = inventories.get(playerId);

            if (savedContents != null) {
                customInventory.setContents(savedContents);
            }
        } else {
            customInventory = Bukkit.createInventory(player, TOTAL_SLOTS, title);
            unlockedSlots.putIfAbsent(playerId, new ArrayList<>());
            nextUnlockableSlot.putIfAbsent(playerId, UNLOCKED_SLOTS);

            fillInventory(customInventory, player);
        }
        player.openInventory(customInventory);
    }
}