package com.hiddenwhite.framework.security;

public interface PermissionResolver {

    boolean hasPermission(String username, String permission);
}
