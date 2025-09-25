package uwu.narumi.deobfuscator.api.asm;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.OriginalSourceValue;
import uwu.narumi.deobfuscator.api.helper.AsmHelper;

import java.util.Set;

/**
 * Instruction context. Holds all information relevant to the current instruction.
 */
public class InsnContext {
  private final AbstractInsnNode insn;
  private final MethodContext methodContext;

  InsnContext(AbstractInsnNode insn, MethodContext methodContext) {
    this.insn = insn;
    this.methodContext = methodContext;
  }

  public InsnContext of(AbstractInsnNode insn) {
    return new InsnContext(insn, this.methodContext);
  }

  @Nullable
  public Frame<OriginalSourceValue> frame() {
    return this.methodContext.frames().get(this.insn);
  }

  public Set<AbstractInsnNode> consumers() {
    return this.methodContext.getConsumersMap().get(this.insn);
  }

  public MethodNode methodNode() {
    return this.methodContext.methodNode();
  }

  /**
   * Current instruction
   */
  public AbstractInsnNode insn() {
    return insn;
  }

  /**
   * Method context
   */
  public MethodContext methodContext() {
    return methodContext;
  }

  public int getConsumedStackValuesCount() {
    return this.insn.getConsumedStackValuesCount(this.frame());
  }

  /**
   * Places POPs instructions before current instruction to remove source values from the stack.
   * This method automatically calculates how many stack values to pop.
   */
  public void placePops() {
    for (int i = 0; i < this.getConsumedStackValuesCount(); i++) {
      int stackValueIdx = frame().getStackSize() - (i + 1);
      OriginalSourceValue sourceValue = frame().getStack(stackValueIdx);

      // Pop
      this.methodNode().instructions.insertBefore(this.insn, AsmHelper.toPop(sourceValue));
    }
  }
}
