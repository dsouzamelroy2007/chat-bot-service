package com.mel.cb.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFactRepository extends JpaRepository<UserFact, Long> {

  List<UserFact> findByUserId(String userId);

}
