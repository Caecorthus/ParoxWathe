package dev.doctor4t.wathe.mixin;

import com.mojang.datafixers.util.Pair;
import dev.doctor4t.wathe.util.InvisibleItemUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

@Mixin(EntityTrackerEntry.class)
public class EntityTrackerEntryMixin {
    @Shadow
    @Final
    private Entity entity;

    // 玩家进入追踪范围时的初始装备同步同样隐藏手持不可视道具
    @ModifyArg(method = "sendPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/EntityEquipmentUpdateS2CPacket;<init>(ILjava/util/List;)V"), index = 1)
    private List<Pair<EquipmentSlot, ItemStack>> wathe$hideInvisibleHeldItems(List<Pair<EquipmentSlot, ItemStack>> equipment) {
        return InvisibleItemUtils.hideFromEquipmentSync(this.entity, equipment);
    }
}
