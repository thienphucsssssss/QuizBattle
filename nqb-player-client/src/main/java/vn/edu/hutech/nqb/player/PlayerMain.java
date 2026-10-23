package vn.edu.hutech.nqb.player;

import vn.edu.hutech.nqb.admin.ArcadeApplication;

/** Student launcher sharing the arcade design system. */
public final class PlayerMain {
    private PlayerMain() { }

    public static void main(String[] args) {
        ArcadeApplication.launchFor("STUDENT", args);
    }
}
