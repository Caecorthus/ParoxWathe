package dev.doctor4t.wathe.client.util;

import dev.doctor4t.ratatouille.util.TextUtils;
import dev.doctor4t.wathe.client.skin.ItemSkinTextureManager;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.component.CosmeticComponent;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class WatheItemTooltips {
    public static final int COOLDOWN_COLOR = 0xC90000;
    public static final int LETTER_COLOR = 0xC5AE8B;
    public static final int REGULAR_TOOLTIP_COLOR = 0x808080;

    // 旧存档 / 未带颜色的皮肤兜底：按 5 档 tag 翻译 + 上色
    private static final Map<String, Integer> RARITY_COLORS = Map.of(
            "WHITE", 0xA8A29E,
            "GREEN", 0x10B981,
            "BLUE", 0x3B82F6,
            "PURPLE", 0xA855F7,
            "ORANGE", 0xFF5E00
    );

    private static final Map<String, String> RARITY_KEYS = Map.of(
            "WHITE", "tip.skin.rarity.white",
            "GREEN", "tip.skin.rarity.green",
            "BLUE", "tip.skin.rarity.blue",
            "PURPLE", "tip.skin.rarity.purple",
            "ORANGE", "tip.skin.rarity.orange"
    );

    /** 解析皮肤服务下发的稀有度颜色（#RRGGBB / RRGGBB / 0xRRGGBB / #RGB）；非法返回 -1。 */
    private static int parseRarityColor(String raw) {
        if (raw == null) return -1;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() == 3) {
            StringBuilder expanded = new StringBuilder();
            for (char c : s.toCharArray()) expanded.append(c).append(c);
            s = expanded.toString();
        }
        if (s.length() != 6) return -1;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static void addTooltips() {
        ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, tooltipList) -> {
            addCooldownText(WatheItems.KNIFE, tooltipList, itemStack);
            addCooldownText(WatheItems.REVOLVER, tooltipList, itemStack);
            addCooldownText(WatheItems.DERRINGER, tooltipList, itemStack);
            addCooldownText(WatheItems.GRENADE, tooltipList, itemStack);
            addCooldownText(WatheItems.LOCKPICK, tooltipList, itemStack);
            addCooldownText(WatheItems.CROWBAR, tooltipList, itemStack);
            addCooldownText(WatheItems.BODY_BAG, tooltipList, itemStack);
            addCooldownText(WatheItems.PSYCHO_MODE, tooltipList, itemStack);
            addCooldownText(WatheItems.BLACKOUT, tooltipList, itemStack);

            addTooltipForItem(WatheItems.KNIFE, itemStack, tooltipList);
            addTooltipForItem(WatheItems.REVOLVER, itemStack, tooltipList);
            addTooltipForItem(WatheItems.DERRINGER, itemStack, tooltipList);
            addTooltipForItem(WatheItems.GRENADE, itemStack, tooltipList);
            addTooltipForItem(WatheItems.PSYCHO_MODE, itemStack, tooltipList);
            addTooltipForItem(WatheItems.POISON_VIAL, itemStack, tooltipList);
            addTooltipForItem(WatheItems.SCORPION, itemStack, tooltipList);
            addTooltipForItem(WatheItems.FIRECRACKER, itemStack, tooltipList);
            addTooltipForItem(WatheItems.LOCKPICK, itemStack, tooltipList);
            addTooltipForItem(WatheItems.CROWBAR, itemStack, tooltipList);
            addTooltipForItem(WatheItems.BODY_BAG, itemStack, tooltipList);
            addTooltipForItem(WatheItems.BLACKOUT, itemStack, tooltipList);
            addTooltipForItem(WatheItems.NOTE, itemStack, tooltipList);

            // Universal skin tooltip for all items with a SKIN component
            CosmeticComponent skin = itemStack.get(WatheDataComponentTypes.SKIN);
            if (skin != null && !"default".equals(skin.cosmeticId())) {
                ItemSkinTextureManager.TextureState state = ItemSkinTextureManager.getInstance().getState(skin.textureUrl());
                if (state == ItemSkinTextureManager.TextureState.LOADING) {
                    tooltipList.add(Text.translatable("tip.skin.loading")
                            .styled(s -> s.withColor(Colors.GRAY)));
                } else if (state == ItemSkinTextureManager.TextureState.FAILED) {
                    tooltipList.add(Text.translatable("tip.skin.failed")
                            .styled(s -> s.withColor(0xFFFF5555)));
                } else {
                    // 稀有度颜色：网站下发的自定义色优先；没有（旧数据/未配置）回退旧的 5 档色
                    int customColor = parseRarityColor(skin.rarityColor());
                    boolean hasCustomColor = customColor != -1;
                    int rarityColor = hasCustomColor
                            ? customColor
                            : RARITY_COLORS.getOrDefault(skin.rarity(), 0xA8A29E);
                    String rawName = skin.displayName();

                    // Only attempt JSON Text parsing if it looks like JSON (object or array);
                    // plain text gets auto-colored with rarity color
                    Text skinName;
                    if (rawName.startsWith("{") || rawName.startsWith("[")) {
                        Text parsed = Text.Serialization.fromJson(rawName, MinecraftClient.getInstance().player.getRegistryManager());
                        skinName = parsed != null ? parsed : Text.literal(rawName).styled(s -> s.withColor(rarityColor));
                    } else {
                        skinName = Text.literal(rawName).styled(s -> s.withColor(rarityColor));
                    }

                    Text line = Text.translatable("tip.skin")
                            .styled(s -> s.withColor(Colors.GRAY))
                            .append(skinName);

                    if (hasCustomColor) {
                        // 网站稀有度：名称原样显示（如「不太常见」），不上翻译键
                        if (!skin.rarity().isEmpty()) {
                            line = line.copy()
                                    .append(Text.literal(" [").styled(s -> s.withColor(rarityColor)))
                                    .append(Text.literal(skin.rarity()).styled(s -> s.withColor(rarityColor)))
                                    .append(Text.literal("]").styled(s -> s.withColor(rarityColor)));
                        }
                    } else {
                        String rarityKey = RARITY_KEYS.get(skin.rarity());
                        if (rarityKey != null) {
                            line = line.copy()
                                    .append(Text.literal(" [").styled(s -> s.withColor(rarityColor)))
                                    .append(Text.translatable(rarityKey).styled(s -> s.withColor(rarityColor)))
                                    .append(Text.literal("]").styled(s -> s.withColor(rarityColor)));
                        }
                    }

                    tooltipList.add(line);
                }
            }
        });
    }

    private static void addTooltipForItem(Item item, @NotNull ItemStack itemStack, List<Text> tooltipList) {
        if (itemStack.isOf(item)) {
            tooltipList.addAll(TextUtils.getTooltipForItem(item, Style.EMPTY.withColor(REGULAR_TOOLTIP_COLOR)));
        }
    }

    private static void addCooldownText(Item item, List<Text> tooltipList, @NotNull ItemStack itemStack) {
        if (!itemStack.isOf(item)) return;
        ItemCooldownManager itemCooldownManager = MinecraftClient.getInstance().player.getItemCooldownManager();
        if (itemCooldownManager.isCoolingDown(item)) {
            ItemCooldownManager.Entry knifeEntry = itemCooldownManager.entries.get(item);
            int timeLeft = knifeEntry.endTick - itemCooldownManager.tick;
            if (timeLeft > 0) {
                int minutes = (int) Math.floor((double) timeLeft / 1200);
                int seconds = (timeLeft - (minutes * 1200)) / 20;
                String countdown = (minutes > 0 ? minutes + "m" : "") + (seconds > 0 ? seconds + "s" : "");
                tooltipList.add(Text.translatable("tip.cooldown", countdown).withColor(COOLDOWN_COLOR));
            }
        }
    }
}
