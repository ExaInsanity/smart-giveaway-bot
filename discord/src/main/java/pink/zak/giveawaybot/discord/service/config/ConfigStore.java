package pink.zak.giveawaybot.discord.service.config;

import com.google.common.collect.Maps;
import pink.zak.giveawaybot.discord.service.bot.SimpleBot;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class ConfigStore {
    private final SimpleBot bot;
    private final Map<String, Config> configMap = Maps.newHashMap();
    private final Map<String, String> commons = Maps.newHashMap();

    public ConfigStore(SimpleBot bot) {
        this.bot = bot;
    }

    public Map<String, String> commons() {
        return this.commons;
    }

    public Config getConfig(String string) {
        return configMap.get(string);
    }

    public ConfigStore config(String name, BiFunction<Path, String, Path> pathFunc, boolean reloadable) {
        this.configMap.put(name, new Config(this.bot, path -> Paths.get(pathFunc.apply(path, name).toString().concat(".yml")), reloadable));
        return this;
    }

    public ConfigStore common(String id, String config, Function<Config, String> function) {
        this.commons.put(id, function.apply(this.getConfig(config)));
        return this;
    }

    public void forceReload(String string) {
        Config config = this.getConfig(string);
        if (config != null) {
            config.reload();
        }
    }

    public void forceReload(String... configs) {
        for (String config : configs) {
            this.forceReload(config);
        }
    }

    public void reloadReloadableConfigs() {
        for (Config config : this.configMap.values()) {
            if (config.isReloadable()) {
                config.reload();
            }
        }
    }
}
