package dev.doctor4t.wathe.util;

import com.mojang.datafixers.util.Pair;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 手持不可视道具：玩家手持时其他玩家看不到，由 {@link WatheItemTags#INVISIBLE_IN_HAND} 定义
 */
public final class InvisibleItemUtils {
    private InvisibleItemUtils() {
    }

    public static boolean isInvisibleInHand(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(WatheItemTags.INVISIBLE_IN_HAND);
    }

    /**
     * 服务端发送装备同步包前调用，把玩家手上的不可视道具替换为空手，其他客户端拿不到真实物品
     */
    public static List<Pair<EquipmentSlot, ItemStack>> hideFromEquipmentSync(Entity holder, List<Pair<EquipmentSlot, ItemStack>> equipment) {
        if (!(holder instanceof PlayerEntity)) return equipment;

        List<Pair<EquipmentSlot, ItemStack>> filtered = null;
        for (int i = 0; i < equipment.size(); i++) {
            Pair<EquipmentSlot, ItemStack> pair = equipment.get(i);
            if (pair.getFirst().getType() == EquipmentSlot.Type.HAND && isInvisibleInHand(pair.getSecond())) {
                if (filtered == null) filtered = new ArrayList<>(equipment);
                filtered.set(i, Pair.of(pair.getFirst(), ItemStack.EMPTY));
            }
        }
        return filtered != null ? filtered : equipment;
    }
}
