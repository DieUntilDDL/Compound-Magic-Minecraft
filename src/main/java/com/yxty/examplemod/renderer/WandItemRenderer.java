package com.yxty.examplemod.renderer;

import com.yxty.examplemod.item.WandItem;
import com.yxty.examplemod.model.WandItemModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class WandItemRenderer extends GeoItemRenderer<WandItem> {
    public WandItemRenderer() {
        super(new WandItemModel());
    }

    @Override
    public long getInstanceId(WandItem animatable) {
        long itemInstanceId = super.getInstanceId(animatable);

        // GeckoLib 默认让同一 ItemStack 的手持模型和 GUI 图标共用动画状态。
        // 为非手持场景分配独立实例，避免打开物品栏时 idle/cast 状态互相覆盖。
        return WandItem.isHandRenderPerspective(this.renderPerspective)
                ? itemInstanceId
                : itemInstanceId ^ Long.MIN_VALUE;
    }
}
