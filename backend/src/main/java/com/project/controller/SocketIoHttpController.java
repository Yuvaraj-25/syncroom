package com.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/socket.io")
public class SocketIoHttpController {

    private final ObjectMapper objectMapper;

    public SocketIoHttpController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleGet(
            @RequestParam(name = "EIO", defaultValue = "4") String eio,
            @RequestParam(name = "transport", defaultValue = "polling") String transport,
            @RequestParam(name = "sid", required = false) String sid) throws Exception {

        if (sid == null || sid.isBlank()) {
            String newSid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Map<String, Object> openPayload = new HashMap<>();
            openPayload.put("sid", newSid);
            openPayload.put("upgrades", Collections.singletonList("websocket"));
            openPayload.put("pingInterval", 25000);
            openPayload.put("pingTimeout", 20000);
            openPayload.put("maxPayload", 1000000);

            String responseBody = "0" + objectMapper.writeValueAsString(openPayload);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body(responseBody);
        }

        // Return empty polling response or Socket.IO connect
        return ResponseEntity.ok("40{\"sid\":\"" + sid + "\"}");
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handlePost(
            @RequestParam(name = "EIO", defaultValue = "4") String eio,
            @RequestParam(name = "sid", required = false) String sid,
            @RequestBody(required = false) String body) {
        return ResponseEntity.ok("ok");
    }
}
