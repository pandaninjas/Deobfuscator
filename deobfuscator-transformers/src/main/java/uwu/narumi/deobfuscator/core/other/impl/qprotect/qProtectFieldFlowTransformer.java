package uwu.narumi.deobfuscator.core.other.impl.qprotect;

import org.objectweb.asm.tree.FieldInsnNode;
import uwu.narumi.deobfuscator.api.asm.MethodContext;
import uwu.narumi.deobfuscator.api.asm.matcher.Match;
import uwu.narumi.deobfuscator.api.asm.matcher.group.SequenceMatch;
import uwu.narumi.deobfuscator.api.asm.matcher.impl.NumberMatch;
import uwu.narumi.deobfuscator.api.asm.matcher.impl.OpcodeMatch;
import uwu.narumi.deobfuscator.api.transformer.Transformer;

public class qProtectFieldFlowTransformer extends Transformer {
  private static final Match FIELD_FLOW_PATTERN = SequenceMatch.of(
      // Init variable through field
      NumberMatch.of(),
      OpcodeMatch.of(PUTSTATIC).capture("field"),
      // Compare
      NumberMatch.of(),
      OpcodeMatch.of(GETSTATIC),
      Match.of(ctx -> ctx.insn().isCompare())
  );

  @Override
  protected void transform() throws Exception {
    scopedClasses().parallelStream().forEach(classWrapper -> {
      classWrapper.methods().parallelStream().forEach(methodNode -> {
        MethodContext methodContext = MethodContext.of(classWrapper, methodNode);
        FIELD_FLOW_PATTERN.findAllMatches(methodContext).forEach(match -> {
          FieldInsnNode fieldInsn = (FieldInsnNode) match.captures().get("field").insn();

          // Remove field flow
          match.removeAll();

          markChange();
        });
      });
    });
  }
}
