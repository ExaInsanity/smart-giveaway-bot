package pink.zak.giveawaybot.discord.service.storage.adapter;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public interface Adapter<T> extends JsonSerializer<T>, JsonDeserializer<T> {

}
