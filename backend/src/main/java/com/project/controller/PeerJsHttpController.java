package com.project.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/peerjs")
public class PeerJsHttpController {

    @GetMapping(value = {"/myapp/id", "/id"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPeerId() {
        String peerId = UUID.randomUUID().toString();
        return ResponseEntity.ok(peerId);
    }
}
