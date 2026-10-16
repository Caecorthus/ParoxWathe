package dev.doctor4t.wathe.cosmetic;

import dev.doctor4t.wathe.item.component.CosmeticComponent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端皮肤缓存：玩家 uuid → 「物品注册 ID → 皮肤组件」。
 * 进服时由 {@link CosmeticApiClient} 写入，断线清理；背包应用逻辑（PlayerInventoryMixin）只读。
 */
public class CosmeticDataCache {
    private static final ConcurrentHashMap<UUID, Map<Identifier, CosmeticComponent>> cache = new ConcurrentHashMap<>();

    public static @Nullable Map<Identifier, CosmeticComponent> getPlayerCosmetics(UUID player) {
        return cache.get(player);
    }

    public static @Nullable CosmeticComponent getCosmetic(UUID player, Identifier itemId) {
        Map<Identifier, CosmeticComponent> playerCosmetics = cache.get(player);
        return playerCosmetics != null ? playerCosmetics.get(itemId) : null;
    }

    public static void update(UUID player, Map<Identifier, CosmeticComponent> cosmetics) {
        cache.put(player, cosmetics);
    }

    public static void remove(UUID player) {
        cache.remove(player);
    }

    public static void clear() {
        cache.clear();
    }
}
