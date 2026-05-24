package dev.kima.cogwheel.model;

/** Distinguishes item-flow ports (count units) from fluid-flow ports (mB units). LOOP ports carry
 *  a control signal (no quantity) and only attach to other LOOP ports — used for LoopNode gating. */
public enum PortType {
    ITEM,
    FLUID,
    LOOP
}
