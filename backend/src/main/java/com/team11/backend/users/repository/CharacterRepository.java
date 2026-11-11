package com.team11.backend.users.repository;

import com.team11.backend.users.domain.Character;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<Character, Long> {

    Optional<Character> findFirstByActiveTrueOrderBySortOrderAsc();
}
