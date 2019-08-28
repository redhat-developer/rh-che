package com.redhat.che.start_workspace_reporter.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JSONRPCResponse {

    private String jsonrpc;
    private JsonElement result;
    private JsonObject error;
    private Integer id;

    public JSONRPCResponse(){}

    public JSONRPCResponse(String jsonrpc, JsonElement result, JsonObject error, Integer id) {
        this.jsonrpc = jsonrpc;
        this.result = result;
        this.error = error;
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public JsonElement getResult() {
        return result;
    }

    public void setResult(JsonElement result) {
        this.result = result;
    }

    public JsonObject getError() {
        return error;
    }

    public void setError(JsonObject error) {
        this.error = error;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Override
    public String toString() {
        StringBuilder response = new StringBuilder();
        response.append("{");
        response.append("\"jsonrpc\":\"").append(this.jsonrpc).append("\",");
        if (this.result != null) response.append("\"result\":").append(this.result.toString()).append(",");
        if (this.error != null) response.append("\"error\":").append(this.error.toString()).append(",");
        response.append("\"id\":").append(this.id);
        response.append("}");
        return response.toString();
    }
}
