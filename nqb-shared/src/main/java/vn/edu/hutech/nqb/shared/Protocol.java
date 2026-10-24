package vn.edu.hutech.nqb.shared;

/** Shared limits for the LAN protocol. No GUI or database dependencies. */
public final class Protocol {
    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 5050;
    public static final int DISCOVERY_PORT = 5051;
    public static final int MAX_FRAME_BYTES = 64 * 1024;
    public static final int MAX_PLAYERS = 10;

    // --- THÊM CÁC HẰNG SỐ LỖI CHO TÍNH NĂNG RÀNG BUỘC THIẾT BỊ ---
    /** Lỗi: Máy tính này đã được gán cho một tài khoản khác. */
    public static final String ERR_DEVICE_BOUND_TO_OTHER = "DEVICE_BOUND_TO_OTHER";
    
    /** Lỗi: Tài khoản này đã được gán cho một máy tính khác. */
    public static final String ERR_ACCOUNT_BOUND_TO_OTHER = "ACCOUNT_BOUND_TO_OTHER";

    private Protocol() { }
}