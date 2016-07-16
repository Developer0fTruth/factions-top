package net.novucs.ftop;

import com.google.common.collect.ImmutableMap;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Settings {

    private static final int LATEST_VERSION = 1;
    private static final String HEADER = "FactionsTop configuration.\n";

    private final FactionsTopPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Message settings.
    private String headerMessage;
    private String entryMessage;
    private String footerMessage;

    // General settings.
    private List<String> commandAliases;
    private int factionsPerPage;
    private int maxBatchSize;
    private int maxQueueSize;
    private long chunkRecalculateMillis;
    private long batchRecalculateMillis;
    private Map<WorthType, Boolean> enabled;
    private Map<WorthType, Boolean> detailed;
    private Map<RecalculateReason, Boolean> performRecalculate;
    private Map<RecalculateReason, Boolean> bypassRecalculateDelay;
    private Map<EntityType, Double> spawnerPrices;
    private Map<Material, Double> blockPrices;

    public Settings(FactionsTopPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> getCommandAliases() {
        return commandAliases;
    }

    public int getFactionsPerPage() {
        return factionsPerPage;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public long getChunkRecalculateMillis() {
        return chunkRecalculateMillis;
    }

    public long getBatchRecalculateMillis() {
        return batchRecalculateMillis;
    }

    public boolean isEnabled(WorthType worthType) {
        return enabled.getOrDefault(worthType, false);
    }

    public boolean isDetailed(WorthType worthType) {
        return detailed.getOrDefault(worthType, false);
    }

    public boolean isPerformRecalculate(RecalculateReason reason) {
        return performRecalculate.getOrDefault(reason, false);
    }

    public boolean isBypassRecalculateDelay(RecalculateReason reason) {
        return bypassRecalculateDelay.getOrDefault(reason, false);
    }

    public double getSpawnerPrice(EntityType entityType) {
        return spawnerPrices.getOrDefault(entityType, 0d);
    }

    public double getBlockPrice(Material material) {
        return blockPrices.getOrDefault(material, 0d);
    }

    private void set(String path, Object val) {
        config.set(path, val);
    }

    private boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path);
    }

    private int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path);
    }

    private long getLong(String path, long def) {
        config.addDefault(path, def);
        return config.getLong(path);
    }

    private double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path);
    }

    private String getString(String path, String def) {
        config.addDefault(path, def);
        return config.getString(path);
    }

    private <T> List<?> getList(String key, List<T> def) {
        config.addDefault("settings." + key, def);
        return config.getList("settings." + key, config.getList("settings." + key));
    }

    private <E> List<E> getList(String key, List<E> def, Class<E> type) {
        try {
            return castList(type, getList(key, def));
        } catch (ClassCastException e) {
            return def;
        }
    }

    private <E> List<E> castList(Class<? extends E> type, List<?> toCast) throws ClassCastException {
        return toCast.stream().map(type::cast).collect(Collectors.toList());
    }

    private <T extends Enum<T>> Optional<T> parseEnum(Class<T> type, String name) {
        name = name.toUpperCase().replaceAll("\\s+", "_").replaceAll("\\W", "");
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private <T extends Enum<T>> EnumMap<T, Boolean> parseStateMap(Class<T> type, String key, boolean def) {
        EnumMap<T, Boolean> target = new EnumMap<>(type);
        for (String name : config.getConfigurationSection(key).getKeys(false)) {
            // Warn user if unable to parse enum.
            Optional<T> parsed = parseEnum(type, name);
            if (!parsed.isPresent()) {
                plugin.getLogger().warning("Invalid " + type.getSimpleName() + ": " + name);
                continue;
            }

            // Add the parsed enum and value to the target map.
            target.put(parsed.get(), getBoolean(key + "." + name, def));
        }
        return target;
    }

    private <T extends Enum<T>> void addDefaults(Class<T> type, String key, boolean def, List<T> exempt) {
        ConfigurationSection section = config.getConfigurationSection(key);
        for (T target : type.getEnumConstants()) {
            section.addDefault(target.name(), exempt.contains(target) == def);
        }
    }

    private <T extends Enum<T>> EnumMap<T, Double> parsePriceMap(Class<T> type, String key, double def) {
        EnumMap<T, Double> target = new EnumMap<>(type);
        for (String name : config.getConfigurationSection(key).getKeys(false)) {
            // Warn user if unable to parse enum.
            Optional<T> parsed = parseEnum(type, name);
            if (!parsed.isPresent()) {
                plugin.getLogger().warning("Invalid " + type.getSimpleName() + ": " + name);
                continue;
            }

            // Add the parsed enum and value to the target map.
            target.put(parsed.get(), getDouble(key + "." + name, def));
        }
        return target;
    }

    private <T extends Enum<T>> void addDefaults(String key, Map<T, Double> prices) {
        ConfigurationSection section = config.getConfigurationSection(key);
        prices.forEach((type, price) -> section.addDefault(key + "." + type.name(), price));
    }

    private <T extends Enum<T>> EnumMap<T, Double> parseDefPrices(Class<T> type, Map<String, Double> def) {
        EnumMap<T, Double> target = new EnumMap<>(type);
        def.forEach((name, price) -> {
            Optional<T> parsed = parseEnum(type, name);
            if (parsed.isPresent()) {
                target.put(parsed.get(), price);
            }
        });
        return target;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void load() throws IOException, InvalidConfigurationException {
        // Create then load the configuration and file.
        configFile = new File(plugin.getDataFolder() + File.separator + "config.yml");
        configFile.getParentFile().mkdirs();
        configFile.createNewFile();

        config = new YamlConfiguration();
        config.load(configFile);

        // Load all configuration values into memory.
        int version = getInt("config-version", 0);
        commandAliases = getList("settings.command-aliases", Collections.singletonList("f top"), String.class);
        factionsPerPage = getInt("settings.hook-per-page", 8);
        maxBatchSize = getInt("settings.max-batch-size", 10);
        maxQueueSize = getInt("settings.max-queue-size", 200);
        chunkRecalculateMillis = getLong("settings.chunk-recalculate-millis", 120000);
        batchRecalculateMillis = getLong("settings.batch-recalculate-millis", 15000);

        addDefaults(WorthType.class, "settings.enabled", true, Collections.singletonList(WorthType.CHESTS));
        enabled = parseStateMap(WorthType.class, "settings.enabled", false);

        addDefaults(WorthType.class, "settings.detailed", true, Collections.emptyList());
        detailed = parseStateMap(WorthType.class, "settings.detailed", false);

        addDefaults(RecalculateReason.class, "settings.perform-recalculate", true, Collections.emptyList());
        performRecalculate = parseStateMap(RecalculateReason.class, "settings.perform-recalculate", false);

        addDefaults(RecalculateReason.class, "settings.bypass-recalculate-delay", false, Arrays.asList(RecalculateReason.UNLOAD, RecalculateReason.CLAIM));
        bypassRecalculateDelay = parseStateMap(RecalculateReason.class, "settings.bypass-recalculate-delay", false);

        Map<String, Double> prices = ImmutableMap.of(
                "SLIME", 75_000.00,
                "SKELETON", 30_000.00,
                "ZOMBIE", 25_000.00
        );
        addDefaults("settings.spawner-prices", parseDefPrices(EntityType.class, prices));
        spawnerPrices = parsePriceMap(EntityType.class, "settings.spawner-prices", 0);

        prices = ImmutableMap.of(
                "EMERALD_BLOCK", 1_250.00,
                "DIAMOND_BLOCK", 1_000.00,
                "GOLD_BLOCK", 250.00,
                "IRON_BLOCK", 75.00,
                "COAL_BLOCK", 25.00
        );
        addDefaults("settings.block-prices", parseDefPrices(Material.class, prices));
        blockPrices = parsePriceMap(Material.class, "settings.block-prices", 0);

        // Update the configuration file if it is outdated.
        if (version < LATEST_VERSION) {
            // Update header and all config values.
            config.options().header(HEADER);
            config.options().copyDefaults(true);
            set("config-version", LATEST_VERSION);

            // Save the config.
            config.save(configFile);
            plugin.getLogger().info("Configuration file has been successfully updated.");
        }
    }
}