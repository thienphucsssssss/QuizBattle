package vn.edu.hutech.nqb.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.hutech.nqb.shared.ProximityRules;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static vn.edu.hutech.nqb.server.Wire.*;

/**
 * Dedicated SQLite connection and bounded asynchronous write queue for Wi-Fi proximity monitoring.
 * Write failures are reported to managers and never abort a running match.
 */
public final class ProximityStore implements AutoCloseable {

    public record PlayerSummaryRow(
        String playerId,
        String nickname,
        String avatarId,
        boolean calibrated,
        String adapterName,
        Double pathLossN,
        Double minDistanceM,
        Double maxDistanceM,
        Double lastDistanceM,
        String lastStatus,
        long missingDataMs,
        int overThresholdCount,
        int alertCount,
        String updatedAtUtc
    ) { }

    public record AlertEventRow(
        String sessionId,
        String playerId,
        String nickname,
        String eventType,
        Double distanceM,
        Integer rssiDbm,
        String detail,
        String createdAtUtc
    ) { }

    public record SessionSnapshot(
        String sessionId,
        int hostUserId,
        String roomName,
        String roomCode,
        boolean proximityEnabled,
        String anchorSsid,
        String anchorBssid,
        String anchorBand,
        double thresholdMeters,
        String status,
        String startedAtUtc,
        String updatedAtUtc,
        String endedAtUtc,
        List<PlayerSummaryRow> players,
        List<AlertEventRow> newAlerts
    ) { }

    private final Connection db;
    private final ThreadPoolExecutor writer;
    private final Consumer<String> onWriteFailure;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean simulateWriteFailure;

    public ProximityStore(Path file, int queueCapacity, Consumer<String> onWriteFailure) throws SQLException {
        this.onWriteFailure = onWriteFailure == null ? ignored -> { } : onWriteFailure;
        this.db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (var s = db.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=2000");
            s.execute("PRAGMA journal_mode=WAL");
        } catch (SQLException error) {
            db.close();
            throw error;
        }
        this.writer = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
            Thread.ofPlatform().daemon().name("proximity-db-writer").factory(),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    void setSimulateWriteFailure(boolean simulateWriteFailure) {
        this.simulateWriteFailure = simulateWriteFailure;
    }

    public void enqueueSnapshot(SessionSnapshot snapshot) {
        if (closed.get() || snapshot == null || snapshot.sessionId() == null || snapshot.sessionId().isBlank()) {
            return;
        }
        try {
            writer.execute(() -> {
                try {
                    if (simulateWriteFailure) {
                        throw new SQLException("Simulated proximity write failure");
                    }
                    writeSnapshotSync(snapshot);
                } catch (Exception error) {
                    onWriteFailure.accept("Chưa lưu được nhật ký giám sát. Ván thi vẫn tiếp tục.");
                }
            });
        } catch (RejectedExecutionException full) {
            onWriteFailure.accept("Hàng đợi ghi nhật ký giám sát đang đầy. Ván thi vẫn tiếp tục.");
        }
    }

    public void flush() {
        if (closed.get()) return;
        try {
            CompletableFuture.runAsync(() -> { }, writer).get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private synchronized void writeSnapshotSync(SessionSnapshot snap) throws SQLException {
        db.setAutoCommit(false);
        try {
            try (var ps = db.prepareStatement(
                "INSERT INTO proximity_sessions(session_id,host_user_id,room_name,room_code,proximity_enabled,anchor_ssid,anchor_bssid,anchor_band,threshold_meters,status,started_at_utc,updated_at_utc,ended_at_utc) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(session_id) DO UPDATE SET " +
                "room_name=excluded.room_name,room_code=excluded.room_code,proximity_enabled=excluded.proximity_enabled," +
                "anchor_ssid=excluded.anchor_ssid,anchor_bssid=excluded.anchor_bssid,anchor_band=excluded.anchor_band," +
                "threshold_meters=excluded.threshold_meters,status=excluded.status,updated_at_utc=excluded.updated_at_utc," +
                "ended_at_utc=coalesce(excluded.ended_at_utc,proximity_sessions.ended_at_utc)"
            )) {
                ps.setString(1, snap.sessionId());
                ps.setInt(2, snap.hostUserId());
                ps.setString(3, snap.roomName());
                ps.setString(4, snap.roomCode());
                ps.setInt(5, snap.proximityEnabled() ? 1 : 0);
                ps.setString(6, snap.anchorSsid());
                ps.setString(7, snap.anchorBssid());
                ps.setString(8, snap.anchorBand());
                ps.setDouble(9, snap.thresholdMeters());
                ps.setString(10, snap.status());
                ps.setString(11, snap.startedAtUtc());
                ps.setString(12, snap.updatedAtUtc());
                if (snap.endedAtUtc() != null && !snap.endedAtUtc().isBlank()) {
                    ps.setString(13, snap.endedAtUtc());
                } else {
                    ps.setNull(13, Types.VARCHAR);
                }
                ps.executeUpdate();
            }

            if (snap.players() != null && !snap.players().isEmpty()) {
                try (var ps = db.prepareStatement(
                    "INSERT INTO proximity_player_summaries(session_id,player_id,nickname,avatar_id,calibrated,adapter_name,path_loss_n,min_distance_m,max_distance_m,last_distance_m,last_status,missing_data_ms,over_threshold_count,alert_count,updated_at_utc) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(session_id,player_id) DO UPDATE SET " +
                    "nickname=excluded.nickname,avatar_id=excluded.avatar_id,calibrated=excluded.calibrated," +
                    "adapter_name=excluded.adapter_name,path_loss_n=excluded.path_loss_n," +
                    "min_distance_m=excluded.min_distance_m,max_distance_m=excluded.max_distance_m," +
                    "last_distance_m=excluded.last_distance_m,last_status=excluded.last_status," +
                    "missing_data_ms=excluded.missing_data_ms,over_threshold_count=excluded.over_threshold_count," +
                    "alert_count=excluded.alert_count,updated_at_utc=excluded.updated_at_utc"
                )) {
                    for (PlayerSummaryRow row : snap.players()) {
                        ps.setString(1, snap.sessionId());
                        ps.setString(2, row.playerId());
                        ps.setString(3, row.nickname());
                        ps.setString(4, row.avatarId());
                        ps.setInt(5, row.calibrated() ? 1 : 0);
                        ps.setString(6, row.adapterName() == null ? "" : row.adapterName());
                        setNullableDouble(ps, 7, row.pathLossN());
                        setNullableDouble(ps, 8, row.minDistanceM());
                        setNullableDouble(ps, 9, row.maxDistanceM());
                        setNullableDouble(ps, 10, row.lastDistanceM());
                        ps.setString(11, row.lastStatus());
                        ps.setLong(12, row.missingDataMs());
                        ps.setInt(13, row.overThresholdCount());
                        ps.setInt(14, row.alertCount());
                        ps.setString(15, row.updatedAtUtc());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            if (snap.newAlerts() != null && !snap.newAlerts().isEmpty()) {
                try (var ps = db.prepareStatement(
                    "INSERT INTO proximity_alert_events(session_id,player_id,nickname,event_type,distance_m,rssi_dbm,detail,created_at_utc) " +
                    "VALUES(?,?,?,?,?,?,?,?)"
                )) {
                    for (AlertEventRow alert : snap.newAlerts()) {
                        ps.setString(1, snap.sessionId());
                        ps.setString(2, alert.playerId());
                        ps.setString(3, alert.nickname());
                        ps.setString(4, alert.eventType());
                        setNullableDouble(ps, 5, alert.distanceM());
                        if (alert.rssiDbm() != null) {
                            ps.setInt(6, alert.rssiDbm());
                        } else {
                            ps.setNull(6, Types.INTEGER);
                        }
                        ps.setString(7, alert.detail());
                        ps.setString(8, alert.createdAtUtc());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            db.commit();
        } catch (SQLException error) {
            db.rollback();
            throw error;
        } finally {
            db.setAutoCommit(true);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null && !Double.isNaN(value) && !Double.isInfinite(value)) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.REAL);
        }
    }

    public ObjectNode listHistory(int viewerUserId, boolean isAdmin, JsonNode payload) throws SQLException {
        flush();
        return readHistory(viewerUserId, isAdmin, payload);
    }

    @FunctionalInterface private interface Query { ObjectNode run() throws SQLException; }

    private CompletableFuture<ObjectNode> queryAsync(Query query) {
        var result = new CompletableFuture<ObjectNode>();
        try {
            writer.execute(() -> {
                try { result.complete(query.run()); }
                catch (Exception error) { result.completeExceptionally(error); }
            });
        } catch (RejectedExecutionException busy) {
            result.completeExceptionally(fail("SERVER_BUSY", "Nhật ký đang bận. Hãy thử lại sau."));
        }
        return result;
    }

    public CompletableFuture<ObjectNode> listHistoryAsync(int viewerUserId, boolean isAdmin, JsonNode payload) {
        JsonNode copy = payload.deepCopy();
        return queryAsync(() -> readHistory(viewerUserId, isAdmin, copy));
    }

    public CompletableFuture<ObjectNode> getHistoryDetailAsync(int viewerUserId, boolean isAdmin, String sessionId, int alertPage) {
        return queryAsync(() -> readHistoryDetail(viewerUserId, isAdmin, sessionId, alertPage));
    }

    private ObjectNode readHistory(int viewerUserId, boolean isAdmin, JsonNode payload) throws SQLException {
        synchronized (this) {
            int page = integer(payload, "page", 1, 1, 1_000_000);
            int size = integer(payload, "page_size", 10, 1, 20);
            StringBuilder where = new StringBuilder(" FROM proximity_sessions s JOIN users u ON u.id=s.host_user_id WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (!isAdmin) {
                where.append(" AND s.host_user_id=?");
                args.add(viewerUserId);
            } else if (payload.hasNonNull("host_user_id")) {
                where.append(" AND s.host_user_id=?");
                args.add(integer(payload, "host_user_id", 0, 1, Integer.MAX_VALUE));
            }
            if (payload.hasNonNull("status")) {
                String status = text(payload, "status", 20);
                if (!Set.of("RUNNING", "COMPLETED", "ABORTED", "INTERRUPTED").contains(status)) {
                    throw fail("INVALID_INPUT", "Trạng thái phiên không hợp lệ.");
                }
                where.append(" AND s.status=?");
                args.add(status);
            }
            int total = count("SELECT count(*)" + where, args.toArray());
            args.add(size);
            args.add((page - 1) * size);

            ArrayNode items = array();
            String sql = "SELECT s.*, u.display_name AS host_name, " +
                "(SELECT count(*) FROM proximity_player_summaries p WHERE p.session_id=s.session_id) AS player_count, " +
                "(SELECT count(*) FROM proximity_alert_events a WHERE a.session_id=s.session_id) AS alert_count, " +
                "EXISTS(SELECT 1 FROM matches m WHERE m.session_id=s.session_id) AS has_scoreboard " +
                where + " ORDER BY coalesce(s.ended_at_utc, s.updated_at_utc) DESC, s.session_id LIMIT ? OFFSET ?";
            try (var ps = statement(sql, args.toArray()); var r = ps.executeQuery()) {
                while (r.next()) {
                    String status = r.getString("status");
                    items.add(obj()
                        .put("session_id", r.getString("session_id"))
                        .put("room_name", r.getString("room_name"))
                        .put("room_code", r.getString("room_code"))
                        .put("host_user_id", r.getInt("host_user_id"))
                        .put("host_name", r.getString("host_name"))
                        .put("proximity_enabled", r.getBoolean("proximity_enabled"))
                        .put("anchor_ssid", r.getString("anchor_ssid"))
                        .put("anchor_bssid", r.getString("anchor_bssid"))
                        .put("anchor_band", r.getString("anchor_band"))
                        .put("threshold_meters", (int) Math.round(r.getDouble("threshold_meters")))
                        .put("status", status)
                        .put("status_label", sessionStatusLabel(status))
                        .put("started_at_utc", r.getString("started_at_utc"))
                        .put("updated_at_utc", r.getString("updated_at_utc"))
                        .put("ended_at_utc", r.getString("ended_at_utc") == null ? "" : r.getString("ended_at_utc"))
                        .put("player_count", r.getInt("player_count"))
                        .put("alert_count", r.getInt("alert_count"))
                        .put("has_scoreboard", r.getBoolean("has_scoreboard")));
                }
            }
            return obj()
                .put("page", page)
                .put("page_size", size)
                .put("total", total)
                .put("has_more", (long) page * size < total)
                .set("items", items);
        }
    }

    public ObjectNode getHistoryDetail(int viewerUserId, boolean isAdmin, String sessionId) throws SQLException {
        flush();
        return readHistoryDetail(viewerUserId, isAdmin, sessionId, 1);
    }

    private ObjectNode readHistoryDetail(int viewerUserId, boolean isAdmin, String sessionId, int alertPage) throws SQLException {
        synchronized (this) {
            ObjectNode detail = obj();
            int hostUserId;
            try (var ps = statement(
                "SELECT s.*, u.display_name AS host_name, EXISTS(SELECT 1 FROM matches m WHERE m.session_id=s.session_id) AS has_scoreboard " +
                "FROM proximity_sessions s JOIN users u ON u.id=s.host_user_id WHERE s.session_id=?",
                sessionId
            ); var r = ps.executeQuery()) {
                if (!r.next()) {
                    throw fail("NOT_FOUND", "Không tìm thấy nhật ký giám sát.");
                }
                hostUserId = r.getInt("host_user_id");
                if (!isAdmin && hostUserId != viewerUserId) {
                    throw fail("FORBIDDEN", "Giảng viên chỉ xem được nhật ký giám sát của phòng mình tổ chức.");
                }
                String status = r.getString("status");
                detail.put("session_id", r.getString("session_id"))
                    .put("room_name", r.getString("room_name"))
                    .put("room_code", r.getString("room_code"))
                    .put("host_user_id", hostUserId)
                    .put("host_name", r.getString("host_name"))
                    .put("proximity_enabled", r.getBoolean("proximity_enabled"))
                    .put("anchor_ssid", r.getString("anchor_ssid"))
                    .put("anchor_bssid", r.getString("anchor_bssid"))
                    .put("anchor_band", r.getString("anchor_band"))
                    .put("threshold_meters", (int) Math.round(r.getDouble("threshold_meters")))
                    .put("status", status)
                    .put("status_label", sessionStatusLabel(status))
                    .put("started_at_utc", r.getString("started_at_utc"))
                    .put("updated_at_utc", r.getString("updated_at_utc"))
                    .put("ended_at_utc", r.getString("ended_at_utc") == null ? "" : r.getString("ended_at_utc"))
                    .put("has_scoreboard", r.getBoolean("has_scoreboard"));
            }

            ArrayNode players = array();
            try (var ps = statement("SELECT * FROM proximity_player_summaries WHERE session_id=? ORDER BY nickname, player_id", sessionId);
                 var r = ps.executeQuery()) {
                while (r.next()) {
                    Double minD = getNullableDouble(r, "min_distance_m");
                    Double maxD = getNullableDouble(r, "max_distance_m");
                    Double lastD = getNullableDouble(r, "last_distance_m");
                    Double n = getNullableDouble(r, "path_loss_n");
                    String st = r.getString("last_status");
                    ObjectNode pNode = obj()
                        .put("player_id", r.getString("player_id"))
                        .put("nickname", r.getString("nickname"))
                        .put("avatar_id", r.getString("avatar_id"))
                        .put("calibrated", r.getBoolean("calibrated"))
                        .put("adapter_name", r.getString("adapter_name"))
                        .put("last_status", st)
                        .put("last_status_label", playerStatusLabel(st))
                        .put("missing_data_ms", r.getLong("missing_data_ms"))
                        .put("over_threshold_count", r.getInt("over_threshold_count"))
                        .put("alert_count", r.getInt("alert_count"))
                        .put("updated_at_utc", r.getString("updated_at_utc"))
                        .put("min_distance_label", minD == null ? "—" : ProximityRules.formatEstimatedDistance(minD))
                        .put("max_distance_label", maxD == null ? "—" : ProximityRules.formatEstimatedDistance(maxD))
                        .put("last_distance_label", lastD == null ? "—" : ProximityRules.formatEstimatedDistance(lastD));
                    if (n != null) pNode.put("path_loss_n", n);
                    if (minD != null) pNode.put("min_distance_m", minD);
                    if (maxD != null) pNode.put("max_distance_m", maxD);
                    if (lastD != null) pNode.put("last_distance_m", lastD);
                    players.add(pNode);
                }
            }
            detail.set("players", players);

            ArrayNode alerts = array();
            int totalAlerts = count("SELECT count(*) FROM proximity_alert_events WHERE session_id=?", sessionId);
            int pageSize = 20;
            detail.put("alert_page", alertPage).put("alert_total", totalAlerts)
                .put("alerts_has_more", (long) alertPage * pageSize < totalAlerts);
            try (var ps = statement("SELECT * FROM proximity_alert_events WHERE session_id=? ORDER BY id ASC LIMIT ? OFFSET ?", sessionId, pageSize, (long) (alertPage - 1) * pageSize);
                 var r = ps.executeQuery()) {
                while (r.next()) {
                    Double dist = getNullableDouble(r, "distance_m");
                    int rssi = r.getInt("rssi_dbm");
                    boolean hasRssi = !r.wasNull();
                    String eventType = r.getString("event_type");
                    ObjectNode aNode = obj()
                        .put("id", r.getInt("id"))
                        .put("player_id", r.getString("player_id"))
                        .put("nickname", r.getString("nickname"))
                        .put("event_type", eventType)
                        .put("event_label", alertEventLabel(eventType))
                        .put("distance_label", dist == null ? "—" : ProximityRules.formatEstimatedDistance(dist))
                        .put("detail", r.getString("detail"))
                        .put("created_at_utc", r.getString("created_at_utc"));
                    if (dist != null) aNode.put("distance_m", dist);
                    if (hasRssi) aNode.put("rssi_dbm", rssi);
                    alerts.add(aNode);
                }
            }
            detail.set("alerts", alerts);

            ArrayNode ranking = array();
            if (detail.path("has_scoreboard").asBoolean()) {
                try (var ps = statement("SELECT * FROM match_results WHERE session_id=? ORDER BY rank, player_id", sessionId);
                     var r = ps.executeQuery()) {
                    while (r.next()) {
                        ranking.add(obj()
                            .put("player_id", r.getString("player_id"))
                            .put("nickname", r.getString("nickname"))
                            .put("avatar_id", r.getString("avatar_id"))
                            .put("score", r.getInt("score"))
                            .put("correct_count", r.getInt("correct_count"))
                            .put("rank", r.getInt("rank"))
                            .put("connected", r.getBoolean("connected")));
                    }
                }
            }
            detail.set("ranking", ranking);
            return detail;
        }
    }

    private static Double getNullableDouble(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? null : v;
    }

    private PreparedStatement statement(String sql, Object... values) throws SQLException {
        var s = db.prepareStatement(sql);
        for (int i = 0; i < values.length; i++) {
            s.setObject(i + 1, values[i]);
        }
        return s;
    }

    private int count(String sql, Object... values) throws SQLException {
        try (var s = statement(sql, values); var r = s.executeQuery()) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public static String sessionStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "RUNNING" -> "Đang diễn ra";
            case "COMPLETED" -> "Hoàn tất";
            case "ABORTED" -> "Đã hủy";
            case "INTERRUPTED" -> "Gián đoạn";
            default -> "Không rõ";
        };
    }

    public static String playerStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "MEASURING" -> "Đang đo";
            case "UNCALIBRATED" -> "Chưa hiệu chuẩn";
            case "OVER_THRESHOLD" -> "Vượt ngưỡng";
            case "FLUCTUATING" -> "Tín hiệu dao động";
            default -> "Không có dữ liệu";
        };
    }

    public static String alertEventLabel(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case "OVER_THRESHOLD" -> "Vượt ngưỡng khoảng cách";
            case "THRESHOLD_RECOVERED" -> "Về trong ngưỡng";
            case "DATA_LOST" -> "Mất dữ liệu Wi-Fi";
            case "DATA_RESTORED" -> "Có dữ liệu trở lại";
            case "ANCHOR_MISMATCH" -> "Đổi điểm phát hoặc băng tần";
            default -> "Cảnh báo giám sát";
        };
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(3, TimeUnit.SECONDS)) {
                    writer.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            synchronized (this) {
                try {
                    db.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}
