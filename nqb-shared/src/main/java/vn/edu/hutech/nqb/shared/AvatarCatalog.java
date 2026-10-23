package vn.edu.hutech.nqb.shared;

import java.util.List;
import java.util.stream.IntStream;

/** Stable wire IDs; artwork is bundled in the JavaFX client, never fetched online. */
public final class AvatarCatalog {
    private static final String[] NAMES = {"Mochi", "Zig", "Bắp", "Mực", "Pip", "Mây", "Bơ", "Nori", "Bim", "Tí", "Milo", "Đốm", "Kiki", "Gạo", "Rin", "Tôm", "Nấm", "Bông", "Mít", "Sumi", "Chíp", "Dâu", "Tép", "Lumi"};
    public record Avatar(String id, String name, int variant) { }
    public static final List<Avatar> ALL = IntStream.range(0, NAMES.length)
        .mapToObj(i -> new Avatar("quizling-" + (i + 1), NAMES[i], i)).toList();
    private AvatarCatalog() { }
    public static Avatar find(String id) {
        return ALL.stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("INVALID_AVATAR"));
    }
}
