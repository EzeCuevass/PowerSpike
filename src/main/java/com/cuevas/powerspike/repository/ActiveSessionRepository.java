package com.cuevas.powerspike.repository;

import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cuevas.powerspike.model.ActiveSession;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, Long> {

}
