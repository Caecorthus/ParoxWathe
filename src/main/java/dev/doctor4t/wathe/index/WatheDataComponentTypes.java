package dev.doctor4t.wathe.index;

import com.mojang.serialization.Codec;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.item.component.CosmeticComponent;
import dev.doctor4t.wathe.item.component.WalkieTalkieComponent;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public interface WatheDataComponentTypes {
    ComponentType<String> POISONER = register("poisoner", stringBuilder -> stringBuilder.codec(Codec.STRING).packetCodec(PacketCodecs.STRING));
    ComponentType<Boolean> USED = register("used", stringBuilder -> stringBuilder.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL));
    ComponentType<WalkieTalkieComponent> WALKIE_TALKIE = register("walkie_talkie", builder -> builder.codec(WalkieTalkieComponent.CODEC).packetCodec(WalkieTalkieComponent.PACKET_CODEC));
    /**
     * 物品皮肤。服务端权威：由服务端写入物品堆叠，随原版组件同步下发（cosmeticId 为 "default" 时视为无皮肤）。
     */
    ComponentType<CosmeticComponent> SKIN = register("skin", builder -> builder.codec(CosmeticComponent.CODEC).packetCodec(CosmeticComponent.PACKET_CODEC));

    private static <T> ComponentType<T> register(String name, @NotNull UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Wathe.id(name), builderOperator.apply(ComponentType.builder()).build());
    }
}
