package dev.inkhome;

import dev.inkhome.folio.Glyph;

/** Compatibility name for screens being migrated to Folio. */
final class Icons extends Glyph {
    Icons(String name) { super(name); }
    Icons(String name, int level) { super(name, level); }
}
