package com.cuevas.powerspike.repository;

import com.cuevas.powerspike.model.SummonerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SummonerRepository extends JpaRepository<SummonerEntity, String> {

    Optional<SummonerEntity> findByGameNameAndTagLine(String gameName, String tagLine);
}
