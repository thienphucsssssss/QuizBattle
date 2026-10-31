package vn.edu.hutech.nqb.admin;

import com.fasterxml.jackson.databind.JsonNode;

/** Shared labels retain the distinction between assumed and measured calibration parameters. */
final class ApDistanceViews {
    private ApDistanceViews(){}
    static JsonNode value(JsonNode player){return player.has("ap_distance")?player.path("ap_distance"):player;}
    static boolean fresh(JsonNode data,long elapsed){
        long age=data.path("age_ms").asLong(data.path("measurement_age_ms").asLong(0));
        return data.has("estimated_meters")&&data.path("fresh").asBoolean(true)&&age>=0&&age+elapsed<=15000;
    }
    static String distance(JsonNode data,long elapsed){return fresh(data,elapsed)?data.path("distance_label").asText(String.format(java.util.Locale.ROOT,"≈ %.1f m",data.path("estimated_meters").asDouble())):"— m";}
    static String method(JsonNode data){return data.path("method_label").asText("Theo hồ sơ hiệu chuẩn");}
    static String status(JsonNode data,long elapsed){
        if(fresh(data,elapsed))return method(data);
        return switch(data.path("status").asText()){
            case "PERMISSION_DENIED"->"Cần cấp quyền Wi-Fi/vị trí trên máy này";
            case "NO_ADAPTER"->"Không có card Wi-Fi hỗ trợ";
            case "WIFI_DISABLED"->"Wi-Fi đang tắt";
            case "DISCONNECTED"->"Chưa kết nối Wi-Fi";
            case "API_ERROR"->"Không đọc được tín hiệu Wi-Fi";
            case "STALE","CONNECTED"->"Số đo đã cũ";
            default->"Đang chờ tín hiệu Wi-Fi";
        };
    }
    static String summary(String device,JsonNode data,long elapsed){
        return device+" → AP: "+distance(data,elapsed)+" · "+status(data,elapsed)
            +(data.path("bssid").asText().isBlank()?"":" · "+data.path("ssid").asText()+" · "+data.path("bssid").asText()+" · "+data.path("band").asText());
    }
}
