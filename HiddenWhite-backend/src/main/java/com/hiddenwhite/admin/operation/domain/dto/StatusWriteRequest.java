package com.hiddenwhite.admin.operation.domain.dto;

/** 营业状态切换请求；manualClosed 必填，statusNote 选填（最长 100 字）。 */
public class StatusWriteRequest {

    private Boolean manualClosed;
    private String statusNote;

    public Boolean getManualClosed() { return manualClosed; }
    public void setManualClosed(Boolean manualClosed) { this.manualClosed = manualClosed; }
    public String getStatusNote() { return statusNote; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
}
