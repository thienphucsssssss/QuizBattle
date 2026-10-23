package vn.edu.hutech.nqb.shared;

/** Shared limits for the LAN protocol. No GUI or database dependencies. */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 5050;
    public static final int DISCOVERY_PORT = 5051;
    public static final int MAX_FRAME_BYTES = 64 * 1024;
    public static final int MAX_PLAYERS = 10;

    private Protocol() { }
}
