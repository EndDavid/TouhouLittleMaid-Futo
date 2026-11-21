package com.github.tartaricacid.touhoulittlemaid.client.screen.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 「朝向」按钮，移植自上游 {@code client/gui/widget/button/DirectButton}。
 *
 * <p>它其实是个自己带状态的四态按钮：每点一次把朝向按
 * {@code 2D 序号 + 1 mod 4} 转 90°，然后回调让外层把新值抄走。</p>
 *
 * <h2>为什么 {@code getMessage()} 要重写</h2>
 * <p>按钮上显示的文字就是「朝向：北/东/南/西」，而 {@code Button} 每次渲染都会调
 * {@link #getMessage()} —— 于是只要在这里按 {@link #direction} 返回对应的
 * {@link TranslatableComponent}，点击后文字就自动跟着变了，不需要额外刷新界面。</p>
 *
 * <p><b>与上游的差异</b>：上游用 {@code Button.builder(...).pos().size().build()}（1.19.3+ 的写法），
 * 1.18.2 只有构造器 {@code Button(int,int,int,int,Component,OnPress)}；
 * 另外 1.18.2 没有 {@code Component.empty()}，用 {@link TextComponent#EMPTY}。</p>
 */
public class DirectButton extends Button {
    private Direction direction;

    public DirectButton(int x, int y, int width, int height, Direction direction, OnPress onPress) {
        super(x, y, width, height, TextComponent.EMPTY, onPress);
        this.direction = direction == null ? Direction.SOUTH : direction;
    }

    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public void onPress() {
        this.direction = Direction.from2DDataValue((this.direction.get2DDataValue() + 1) % 4);
        this.onPress.onPress(this);
    }

    @Override
    public Component getMessage() {
        return new TranslatableComponent(
                "gui.touhou_little_maid.model_switcher.direction." + this.direction.getName());
    }
}

