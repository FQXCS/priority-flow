package com.sdecloud.priorityflow.auth.init;

import cn.hutool.crypto.digest.BCrypt;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 种子数据初始化：首次启动创建系统管理员账号 admin/admin123（幂等）。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    /** 内置管理员登录名 */
    public static final String ADMIN_USERNAME = "admin";

    /** 内置管理员初始密码 */
    public static final String ADMIN_INIT_PASSWORD = "admin123";

    private final SysUserRepository sysUserRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (sysUserRepository.findByUsername(ADMIN_USERNAME) != null) {
            return;
        }
        SysUserDo admin = new SysUserDo();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPasswordHash(BCrypt.hashpw(ADMIN_INIT_PASSWORD));
        admin.setDisplayName("系统管理员");
        admin.setStatus("0");
        admin.setIsAdmin("1");
        admin.setRemark("系统内置管理员，首次启动自动创建");
        sysUserRepository.save(admin);
        log.info("已创建系统管理员账号：{} / {}", ADMIN_USERNAME, ADMIN_INIT_PASSWORD);
    }
}
