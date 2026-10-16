package dev.doctor4t.wathe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.cosmetic.CosmeticDataCache;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.component.CosmeticComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow
    @Final
    public PlayerEntity player;

    @Shadow
    @Final
    private List<DefaultedList<ItemStack>> combinedInventory;

    /**
     * 皮肤应用：每 tick 扫一遍背包，没有皮肤的物品按玩家装备打上 {@code wathe:skin} 组件
     * （只增不覆盖——已有皮肤的物品保持不变，捡到的他人物品不会被换皮）。
     * 物品在谁背包里就按谁的皮肤；服务端权威，组件走原版同步。
     */
    @Inject(method = "updateItems", at = @At("HEAD"))
    private void wathe$assignSkinData(CallbackInfo ci) {
        if (this.player.getWorld().isClient) return;   // updateItems 双端都会调用，只处理服务端

        Map<Identifier, CosmeticComponent> playerCosmetics = CosmeticDataCache.getPlayerCosmetics(this.player.getUuid());
        if (playerCosmetics == null) return;

        for (DefaultedList<ItemStack> list : this.combinedInventory) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.isEmpty() || stack.contains(WatheDataComponentTypes.SKIN)) continue;

                CosmeticComponent cosmetic = playerCosmetics.get(Registries.ITEM.getId(stack.getItem()));
                if (cosmetic != null) {
                    stack.set(WatheDataComponentTypes.SKIN, cosmetic);
                }
            }
        }
    }

    @WrapMethod(method = "scrollInHotbar")
    private void wathe$invalid(double scrollAmount, @NotNull Operation<Void> original) {
        int oldSlot = this.player.getInventory().selectedSlot;
        original.call(scrollAmount);
        PlayerPsychoComponent component = PlayerPsychoComponent.KEY.get(this.player);
        if (component.getPsychoTicks() > 0 &&
                (this.player.getInventory().getStack(oldSlot).isOf(WatheItems.BAT)) &&
                (!this.player.getInventory().getStack(this.player.getInventory().selectedSlot).isOf(WatheItems.BAT))
        ) this.player.getInventory().selectedSlot = oldSlot;
    }
}
