package dev.macepilot.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Atomic, versioned properties configuration. Unknown future keys are ignored. */
public final class ConfigManager {
    private final Path path;
    private final Settings settings = new Settings();
    public ConfigManager(Path path) { this.path = path; }
    public Settings settings() { return settings; }

    public void load() throws IOException {
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        for (var f : Settings.class.getFields()) {
            String raw = p.getProperty(f.getName());
            if (raw == null) continue;
            try {
                if (f.getType() == boolean.class) {
                    if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) f.setBoolean(settings, Boolean.parseBoolean(raw));
                } else if (f.getType() == int.class) f.setInt(settings, Integer.parseInt(raw));
                else if (f.getType() == double.class) f.setDouble(settings, Double.parseDouble(raw));
                else if (f.getType() == String.class) f.set(settings, raw);
                else if (f.getType().isEnum()) {
                    for (Object v : f.getType().getEnumConstants()) if (((Enum<?>)v).name().equals(raw)) f.set(settings, v);
                }
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            catch (NumberFormatException ignored) { /* Keep the default for corrupt fields. */ }
        }
        settings.normalize();
    }

    public void save() throws IOException {
        settings.normalize();
        Properties p = new Properties();
        p.setProperty("formatVersion", "1");
        for (var f : Settings.class.getFields()) {
            try {
                Object v = f.get(settings);
                p.setProperty(f.getName(), v instanceof Enum<?> e ? e.name() : v.toString());
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "macepilot-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary)) { p.store(writer, "MacePilot settings. Health is HP; timing is ticks unless stated."); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public void reset(String category) {
        Settings defaults = new Settings();
        for (var f : Settings.class.getFields()) {
            Option option = f.getAnnotation(Option.class);
            if (category == null || option != null && option.category().equals(category)) {
                try { f.set(settings, f.get(defaults)); }
                catch (IllegalAccessException e) { throw new IllegalStateException(e); }
            }
        }
    }
}

