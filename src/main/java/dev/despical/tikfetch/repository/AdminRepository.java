package dev.despical.tikfetch.repository;

import dev.despical.tikfetch.entity.Admin;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByUsernameIgnoreCase(String username);
}
