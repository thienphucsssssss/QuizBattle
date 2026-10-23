package vn.edu.hutech.nqb.admin;

/** Plain launcher avoids the Java launcher special-case for Application subclasses. */
public final class AdminMain {
    private AdminMain() { }

    public static void main(String[] args) {
        ArcadeApplication.launchFor("MANAGE", args);
    }
}
