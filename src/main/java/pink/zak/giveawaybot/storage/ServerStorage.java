package pink.zak.giveawaybot.storage;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.enums.Setting;
import pink.zak.giveawaybot.lang.enums.Language;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.cache.options.CacheStorage;
import pink.zak.giveawaybot.service.storage.mongo.MongoDeserializer;
import pink.zak.giveawaybot.service.storage.mongo.MongoSerializer;
import pink.zak.giveawaybot.service.storage.mongo.MongoStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerStorage extends MongoStorage<Long, Server> implements CacheStorage<Long, Server> {
    private final GiveawayBot bot;
    private final Gson gson = new Gson();

    public ServerStorage(GiveawayBot bot) {
        super(bot, "server-settings", "_id");
        this.bot = bot;
    }

    @Override
    public MongoSerializer<Server> serializer() {
        return (server, document) -> {
            document.put("_id", server.getId());
            document.put("presets", this.gson.toJson(this.serializePresets(server.getPresets())));
            document.put("activeGiveaways", this.gson.toJson(server.getActiveGiveaways()));
            document.put("managerRoles", this.gson.toJson(server.getManagerRoles()));
            document.put("bannedUsers", this.gson.toJson(server.getBannedUsers()));
            document.put("premium", server.getPremiumExpiry());
            document.put("language", server.getLanguage().toString());
            return document;
        };
    }

    @Override
    public MongoDeserializer<Server> deserializer() {
        return document -> {
            long id = document.getLong("_id");
            Map<String, Preset> presets = this.deserializePresets(id, this.gson.fromJson(document.getString("presets"), new TypeToken<ConcurrentHashMap<String, HashMap<Setting, String>>>(){}.getType()));
            Set<Long> activeGiveaways = Sets.newConcurrentHashSet(this.gson.fromJson(document.getString("activeGiveaways"), new TypeToken<HashSet<Long>>(){}.getType()));
            Set<Long> managerRoles = this.gson.fromJson(document.getString("managerRoles"), new TypeToken<HashSet<Long>>(){}.getType());
            List<Long> bannedUsers = this.gson.fromJson(document.getString("bannedUsers"), new TypeToken<CopyOnWriteArrayList<Long>>(){}.getType());
            long premium = document.getLong("premium");
            Language language = Language.valueOf(document.getString("language"));
            return new Server(this.bot, id, activeGiveaways, presets, managerRoles, bannedUsers, premium, language);
        };
    }

    @Override
    public Server create(Long id) {
        return new Server(this.bot, id);
    }

    private Map<String, EnumMap<Setting, String>> serializePresets(Map<String, Preset> unserialized) {
        Map<String, EnumMap<Setting, String>> serialized = Maps.newHashMap();
        for (Map.Entry<String, Preset> preset : unserialized.entrySet()) {
            serialized.put(preset.getKey(), preset.getValue().serialized());
        }
        return serialized;
    }

    private Map<String, Preset> deserializePresets(long guildId, Map<String, Map<Setting, String>> serialized) {
        if (serialized.isEmpty()) {
            return Maps.newConcurrentMap();
        }
        Guild guild = this.bot.getShardManager().getGuildById(guildId);
        Map<String, Preset> deserialized = Maps.newHashMap();
        for (Map.Entry<String, Map<Setting, String>> entry : serialized.entrySet()) {
            EnumMap<Setting, Object> enumMap = Maps.newEnumMap(Setting.class);
            for (Map.Entry<Setting, String> innerEntry : entry.getValue().entrySet()) {
                Object parsed = innerEntry.getKey().parseAny(innerEntry.getValue(), guild);
                if (parsed == null) {
                    continue;
                }
                enumMap.put(innerEntry.getKey(), parsed);
            }
            deserialized.put(entry.getKey(), new Preset(entry.getKey(), enumMap));
        }
        return deserialized;
    }
}
