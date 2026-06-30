package com.promptarena.repository;

import com.promptarena.model.Comparison;
import com.promptarena.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonRepository extends JpaRepository<Comparison, String> {

  List<Comparison> findByUserOrderByCreatedAtDesc(User user);

  Optional<Comparison> findByIdAndUser(String id, User user);
}
