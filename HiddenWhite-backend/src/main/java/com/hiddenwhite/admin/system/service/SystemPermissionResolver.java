package com.hiddenwhite.admin.system.service;

import com.hiddenwhite.framework.security.PermissionResolver;
import com.hiddenwhite.admin.system.mapper.AuthMapper;
import org.springframework.stereotype.Service;

import static com.hiddenwhite.common.domain.Constants.SUPER_ADMIN;

@Service
public class SystemPermissionResolver implements PermissionResolver {

    private final AuthMapper authMapper;

    public SystemPermissionResolver(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    @Override
    public boolean hasPermission(String username, String permission) {
        var user = authMapper.selectUserByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        if (authMapper.selectRoleCodes(user.getId()).contains(SUPER_ADMIN)) {
            return true;
        }
        return authMapper.selectPermissions(user.getId()).contains(permission);
    }
}
