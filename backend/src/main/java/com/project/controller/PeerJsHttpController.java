package com.project.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/peerjs")
public class PeerJsHttpController {

    @GetMapping(value = {"/myapp/id", "/id", "/myapp/{key}/id", "/{key}/id"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getPeerId() {
        String peerId = UUID.randomUUID().toString();
        return ResponseEntity.ok(peerId);
    }

    @GetMapping(value = {"/myapp/peers", "/peers", "/myapp/{key}/peers", "/{key}/peers"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> getPeers() {
        return ResponseEntity.ok(Collections.emptyList());
    }
}

