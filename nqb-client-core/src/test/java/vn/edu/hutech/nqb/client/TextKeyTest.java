package vn.edu.hutech.nqb.client;

import org.junit.jupiter.api.Test;
import java.text.Normalizer;
import static org.junit.jupiter.api.Assertions.*;

class TextKeyTest {
    @Test void vietnameseNfcAndUnicodeWhitespace() {
        String decomposed = Normalizer.normalize("  CÂU\u00a0  HỎI\u2009mạng\n", Normalizer.Form.NFD);
        assertEquals("câu hỏi mạng", TextKey.of(decomposed));
    }
    @Test void keepsAccentsAndMeaningfulPunctuation() {
        assertNotEquals(TextKey.of("ma"), TextKey.of("má"));
        assertNotEquals(TextKey.of("a+b"), TextKey.of("ab"));
        assertNotEquals(TextKey.of("127.0.0.1"), TextKey.of("127001"));
    }
    @Test void usesRootLocaleNotMachineLocale() {
        var old = java.util.Locale.getDefault();
        try { java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr")); assertEquals("ip", TextKey.of("IP")); }
        finally { java.util.Locale.setDefault(old); }
    }
}
