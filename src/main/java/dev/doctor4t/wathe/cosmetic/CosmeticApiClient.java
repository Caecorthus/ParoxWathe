package dev.doctor4t.wathe.cosmetic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.item.component.CosmeticComponent;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 皮肤来源层：进服时向自建皮肤服务（ParoxSite）拉取该玩家当前装备的皮肤。
 * <p>GET {@code {BASE}/api/skins/equipped?uuid=<uuid>}；响应含每个物品类别装备的皮肤与
 * 「对应游戏物品ID」（任意模组命名空间）。任何失败都静默降级为空表（= 无皮肤），不影响进服。
 * <p>接口契约与数据流见 {@code docs/skin-system.md}。
 */
public class CosmeticApiClient {
    private static final Gson GSON = new Gson();
    /** 自建皮肤服务地址：与网站同域名（客户端下载贴图用的 URL 也由该服务下发）。改地址需重新构建。 */
    private static final String API_BASE = "https://parox.luoyuewu.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 拉取玩家的装备皮肤；结果按「物品注册 ID → 皮肤组件」映射（可能为空表）。
     * 在后台线程执行，回调不切主线程——调用方自行决定怎么消费。
     */
    public static CompletableFuture<Map<Identifier, CosmeticComponent>> fetchPlayerSkins(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/api/skins/equipped?uuid=" + uuid))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    Wathe.LOGGER.warn("[CosmeticApi] HTTP {}: {}", response.statusCode(), response.body());
                    return Map.<Identifier, CosmeticComponent>of();
                }
                return parseResponse(response.body());
            } catch (Exception e) {
                Wathe.LOGGER.warn("[CosmeticApi] Failed to fetch skins for {}: {}", uuid, e.getMessage());
                return Map.<Identifier, CosmeticComponent>of();
            }
        });
    }

    /**
     * 解析响应： {@code {"success":true,"equipped":[{"item_type","item_ids":[..],"skin":{..}}]}}。
     * 每个 item_id 一条映射；格式不对的 id 跳过，缺皮肤的条目跳过。
     */
    static Map<Identifier, CosmeticComponent> parseResponse(String json) {
        Map<Identifier, CosmeticComponent> result = new HashMap<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return result;
            JsonArray equipped = root.getAsJsonArray("equipped");
            if (equipped == null) return result;

            for (JsonElement element : equipped) {
                JsonObject entry = element.getAsJsonObject();
                JsonObject skin = entry.getAsJsonObject("skin");
                JsonArray itemIds = entry.getAsJsonArray("item_ids");
                if (skin == null || itemIds == null) continue;

                CosmeticComponent component = new CosmeticComponent(
                        skin.get("id").getAsString(),
                        skin.get("name").getAsString(),
                        getString(skin, "rarity_name"),
                        skin.get("image_url").getAsString(),
                        "",
                        getString(skin, "rarity_color")
                );
                for (JsonElement idElement : itemIds) {
                    Identifier itemId = Identifier.tryParse(idElement.getAsString());
                    if (itemId != null) result.put(itemId, component);
                }
            }
        } catch (Exception e) {
            Wathe.LOGGER.warn("[CosmeticApi] Failed to parse response: {}", e.getMessage());
        }
        return result;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }
}
