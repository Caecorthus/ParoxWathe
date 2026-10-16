package dev.doctor4t.wathe.item;

/**
 * 标记允许使用皮肤的物品。渲染管线只认 {@code wathe:skin} 数据组件，不依赖此接口；
 * 是否把它用作发放/选择的限制条件由皮肤来源层决定（见 docs/skin-system.md）。
 */
public interface ItemWithSkin {
}
