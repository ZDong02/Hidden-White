package com.hiddenwhite.framework.audit;

public interface OperationLogRecorder {

    void record(OperationLogEntry entry);
}
