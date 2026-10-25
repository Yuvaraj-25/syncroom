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
    const mediaRequestRef = useRef<Promise<MediaStream> | null>(null);
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
            const connection = (call as any).peerConnection as RTCPeerConnection | undefined;
            const sender = connection
                ?.getSenders()
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                ?.find((s: any) => s.track?.kind === kind);
            if (sender) {
                sender.replaceTrack(freshTrack).catch((error: unknown) => {
                    console.error(`Failed to replace ${kind} track:`, error);
                });
            } else if (connection && localStreamRef.current) {
                connection.addTrack(freshTrack, localStreamRef.current);
            }
        });
    }, []);

    const ensureTrack = useCallback(async (kind: "audio" | "video"): Promise<MediaStreamTrack | null> => {
        let stream = localStreamRef.current;
        let existing = stream?.getTracks().find(track => track.kind === kind);
        if (existing && existing.readyState === "live") return existing;

        try {
            let capturedStream: MediaStream | null = null;
            if (mediaRequestRef.current) {
                try {
                    capturedStream = await mediaRequestRef.current;
                } catch {
                    // A failed combined request can still succeed for one device on its own.
                }
            }

            stream = localStreamRef.current;
            existing = stream?.getTracks().find(track => track.kind === kind);
            if (existing && existing.readyState === "live") return existing;

            let freshTrack = capturedStream?.getTracks().find(track => track.kind === kind);
            if (!freshTrack) {
                const request = navigator.mediaDevices.getUserMedia(
                    kind === "audio" ? { audio: true } : { video: true }
                );
                mediaRequestRef.current = request;
                try {
                    capturedStream = await request;
                } finally {
                    if (mediaRequestRef.current === request) mediaRequestRef.current = null;
                }
                freshTrack = capturedStream.getTracks().find(track => track.kind === kind);
            }
            if (!freshTrack) return null;

            stream = localStreamRef.current;
            existing = stream?.getTracks().find(track => track.kind === kind);
            if (existing) {
                stream?.removeTrack(existing);
                existing.stop();
            }
            if (!stream) {
                stream = new MediaStream();
                localStreamRef.current = stream;
                setLocalStream(stream);
            }
            if (!stream.getTracks().includes(freshTrack)) {
                stream.addTrack(freshTrack);
                hotSwapTrack(freshTrack);
            }
            return freshTrack;
        } catch (err) {
            console.error(`Failed to acquire ${kind} track:`, err);
            return null;
        }
    }, [hotSwapTrack]);

    const ensureVideoTrack = useCallback(() => ensureTrack("video"), [ensureTrack]);
    const ensureAudioTrack = useCallback(() => ensureTrack("audio"), [ensureTrack]);

    const disableVideoTrack = useCallback(async () => {
        const stream = localStreamRef.current;
        const track = stream?.getVideoTracks()[0];
        if (track) {
            stream?.removeTrack(track);
            track.stop();
        }

        const replacements = Object.values(callsRef.current).map((call) => {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const connection = (call as any).peerConnection as RTCPeerConnection | undefined;
            const sender = connection?.getSenders().find((item) => item.track?.kind === "video");
            return sender ? sender.replaceTrack(null) : Promise.resolve();
        });
        await Promise.all(replacements);
    }, []);

    // ── Unified media recovery ──────────────────────────────────────
    // Single recovery function used by both visibility-change and
    // track-ended handlers.  Serialized via isRecoveringRef.
    //
    // Restore media only when the user has enabled it. A disabled video track
    // still keeps the camera hardware active in many browsers.
    const recoverMedia = useCallback(async () => {
        if (isRecoveringRef.current) return;
        isRecoveringRef.current = true;

        try {
            const stream = localStreamRef.current;
            if (!stream) return;

            const wantVideo = isVideoOnRef?.current ?? false;
            const wantAudio = !(isMutedRef?.current ?? true);

            // ── Video ──
            const vTrack = stream.getVideoTracks()[0];
            if (wantVideo) {
                if (!vTrack || vTrack.readyState === "ended") {
                    const fresh = await ensureVideoTrack();
                    if (fresh) fresh.enabled = true;
                } else {
                    vTrack.enabled = true;
                }
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

        let disposed = false;
        let pendingStream: MediaStream | null = null;
        const pendingCalls: MediaConnection[] = [];
        callsRef.current = {};
        localStreamRef.current = null;
        setLocalStream(null);
        setRemoteStreams({});

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
            secure: window.location.protocol === "https:",
            config: { iceServers },
            debug: process.env.NODE_ENV === "development" ? 2 : 0,
        });

        setPeer(newPeer);

        const registerCall = (call: MediaConnection) => {
            callsRef.current[call.peer] = call;
            call.on("stream", (remoteStream: MediaStream) => {
                setRemoteStreams((prev) => ({ ...prev, [call.peer]: remoteStream }));
                updateUser(call.peer, { stream: remoteStream });
            });
            call.on("error", (err) => {
                console.error(`[useWebRTC] Call error with ${call.peer}:`, err);
            });
            call.on("close", () => {
                if (callsRef.current[call.peer] === call) {
                    delete callsRef.current[call.peer];
                }
                setRemoteStreams((prev) => {
                    const next = { ...prev };
                    delete next[call.peer];
                    return next;
                });
                updateUser(call.peer, { stream: undefined });
            });
        };

        const handleIncomingCall = (call: MediaConnection) => {
            if (callsRef.current[call.peer]) {
                call.close();
                return;
            }
            registerCall(call);
            if (pendingStream) {
                call.answer(pendingStream);
            } else {
                pendingCalls.push(call);
            }
        };
        newPeer.on("call", handleIncomingCall);

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

        // Request audio on room entry, but only request camera after the user
        // explicitly turns video on. Disabled video tracks can still light the
        // camera indicator, so do not acquire one preemptively.
        const setUpMedia = (stream: MediaStream) => {
            if (disposed) {
                stream.getTracks().forEach((track) => track.stop());
                return;
            }
            stream.getTracks().forEach((track) => { track.enabled = false; });
            pendingStream = stream;
            localStreamRef.current = stream;
            setLocalStream(stream);
            pendingCalls.splice(0).forEach((call) => {
                if (callsRef.current[call.peer] === call) call.answer(stream);
            });
        };

        if (!navigator.mediaDevices?.getUserMedia) {
            console.error("Camera and microphone access require HTTPS or localhost.");
            return () => {
                disposed = true;
                newPeer.destroy();
            };
        }

        const initialMediaRequest = navigator.mediaDevices.getUserMedia({ audio: true });
        mediaRequestRef.current = initialMediaRequest;
        initialMediaRequest
            .then((stream) => {
                setUpMedia(stream);
            })
            .catch((err) => {
                console.error("Could not access the microphone:", err);
            })
            .finally(() => {
                if (mediaRequestRef.current === initialMediaRequest) mediaRequestRef.current = null;
            });

        return () => {
            disposed = true;
            newPeer.off("call", handleIncomingCall);
            newPeer.destroy();
            pendingStream?.getTracks().forEach((track) => track.stop());
            if (localStreamRef.current === pendingStream) {
                localStreamRef.current = null;
            }
            callsRef.current = {};
            mediaRequestRef.current = null;
            setPeer((currentPeer) => currentPeer === newPeer ? null : currentPeer);
            setLocalStream((currentStream) => currentStream === pendingStream ? null : currentStream);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [userId, roomId]);

    // Call new users when they join
    useEffect(() => {
        if (!peer || !localStream) return;

        users.forEach((user) => {
            if (user.id !== userId && userId.localeCompare(user.id) < 0 && !callsRef.current[user.id]) {
                const call = peer.call(user.id, localStream);
                if (call) {
                    callsRef.current[user.id] = call;
                    call.on("stream", (remoteStream: MediaStream) => {
                        setRemoteStreams((prev) => ({ ...prev, [user.id]: remoteStream }));
                        updateUser(user.id, { stream: remoteStream });
                    });
                    call.on("error", (err) => {
                        console.error(`[useWebRTC] Call error with ${user.id}:`, err);
                    });
                    call.on("close", () => {
                        if (callsRef.current[user.id] === call) delete callsRef.current[user.id];
                        setRemoteStreams((prev) => {
                            const next = { ...prev };
                            delete next[user.id];
                            return next;
                        });
                        updateUser(user.id, { stream: undefined });
                    });
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
            const oldTrack = stream.getVideoTracks()[0];
            if (!isVideoOnRef?.current) {
                if (oldTrack) {
                    stream.removeTrack(oldTrack);
                    oldTrack.stop();
                }
                Object.values(callsRef.current).forEach((call) => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const connection = (call as any).peerConnection as RTCPeerConnection | undefined;
                    const sender = connection?.getSenders().find((item) => item.track?.kind === "video");
                    if (sender) {
                        sender.replaceTrack(null).catch((error: unknown) => {
                            console.error("Failed to stop sending screen video:", error);
                        });
                    }
                });
                return false;
            }

            try {
                const cameraStream = await navigator.mediaDevices.getUserMedia({ video: true });
                const cameraVideoTrack = cameraStream.getVideoTracks()[0];

                hotSwapTrack(cameraVideoTrack);

                if (oldTrack) {
                    stream.removeTrack(oldTrack);
                    oldTrack.stop();
                }
                cameraVideoTrack.enabled = true;
                stream.addTrack(cameraVideoTrack);

                return false;
            } catch (error) {
                console.error("Camera restore error:", error);
                return false;
            }
        }
    };

    return { localStream, remoteStreams, toggleScreenShare, ensureAudioTrack, ensureVideoTrack, disableVideoTrack, recoverMedia };
}
