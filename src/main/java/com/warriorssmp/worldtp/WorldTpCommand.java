package com.warriorssmp.worldtp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * /wtp <world> - teleports the sender directly to the given world's configured
 * Multiverse spawn point, using a plain, synchronous Bukkit teleport.
 *
 * This deliberately does NOT go through Multiverse-Core's own teleport command or
 * its async safe-teleporter - it reads Multiverse-Core's own worlds.yml directly
 * (just as a plain YAML file, no dependency on the Multiverse-Core plugin/API
 * itself) to find the same spawn coordinates /mvtp would normally use, then calls
 * player.teleport(location) directly. This exists as a workaround for a confirmed
 * bug in Multiverse-Core's async cross-world teleport on this server - see the
 * matching upstream report for the same "Failed to async teleport ...
 * Failure{reason=...}" symptom.
 */
public class WorldTpCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    WorldTpCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /wtp <world>");
            return true;
        }

        String worldName = args[0];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "World '" + worldName + "' isn't loaded or doesn't exist.");
            return true;
        }

        Location destination = resolveMultiverseSpawn(world);
        if (destination == null) {
            // Fall back to the world's own vanilla spawn if Multiverse's worlds.yml
            // is missing, unreadable, or doesn't have this world's section.
            destination = world.getSpawnLocation();
        }

        player.teleport(destination);
        sender.sendMessage(ChatColor.GREEN + "Teleported to " + worldName + " (bypassing Multiverse).");
        return true;
    }

    /**
     * Reads plugins/Multiverse-Core/worlds.yml directly as plain YAML (no
     * Multiverse-Core API dependency) and pulls out the spawn-location fields for
     * the given world's section, keyed by its namespaced key (e.g.
     * "minecraft:world_arena", or "minecraft:overworld" for the main world) -
     * matching exactly how Multiverse-Core itself keys that file.
     */
    private Location resolveMultiverseSpawn(World world) {
        File worldsFile = new File(plugin.getDataFolder().getParentFile(), "Multiverse-Core/worlds.yml");
        if (!worldsFile.exists()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(worldsFile);
        String key = world.getKey().toString(); // e.g. "minecraft:world_arena"
        ConfigurationSection worldSection = config.getConfigurationSection(key);
        if (worldSection == null) {
            return null;
        }

        ConfigurationSection spawn = worldSection.getConfigurationSection("spawn-location");
        if (spawn == null) {
            return null;
        }

        double x = spawn.getDouble("x", world.getSpawnLocation().getX());
        double y = spawn.getDouble("y", world.getSpawnLocation().getY());
        double z = spawn.getDouble("z", world.getSpawnLocation().getZ());
        float pitch = (float) spawn.getDouble("pitch", 0.0);
        float yaw = (float) spawn.getDouble("yaw", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }
}
