package vn.edu.hutech.nqb.shared;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() { }
    public static String key(String text) {
        StringBuilder value = new StringBuilder(); boolean gap = false;
        for (int cp : Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC).codePoints().toArray()) {
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) gap = !value.isEmpty();
            else { if (gap) value.append(' '); gap = false; value.appendCodePoint(cp); }
        }
        return Normalizer.normalize(value.toString().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }
}
