package com.project.repository;

import com.project.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByRoomIdOrderByCreatedAtAsc(String roomId);
    List<Message> findTop500ByRoomIdOrderByCreatedAtDesc(String roomId);
    Optional<Message> findFirstByRoomIdAndClientMessageId(String roomId, String clientMessageId);
    List<Message> findTop500ByRoomIdAndUserIdOrderByCreatedAtDesc(String roomId, String userId);
    void deleteByRoomId(String roomId);
}
