package vn.edu.hutech.nqb.client;

import com.fasterxml.jackson.databind.JsonNode;

public final class GatewayException extends RuntimeException {
    private final String code;
    private final JsonNode details;

    public GatewayException(String code, String message, JsonNode details) {
        super(message);
        this.code = code;
        this.details = details;
    }
    public String code() { return code; }
    public JsonNode details() { return details; }
}
