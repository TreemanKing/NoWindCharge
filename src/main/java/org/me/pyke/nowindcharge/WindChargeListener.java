package org.me.pyke.nowindcharge;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class WindChargeListener implements Listener {

    private final NoWindCharge plugin;

    public WindChargeListener(NoWindCharge plugin) {
        this.plugin = plugin;
    }

    private enum ExclusionType {
        WORLD, REGION, NONE
    }

    private ExclusionType getExclusionType(Player player) {
        List<String> excludedRegions = plugin.getConfig().getStringList("excluded-regions");
        List<String> excludedWorlds = plugin.getConfig().getStringList("excluded-worlds");
        if (excludedWorlds.contains(player.getWorld().getName())) {
            return ExclusionType.WORLD;
        }
        RegionManager regionManager = com.sk89q.worldguard.WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(player.getWorld()));
        if (regionManager == null) return ExclusionType.NONE;
        ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));
        for (ProtectedRegion region : regions) {
            if (excludedRegions.contains(region.getId())) {
                return ExclusionType.REGION;
            }
        }
        return ExclusionType.NONE;
    }

    private String getAreaName(Player player, ExclusionType type) {
        if (type == ExclusionType.WORLD) {
            return player.getWorld().getName();
        } else if (type == ExclusionType.REGION) {
            RegionManager regionManager = com.sk89q.worldguard.WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(player.getWorld()));
            if (regionManager == null) return "unknown";
            ApplicableRegionSet regions = regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(player.getLocation()));
            for (ProtectedRegion region : regions) {
                return region.getId();
            }
        }
        return "unknown";
    }

    @EventHandler
    public void onPlayerUseWindCharge(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        // Check if neither hand has WIND_CHARGE OR the action is not a right-click
        if ((mainHandItem.getType() != Material.WIND_CHARGE && offHandItem.getType() != Material.WIND_CHARGE) ||
                (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (plugin.getConfig().getBoolean("disable-completely", false)) {
            event.setCancelled(true);
            String message = plugin.getConfig().getString("completely-disabled-message", "&cWIND_CHARGE usage is completely disabled!");
            player.sendMessage(plugin.formatMessage(message));
            return;
        }

        if (player.hasPermission("nowindcharge.bypass")) {
            return;
        }

        ExclusionType exclusionType = getExclusionType(player);
        if (exclusionType != ExclusionType.NONE) {
            event.setCancelled(true);
            if (plugin.getConfig().getBoolean("send-error-message", true)) {
                String message = plugin.getConfig().getString("deny-message", "&cYou cannot use this item in area &e%area%&c!");
                player.sendMessage(plugin.formatMessage(message.replace("%area%", getAreaName(player, exclusionType))));
            }
        }
    }

    @EventHandler
    public void onWindChargeExplosion(ExplosionPrimeEvent event) {
        // Check if the plugin is completely disabled for WIND_CHARGE
        if (plugin.getConfig().getBoolean("disable-completely", false)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity().getType() == EntityType.WIND_CHARGE || event.getEntity().getType() == EntityType.BREEZE_WIND_CHARGE) {
            for (Player player : event.getEntity().getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(event.getEntity().getLocation()) < 16 && isInExcludedRegion(player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isInExcludedRegion(Player player) {
        return getExclusionType(player) != ExclusionType.NONE;
    }
}
