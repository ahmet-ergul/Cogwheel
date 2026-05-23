package dev.kima.cogwheel.model;

import net.minecraft.world.item.ItemStack;

/**
 * One input or output slot on a {@link Node}.
 *
 * @param index   Position within the input or output list. Determines render Y position on the node.
 * @param type    Item or fluid.
 * @param label   Human-readable label (display only).
 * @param display Representative stack to show beside the port (icon).
 */
public record Port(int index, PortType type, String label, ItemStack display) {}
