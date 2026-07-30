package dev.bibo.aqua;

import dev.bibo.aqua.anim.Animator;
import dev.bibo.aqua.command.AquaCommand;
import dev.bibo.aqua.form.Forms;
import dev.bibo.aqua.fx.WaterStyle;
import dev.bibo.aqua.item.Items;
import dev.bibo.aqua.listener.InputListener;
import dev.bibo.aqua.listener.LifecycleListener;
import dev.bibo.aqua.user.UserManager;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** AquaPowers — water manipulation abilities granted by Holy Water. */
public final class AquaWaterPlugin extends JavaPlugin {

    private static final int CONFIG_VERSION = 7;

    private final AquaConfig config = new AquaConfig();
    private WaterStyle style;
    private Keys keys;
    private Items items;
    private UserManager users;
    private Animator animator;
    private Forms forms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfOutdated();
        config.reload(getConfig());
        style = WaterStyle.fromConfig(getConfig());

        keys = new Keys(this);
        items = new Items(this);
        users = new UserManager(this);
        animator = new Animator(this);
        forms = new Forms(this);

        animator.start();
        // Stamina regen + awakening decay + boss-bar refresh, once per second.
        getServer().getScheduler().runTaskTimer(this, () -> users.serverTick(), 20L, 20L);

        if (config.enableRecipes) {
            items.registerRecipes();
        }

        getServer().getPluginManager().registerEvents(new InputListener(this), this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this), this);

        AquaCommand cmd = new AquaCommand(this);
        if (getCommand("aqua") != null) {
            getCommand("aqua").setExecutor(cmd);
            getCommand("aqua").setTabCompleter(cmd);
        }

        sweepOrphanDisplays();
        getLogger().info("AquaPowers enabled — " + forms.all().size() + " water forms loaded.");
    }

    @Override
    public void onDisable() {
        if (users != null) {
            users.disperseAll();
            users.shutdown();
        }
        if (animator != null) animator.stopAll();
        sweepOrphanDisplays();
        getLogger().info("AquaPowers disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        config.reload(getConfig());
        style = WaterStyle.fromConfig(getConfig());
    }

    /**
     * Bring an older config.yml up to date <b>without throwing away the owner's settings</b>.
     *
     * <p>This used to overwrite the file wholesale and leave a backup for the owner to re-merge by
     * hand. With forty-odd keys that is a real cost on every version bump, and they had no way to see
     * which ones had actually changed. Now only missing keys are added and the version is bumped; a
     * backup is still written, because a config rewrite should always be undoable.
     */
    private void updateConfigIfOutdated() {
        int onDisk = getConfig().getInt("config-version", 1);
        if (onDisk >= CONFIG_VERSION) return;

        File current = new File(getDataFolder(), "config.yml");
        if (current.exists()) {
            File backup = new File(getDataFolder(), "config-backup-v" + onDisk + ".yml");
            try {
                Files.copy(current.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getLogger().warning("Could not back up old config: " + e.getMessage());
            }
        }

        int added = 0;
        try (InputStream in = getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            FileConfiguration live = getConfig();
            for (String key : bundled.getKeys(true)) {
                if (bundled.isConfigurationSection(key)) continue;
                if (live.contains(key)) continue;
                live.set(key, bundled.get(key));
                added++;
            }
            live.set("config-version", CONFIG_VERSION);
            saveConfig();
        } catch (IOException e) {
            getLogger().warning("Could not merge config defaults: " + e.getMessage());
            return;
        }
        reloadConfig();
        getLogger().info("config.yml updated to v" + CONFIG_VERSION + " — added " + added
                + " new key(s), your existing values were kept.");
    }

    /** Remove any leftover water FX displays (e.g. after a crash or /reload). */
    public void sweepOrphanDisplays() {
        int removed = 0;
        for (World w : getServer().getWorlds()) {
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class)) {
                if (d.getScoreboardTags().contains(Keys.DISPLAY_TAG)) {
                    d.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) getLogger().info("Cleaned " + removed + " leftover water display(s).");
    }

    public AquaConfig cfg() { return config; }
    public WaterStyle style() { return style; }
    public Keys keys() { return keys; }
    public Items items() { return items; }
    public UserManager users() { return users; }
    public Animator animator() { return animator; }
    public Forms forms() { return forms; }
}
