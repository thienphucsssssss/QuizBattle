package vn.edu.hutech.nqb.client;

/** Identical normalization for Preview and the production Server. */
public final class TextKey {
    private TextKey() { }
    public static String of(String text) {
        return vn.edu.hutech.nqb.shared.TextNormalizer.key(text);
    }
}
