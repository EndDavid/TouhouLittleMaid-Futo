package com.github.tartaricacid.touhoulittlemaid.mixin;

import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * 把原版私有的 {@code PoiType#register} 暴露出来。
 *
 * <p><b>为什么需要它：</b>1.18.2 的 {@link PoiType} <b>没有公开构造器</b>
 * （两个构造器和三个 {@code register} 重载全是 private），也没有 1.19 才引入的
 * {@code PoiTypes} 注册辅助类。整个注册流程里唯一正确的入口就是那个 private
 * static {@code register(String, Set, int, int)} —— 它同时做了三件事：</p>
 * <ol>
 *     <li>{@code Registry.register(POINT_OF_INTEREST_TYPE, ...)}；</li>
 *     <li>调用 {@code registerBlockStates(type)}，把方块状态塞进
 *         {@code TYPE_BY_STATE}（否则 {@code PoiType#forState} 永远查不到，
 *         POI 管理器也不会认这个方块）；</li>
 *     <li>把 {@code name} 当作<b>完整 id</b> 包成 {@code new ResourceLocation(name)}，
 *         所以调用方必须传 {@code touhou_little_maid:maid_bed} 而不是 {@code maid_bed}。</li>
 * </ol>
 *
 * <p>Forge 把这个方法改成了 public（上游因此直接
 * {@code ForgeRegistries.POI_TYPES} + 公开构造器），Fabric 不修改可见性，
 * 所以这里用一个 {@code @Invoker} 静态访问器代替 —— 不改变任何行为。</p>
 */
@Mixin(PoiType.class)
public interface PoiTypeAccessor {
    @Invoker("register")
    static PoiType invokeRegister(String name, Set<BlockState> matchingStates, int maxTickets, int validRange) {
        throw new AssertionError("Mixin invoker was not applied: PoiType#register");
    }
}

