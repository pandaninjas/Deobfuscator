package uwu.narumi.deobfuscator.core.other.impl.clean.peephole;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.OriginalSourceValue;
import uwu.narumi.deobfuscator.api.asm.InsnContext;
import uwu.narumi.deobfuscator.api.asm.MethodContext;
import uwu.narumi.deobfuscator.api.helper.FramedInstructionsStream;
import uwu.narumi.deobfuscator.api.transformer.Transformer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UselessPopCleanTransformer extends Transformer {

  private final Set<AbstractInsnNode> poppedInsns = ConcurrentHashMap.newKeySet();

  @Override
  protected void transform() throws Exception {
    // Expand DUPs first
    Transformer.transform(ExpandDupsTransformer::new, scope(), context());

    FramedInstructionsStream.of(this)
        .editInstructionsStream(stream -> stream.filter(insn -> insn.getOpcode() == POP || insn.getOpcode() == POP2))
        .forEach(insnContext -> {
          boolean success = tryRemovePop(insnContext);

          if (success) {
            markChange();
          }
        });
  }

  /**
   * Tries to remove pop's source values
   *
   * @param insnContext Instruction context
   * @return If removed
   */
  private boolean tryRemovePop(InsnContext insnContext) {
    AbstractInsnNode insn = insnContext.insn();
    OriginalSourceValue firstValue = insnContext.frame().getStack(insnContext.frame().getStackSize() - 1);

    boolean canPopFirstValue = canPop(firstValue, insnContext.methodContext(), null);

    if (insn.getOpcode() == POP) {
      if (!canPopFirstValue) return false;

      // Pop the value from the stack
      popSourceValue(firstValue, insnContext.methodNode());

      // Remove POP
      insnContext.methodNode().instructions.remove(insn);
      return true;
    } else if (insn.getOpcode() == POP2) {
      if (firstValue.getSize() == 2) {
        if (!canPopFirstValue) return false;

        // Pop 2-sized value from the stack
        popSourceValue(firstValue, insnContext.methodNode());

        // Remove POP
        insnContext.methodNode().instructions.remove(insn);
        return true;
      } else {
        // Pop two values from the stack

        int index = insnContext.frame().getStackSize() - 2;
        if (index < 0) return false;
        OriginalSourceValue secondValue = insnContext.frame().getStack(index);
        if (firstValue.getProducer().getOpcode() == DUP) {
          // Extract the original source value from DUP
          secondValue = Objects.requireNonNull(secondValue.copiedFrom);
        }

        boolean canPopSecondValue = canPop(secondValue, insnContext.methodContext(), firstValue);
        if (canPopFirstValue && canPopSecondValue) {
          // Pop
          popSourceValue(firstValue, insnContext.methodNode());
          popSourceValue(secondValue, insnContext.methodNode());

          // Remove POP2
          insnContext.methodNode().instructions.remove(insn);

          return true;
        } else if (canPopFirstValue || canPopSecondValue) {
          // Only pop one value and replace POP2 with POP
          if (canPopFirstValue) {
            popSourceValue(firstValue, insnContext.methodNode());
          } else {
            popSourceValue(secondValue, insnContext.methodNode());
          }

          // Replace POP2 with POP
          insnContext.methodNode().instructions.set(insn, new InsnNode(POP));

          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if source value can be popped
   *
   * @param sourceValue Source value
   * @param methodContext Method context
   * @param poppedInPair Source value that was popped in pair with this value
   */
  private boolean canPop(OriginalSourceValue sourceValue, MethodContext methodContext, @Nullable OriginalSourceValue poppedInPair) {
    if (sourceValue.insns.isEmpty()) {
      // Nothing to remove. Probably a local variable
      return false;
    }

    // Check if all producers of the source value are constants
    for (AbstractInsnNode producer : sourceValue.insns) {
      // Prevent popping instructions twice (especially DUPs)
      if (poppedInsns.contains(producer)) return false;

      Set<AbstractInsnNode> consumers = methodContext.getConsumersMap().get(producer);
      if (consumers.stream().anyMatch(insn -> insn.getOpcode() != POP &&
          insn.getOpcode() != POP2 && (poppedInPair == null || !poppedInPair.insns.contains(insn)))
      ) {
        // If the value is consumed by another instruction, we can't remove it
        return false;
      }

      // Can be popped if the value is constant
      if (producer.isConstant()) continue;
      // Can be popped if the value is DUP or DUP2
      if (producer.getOpcode() == DUP || producer.getOpcode() == DUP2) continue;
      // Can be popped if value is a local variable
      if (producer.isVarLoad()) continue;

      return false;
    }
    return true;
  }

  private void popSourceValue(OriginalSourceValue value, MethodNode methodNode) {
    for (AbstractInsnNode producer : value.insns) {
      // Prevent popping instructions twice (especially DUPs)
      poppedInsns.add(producer);

      methodNode.instructions.remove(producer);
    }
  }
}
