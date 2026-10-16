package dev.doctor4t.wathe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.GlowInkSacItem;
import net.minecraft.item.InkSacItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 便于修地图：染料、荧光墨囊、墨囊在冒险模式下也能用于告示牌，且无视涂蜡。
 * 对局中存活的参与者仍走原版规则，避免对局内改动地图。
 */
@Mixin(AbstractSignBlock.class)
public abstract class AbstractSignBlockMixin {
    @WrapOperation(method = "onUseWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;canModifyBlocks()Z"))
    private boolean wathe$allowSignDyeInAdventure(PlayerEntity player, Operation<Boolean> original, @Local(argsOnly = true) ItemStack stack) {
        return wathe$canDyeSign(stack, player) || original.call(player);
    }

    @WrapOperation(method = "onUseWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/SignBlockEntity;isWaxed()Z"))
    private boolean wathe$allowSignDyeWhenWaxed(SignBlockEntity sign, Operation<Boolean> original, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) PlayerEntity player) {
        return !wathe$canDyeSign(stack, player) && original.call(sign);
    }

    @Unique
    private static boolean wathe$canDyeSign(ItemStack stack, PlayerEntity player) {
        Item item = stack.getItem();
        boolean signDye = item instanceof DyeItem || item instanceof GlowInkSacItem || item instanceof InkSacItem;
        return signDye && !GameFunctions.isPlayerPlayingAndAlive(player);
    }
}
