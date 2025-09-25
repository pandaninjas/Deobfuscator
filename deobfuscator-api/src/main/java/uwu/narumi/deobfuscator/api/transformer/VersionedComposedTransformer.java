package uwu.narumi.deobfuscator.api.transformer;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class VersionedComposedTransformer extends Transformer {

  private final String version;

  public VersionedComposedTransformer(String version) {
    this.version = version;
  }

  @Override
  protected void transform() {
    Map<String, List<Supplier<Transformer>>> transformers = transformersByVersions();
    if (!transformers.containsKey(version)) {
      throw new IllegalArgumentException(String.format("Version '%s' not found!", version));
    }

    transformers
        .get(version)
        .forEach(transformer -> this.changes.addAndGet(Transformer.transform(transformer, scope(), context())));
  }

  public abstract Map<String, List<Supplier<Transformer>>> transformersByVersions();
}
