package com.despescar.identityservice.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
    
    List<User> findByRole(Role role);
}