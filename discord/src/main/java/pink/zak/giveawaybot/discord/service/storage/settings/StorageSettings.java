package pink.zak.giveawaybot.discord.service.storage.settings;

import com.google.common.collect.Maps;

import java.util.Map;

public class StorageSettings {
    private String address;
    private String prefix;
    private String database;
    private String authDatabase;
    private String username;
    private String password;
    private int maximumPoolSize;
    private int minimumIdle;
    private int maximumLifetime;
    private int connectionTimeout;
    private Map<String, String> properties = Maps.newHashMap();

    public StorageSettings() {
        this.address = "";
        this.prefix = "";
        this.database = "";
        this.username = "";
        this.password = "";
        this.maximumPoolSize = 0;
        this.minimumIdle = 0;
        this.maximumLifetime = 0;
        this.connectionTimeout = 0;
    }

    public String getHost() {
        return this.address.split(":")[0];
    }

    public String getPort() {
        String[] hostAndPort = this.address.split(":");
        return hostAndPort.length > 1 ? hostAndPort[1] : "3306";
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDatabase() {
        return this.database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getAuthDatabase() {
        return this.authDatabase;
    }

    public void setAuthDatabase(String authDatabase) {
        this.authDatabase = authDatabase;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getMaximumPoolSize() {
        return this.maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return this.minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public int getMaximumLifetime() {
        return this.maximumLifetime;
    }

    public void setMaximumLifetime(int maximumLifetime) {
        this.maximumLifetime = maximumLifetime;
    }

    public int getConnectionTimeout() {
        return this.connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Map<String, String> getProperties() {
        return this.properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
