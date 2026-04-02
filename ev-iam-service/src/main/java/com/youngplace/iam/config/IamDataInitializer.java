package com.youngplace.iam.config;

import com.youngplace.iam.entity.IamUserEntity;
import com.youngplace.iam.repository.IamUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IamDataInitializer {

    @Bean
    public CommandLineRunner initIamUsers(IamUserRepository iamUserRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            if (iamUserRepository.findByUsername("admin").isPresent()) {
                return;
            }
            IamUserEntity admin = new IamUserEntity();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("123456"));
            admin.setEnabled(true);
            admin.setRoles("ROLE_ADMIN,ROLE_USER");
            admin.setTokenVersion(1);
            iamUserRepository.save(admin);
        };
    }
}
