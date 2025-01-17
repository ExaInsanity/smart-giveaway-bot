package pink.zak.giveawaybot.discord.cache;

import com.google.common.collect.Maps;
import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.models.User;
import pink.zak.giveawaybot.discord.service.cache.caches.AccessExpiringCache;
import pink.zak.giveawaybot.discord.service.cache.options.CacheStorage;
import pink.zak.giveawaybot.discord.threads.ThreadFunction;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UserCache extends AccessExpiringCache<Long, User> {

    private final Map<String, Long> baseValueMap = Maps.newHashMap();

    public UserCache(GiveawayBot bot, CacheStorage<Long, User> storage, long serverId) {
        super(bot, storage, TimeUnit.MINUTES, 10, TimeUnit.MINUTES, 5);

        this.baseValueMap.put("serverId", serverId);
    }

    @Override
    public CompletableFuture<User> getAsync(Long key, ThreadFunction threadFunction) {
        return CompletableFuture.supplyAsync(() -> {
            User retrieved = this.get(key);
            if (retrieved != null) {
                super.accessTimes.put(key, System.currentTimeMillis());
            }
            return retrieved;
        }, super.threadManager.getAsyncExecutor(threadFunction));
    }

    @Override
    public User get(Long key) {
        return this.getUserSync(key);
    }

    public User getUserSync(long userId) {
        User retrieved = super.cacheMap.get(userId);
        super.hits.incrementAndGet();
        if (retrieved == null) {
            User loaded = this.storage.load(userId, this.getUserValues(userId)).join();
            super.loads.incrementAndGet();
            if (loaded != null) {
                return this.set(userId, loaded);
            }
            return null;
        }
        return retrieved;
    }

    @Override
    public void save(Long key) {
        this.storage.save(this.getUserValues(key), this.cacheMap.get(key));
    }

    @Override
    public void invalidateAll() {
        super.accessTimes.clear();
        for (Long key : this.cacheMap.keySet()) {
            this.invalidate(key);
        }
    }

    private Map<String, Object> getUserValues(long userId) {
        Map<String, Object> userValueMap = Maps.newHashMap(this.baseValueMap);
        userValueMap.put("userId", userId);
        return userValueMap;
    }
}
