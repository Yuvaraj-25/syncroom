package com.project.service;

import com.project.dto.RoomCreateRequest;
import com.project.dto.RoomResponse;

public interface RoomService {
    RoomResponse getRoom(String roomId);
    RoomResponse createRoom(RoomCreateRequest request);
    boolean prepareRoomForJoin(String roomId, String ownerId);
    boolean isRoomCreator(String roomId, String userId);
    boolean closeRoom(String roomId, String ownerId);
}
