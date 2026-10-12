package com.project.repository;

import com.project.model.RoomMediaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMediaStateRepository extends JpaRepository<RoomMediaState, String> {
}
