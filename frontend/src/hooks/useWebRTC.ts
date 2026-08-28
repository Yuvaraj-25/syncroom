"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import Peer, { MediaConnection } from "peerjs";
import { useRoomStore } from "@/store/useRoomStore";

/**
 * Central WebRTC hook.
 *
 * Accepts optional refs for the caller's desired media state so that
 * visibility-change recovery can restore tracks to the correct
 * enabled/disabled state without fighting a second handler.
 */
export function useWebRTC(
    roomId: string,
    userId: string,
    isMutedRef?: React.MutableRefObject<boolean>,
    isVideoOnRef?: React.MutableRefObject<boolean>,
) {
    const [peer, setPeer] = useState<Peer | null>(null);
    const [localStream, setLocalStream] = useState<MediaStream | null>(null);
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const [remoteStreams, setRemoteStreams] = useState<{ [id: string]: MediaStream }>({});

    const { users, updateUser } = useRoomStore();
    const callsRef = useRef<{ [id: string]: MediaConnection }>({});
    const localStreamRef = useRef<MediaStream | null>(null);
    const isRecoveringRef = useRef(false);
    const userIdRef = useRef(userId);

    // Keep refs in sync
    useEffect(() => {
        localStreamRef.current = localStream;
    }, [localStream]);

    useEffect(() => {
        userIdRef.current = userId;
    }, [userId]);

    // Update global store with local stream when it changes
    useEffect(() => {
        if (localStream && userId) {
            updateUser(userId, { stream: localStream });
        }
    }, [localStream, userId, updateUser]);

    // ── Hot-swap helpers ────────────────────────────────────────────────

    /** Replace a track in every active peer connection */
    const hotSwapTrack = useCallback((freshTrack: MediaStreamTrack) => {
        const kind = freshTrack.kind; // "audio" | "video"
        Object.values(callsRef.current).forEach((call) => {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const sender = (call as any).peerConnection
                ?.getSenders()
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                ?.find((s: any) => s.track?.kind === kind);
            if (sender) sender.replaceTrack(freshTrack);
        });
    }, []);

    // ── ensureVideoTrack ────────────────────────────────────────────────
    const ensureVideoTrack = useCallback(async (): Promise<MediaStreamTrack | null> => {
        const stream = localStreamRef.current;
        if (!stream) return null;

        const existing = stream.getVideoTracks()[0];
        if (existing && existing.readyState === "live") return existing;

        try {
            const freshStream = await navigator.mediaDevices.getUserMedia({ video: true });
            const freshTrack = freshStream.getVideoTracks()[0];

            if (existing) {
                stream.removeTrack(existing);
                try { existing.stop(); } catch { /* already stopped */ }
            }
            stream.addTrack(freshTrack);
            hotSwapTrack(freshTrack);
            return freshTrack;
        } catch (err) {
            console.error("Failed to re-acquire video track:", err);
            return null;
        }
    }, [hotSwapTrack]);

    // ── ensureAudioTrack ────────────────────────────────────────────────
    const ensureAudioTrack = useCallback(async (): Promise<MediaStreamTrack | null> => {
        const stream = localStreamRef.current;
        if (!stream) return null;

        const existing = stream.getAudioTracks()[0];
        if (existing && existing.readyState === "live") return existing;

        try {
            const freshStream = await navigator.mediaDevices.getUserMedia({ audio: true });
            const freshTrack = freshStream.getAudioTracks()[0];

            if (existing) {
                stream.removeTrack(existing);
                try { existing.stop(); } catch { /* */ }
            }
            stream.addTrack(freshTrack);
            hotSwapTrack(freshTrack);
            return freshTrack;
        } catch (err) {
            console.error("Failed to re-acquire audio track:", err);
            return null;
        }
    }, [hotSwapTrack]);

    // ── Unified media recovery ──────────────────────────────────────
    // Single recovery function used by both visibility-change and
    // track-ended handlers.  Serialized via isRecoveringRef.
    //
    // KEY BEHAVIOUR: We ALWAYS keep the hardware track alive (readyState=live).
    // User preference (camera on/off) is reflected via track.enabled only.
    // This prevents the browser from truly killing the camera between tabs.
    const recoverMedia = useCallback(async () => {
        if (isRecoveringRef.current) return;
        isRecoveringRef.current = true;

        try {
            const stream = localStreamRef.current;
            if (!stream) return;

            const wantVideo = isVideoOnRef?.current ?? false;
            const wantAudio = !(isMutedRef?.current ?? true);

            // ── Video ──
            // Always ensure a live video track exists in the stream regardless of
            // whether the user wants it on or off.  Only .enabled reflects preference.
            const vTrack = stream.getVideoTracks()[0];
            if (!vTrack || vTrack.readyState === "ended") {
                const fresh = await ensureVideoTrack();
                if (fresh) fresh.enabled = wantVideo;
            } else {
                vTrack.enabled = wantVideo;
            }

            // ── Audio ──
            const aTrack = stream.getAudioTracks()[0];
            if (!aTrack || aTrack.readyState === "ended") {
                const fresh = await ensureAudioTrack();
                if (fresh) fresh.enabled = wantAudio;
            } else {
                aTrack.enabled = wantAudio;
            }

            // ── Push confirmed state into Zustand store ──
            const uid = userIdRef.current;
            if (uid) {
                updateUser(uid, {
                    stream: stream,
                    isVideoOn: wantVideo,
                    isMuted: !wantAudio,
                });
            }
        } finally {
            isRecoveringRef.current = false;
        }
    }, [ensureVideoTrack, ensureAudioTrack, isMutedRef, isVideoOnRef, updateUser]);

    // ── Track-ended listeners ───────────────────────────────────────────
    // Detect when the browser kills a track (e.g. hardware error,
    // permission revocation) and recover immediately.
    useEffect(() => {
        const stream = localStreamRef.current;
        if (!stream) return;

        const onVideoEnded = () => {
            console.log("[useWebRTC] Video track ended — triggering recovery");
            recoverMedia();
        };
        const onAudioEnded = () => {
            console.log("[useWebRTC] Audio track ended — triggering recovery");
            recoverMedia();
        };

        const attachListeners = () => {
            stream.getVideoTracks().forEach(t => t.addEventListener("ended", onVideoEnded));
            stream.getAudioTracks().forEach(t => t.addEventListener("ended", onAudioEnded));
        };

        attachListeners();

        // Also listen for new tracks being added so we can attach listeners
        const onAddTrack = (e: MediaStreamTrackEvent) => {
            if (e.track.kind === "video") e.track.addEventListener("ended", onVideoEnded);
            if (e.track.kind === "audio") e.track.addEventListener("ended", onAudioEnded);
        };
        stream.addEventListener("addtrack", onAddTrack);

        return () => {
            stream.getVideoTracks().forEach(t => t.removeEventListener("ended", onVideoEnded));
            stream.getAudioTracks().forEach(t => t.removeEventListener("ended", onAudioEnded));
            stream.removeEventListener("addtrack", onAddTrack);
        };
    }, [localStream, recoverMedia]);

    // ── PeerJS + getUserMedia initialisation ────────────────────────────
    useEffect(() => {
        if (!userId) return;

        // STUN/TURN configuration for NAT traversal
        const iceServers = [
            { urls: "stun:stun.l.google.com:19302" },
            { urls: "stun:stun1.l.google.com:19302" },
            { urls: "stun:stun2.l.google.com:19302" },
            { urls: "stun:stun3.l.google.com:19302" },
            { urls: "stun:stun4.l.google.com:19302" },
        ];

        const newPeer = new Peer(userId, {
            host: process.env.NEXT_PUBLIC_PEER_HOST || "localhost",
            port: parseInt(process.env.NEXT_PUBLIC_PEER_PORT || "4000"),
            path: "/peerjs/myapp",
            config: { iceServers },
            debug: process.env.NODE_ENV === "development" ? 2 : 0,
        });

        setPeer(newPeer);

        // Set up call handler IMMEDIATELY to avoid race condition
        // where incoming calls arrive before getUserMedia completes
        const setupCallHandler = (stream: MediaStream) => {
            newPeer.on("call", (call: MediaConnection) => {
                call.answer(stream);
                call.on("stream", (userVideoStream: MediaStream) => {
                    setRemoteStreams((prev) => ({
                        ...prev,
                        [call.peer]: userVideoStream,
                    }));
                    updateUser(call.peer, { stream: userVideoStream });
                });
                call.on("error", (err) => {
                    console.error(`[useWebRTC] Call error with ${call.peer}:`, err);
                });
                call.on("close", () => {
                    console.log(`[useWebRTC] Call closed with ${call.peer}`);
                    delete callsRef.current[call.peer];
                    setRemoteStreams((prev) => {
                        const next = { ...prev };
                        delete next[call.peer];
                        return next;
                    });
                    updateUser(call.peer, { stream: undefined });
                });
                callsRef.current[call.peer] = call;
            });
        };

        // Set up call handler immediately with a placeholder stream
        // Will be updated with real stream once getUserMedia resolves
        let pendingStream: MediaStream | null = null;
        const callHandlerWrapper = (call: MediaConnection) => {
            if (pendingStream) {
                call.answer(pendingStream);
            } else {
                // Queue the call to answer once stream is ready
                const answerWhenReady = () => {
                    if (pendingStream) {
                        call.answer(pendingStream);
                        call.removeListener("error", answerWhenReady);
                    }
                };
                call.on("error", answerWhenReady);
            }
            call.on("stream", (userVideoStream: MediaStream) => {
                setRemoteStreams((prev) => ({
                    ...prev,
                    [call.peer]: userVideoStream,
                }));
                updateUser(call.peer, { stream: userVideoStream });
            });
            call.on("error", (err) => {
                console.error(`[useWebRTC] Call error with ${call.peer}:`, err);
            });
            call.on("close", () => {
                console.log(`[useWebRTC] Call closed with ${call.peer}`);
                delete callsRef.current[call.peer];
                setRemoteStreams((prev) => {
                    const next = { ...prev };
                    delete next[call.peer];
                    return next;
                });
                updateUser(call.peer, { stream: undefined });
            });
            callsRef.current[call.peer] = call;
        };
        newPeer.on("call", callHandlerWrapper);

        // PeerJS connection events
        newPeer.on("open", (id) => {
            console.log(`[useWebRTC] PeerJS connected with ID: ${id}`);
        });
        newPeer.on("error", (err) => {
            console.error("[useWebRTC] PeerJS error:", err);
        });
        newPeer.on("disconnected", () => {
            console.warn("[useWebRTC] PeerJS disconnected, attempting reconnect...");
            newPeer.reconnect();
        });
        newPeer.on("close", () => {
            console.log("[useWebRTC] PeerJS connection closed");
        });

        // Request both audio + video upfront so PeerJS has tracks to share.
        // BOTH video and audio tracks are disabled by default —
        // video: user toggles it on explicitly
        // audio: mic starts muted for better UX
        navigator.mediaDevices.getUserMedia({ video: true, audio: true })
            .then((stream) => {
                stream.getVideoTracks().forEach(t => t.enabled = false);
                stream.getAudioTracks().forEach(t => t.enabled = false);
                pendingStream = stream;
                setLocalStream(stream);
                localStreamRef.current = stream;
                // Replace the wrapper with the real handler now that we have a stream
                newPeer.off("call", callHandlerWrapper);
                setupCallHandler(stream);
            })
            .catch((err) => {
                console.warn("getUserMedia (video+audio) failed, trying audio-only:", err);
                navigator.mediaDevices.getUserMedia({ audio: true })
                    .then((audioStream) => {
                        audioStream.getAudioTracks().forEach(t => t.enabled = false);
                        pendingStream = audioStream;
                        setLocalStream(audioStream);
                        localStreamRef.current = audioStream;
                        newPeer.off("call", callHandlerWrapper);
                        setupCallHandler(audioStream);
                    })
                    .catch((audioErr) => {
                        console.error("getUserMedia (audio-only) also failed:", audioErr);
                        pendingStream = null;
                    });
            });

        return () => {
            newPeer.off("call", callHandlerWrapper);
            newPeer.destroy();
            // We intentionally DO NOT stop the tracks here to prevent
            // component rerenders or StrictMode from killing the persistent camera feed.
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [userId, roomId]);

    // Call new users when they join
    useEffect(() => {
        if (!peer || !localStream) return;

        users.forEach((user) => {
            if (user.id !== userId && !callsRef.current[user.id]) {
                const call = peer.call(user.id, localStream);
                if (call) {
                    call.on("stream", (userVideoStream: MediaStream) => {
                        setRemoteStreams((prev) => ({
                            ...prev,
                            [user.id]: userVideoStream,
                        }));
                        updateUser(user.id, { stream: userVideoStream });
                    });
                    callsRef.current[user.id] = call;
                }
            }
        });
    }, [users, peer, localStream, userId, updateUser]);

    const toggleScreenShare = async (isSharing: boolean, onStop?: () => void) => {
        const stream = localStreamRef.current;
        if (!stream) return false;

        if (isSharing) {
            try {
                const displayStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: true });
                const screenVideoTrack = displayStream.getVideoTracks()[0];

                hotSwapTrack(screenVideoTrack);

                const localVideoTrack = stream.getVideoTracks()[0];
                if (localVideoTrack) {
                    stream.removeTrack(localVideoTrack);
                    localVideoTrack.stop();
                }
                stream.addTrack(screenVideoTrack);

                screenVideoTrack.onended = () => {
                    toggleScreenShare(false);
                    if (onStop) onStop();
                };

                return true;
            } catch (error) {
                console.error("Screen sharing error:", error);
                return false;
            }
        } else {
            try {
                const cameraStream = await navigator.mediaDevices.getUserMedia({ video: true });
                const cameraVideoTrack = cameraStream.getVideoTracks()[0];

                hotSwapTrack(cameraVideoTrack);

                const oldTrack = stream.getVideoTracks()[0];
                if (oldTrack) {
                    stream.removeTrack(oldTrack);
                    oldTrack.stop();
                }
                stream.addTrack(cameraVideoTrack);

                return false;
            } catch (error) {
                console.error("Camera restore error:", error);
                return false;
            }
        }
    };

    return { localStream, remoteStreams, toggleScreenShare, ensureVideoTrack, recoverMedia };
}
