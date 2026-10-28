"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { useParams, useRouter } from "next/navigation";
import { useSession, signOut } from "next-auth/react";
import { io, Socket } from "socket.io-client";
import { useRoomStore } from "@/store/useRoomStore";
import { Button } from "@/components/ui/button";
import { LogOut, Mic, MicOff, Video, VideoOff, MonitorUp, Users as UsersIcon, MessageSquare, Copy, Check, Play, Share2, Link2, WifiOff, Settings, Hand, Vote, ListVideo, BookOpen, Trash2 } from "lucide-react";
import EmojiReactions from "@/components/EmojiReactions";
import StudyRoomPanel from "@/components/StudyRoomPanel";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
} from "@/components/ui/dialog";
import ChatPanel from "@/components/ChatPanel";
import ParticipantsPanel from "@/components/ParticipantsPanel";
import CameraGrid from "@/components/CameraGrid";
import VideoPlayer from "@/components/VideoPlayer";
import { useWebRTC } from "@/hooks/useWebRTC";
import { useToast } from "@/hooks/use-toast";
import { ToastAction } from "@/components/ui/toast";
import RoomTour from "@/components/RoomTour";
import { useSettingsStore } from "@/store/useSettingsStore";
import SettingsModal from "@/components/SettingsModal";
import HostControls from "@/components/HostControls";
import PollsPanel from "@/components/PollsPanel";
import QueuePanel from "@/components/QueuePanel";

const SOCKET_URL = process.env.NEXT_PUBLIC_SOCKET_URL || "http://localhost:4000";

export default function RoomPage() {
    const { roomId } = useParams();
    const { data: session, status } = useSession();
    const router = useRouter();
    const { toast } = useToast();
    const [socket, setSocket] = useState<Socket | null>(null);
    const [socketId, setSocketId] = useState<string>("");

    // Local Media State
    const [isMuted, setIsMuted] = useState(true);
    const [isVideoOn, setIsVideoOn] = useState(false);
    const [isScreenSharing, setIsScreenSharing] = useState(false);
    const [isHandRaised, setIsHandRaised] = useState(false);
    const [copied, setCopied] = useState(false);
    const [showShareDialog, setShowShareDialog] = useState(false);
    const [copiedLink, setCopiedLink] = useState(false);
    const [showRoomBanner, setShowRoomBanner] = useState(true);
    const [showSettingsModal, setShowSettingsModal] = useState(false);
    const isTogglingCamera = useRef(false);
    const [roomName, setRoomName] = useState("");
    const [isDisconnected, setIsDisconnected] = useState(false);

    // UI Toggles
    const [showSidebar, setShowSidebar] = useState<
    "chat" | "participants" | "polls" | "queue" | "study" | null
>("chat");
    const [queueTab, setQueueTab] = useState<"link" | "search">("link");

    const { users, setUsers, addUser, removeUser, addMessage, setMessages, updateUser, setVideoQueue, setVideoHistory, setPolls, setQuestions, resetRoom } = useRoomStore();
    const { shortcuts } = useSettingsStore();

    useEffect(() => {
        resetRoom();
    }, [roomId, resetRoom]);

    const isOwner = users.find(u => u.id === socket?.id)?.isOwner ?? false;
    const isRoomCreator = users.find(u => u.id === socket?.id)?.isCreator ?? false;

    // Refs to hold the latest toggle state so the WebRTC hook's recovery
    // handler can read them without creating new listener registrations.
    const isMutedRef = useRef(isMuted);
    const isVideoOnRef = useRef(isVideoOn);
    useEffect(() => { isMutedRef.current = isMuted; }, [isMuted]);
    useEffect(() => { isVideoOnRef.current = isVideoOn; }, [isVideoOn]);

    // Start WebRTC connection — pass state refs so recovery restores correct state
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const { localStream, toggleScreenShare, ensureAudioTrack, ensureVideoTrack, disableVideoTrack, refreshMediaConnections } = useWebRTC(
        roomId as string, socketId, isMutedRef, isVideoOnRef, socket
    );

    const localStreamRef = useRef<MediaStream | null>(null);
    useEffect(() => {
        localStreamRef.current = localStream || null;
    }, [localStream]);

    // ── No secondary sync needed ────────────────────────────────────────
    // recoverMedia (in useWebRTC) handles all track recovery on tab return
    // and pushes the confirmed state directly into the Zustand store.
    // The UI (buttons + CameraGrid) reacts to the store, so no extra sync
    // effect is needed here — a secondary handler would race recoverMedia
    // and could falsely report the camera as "off" before recovery completes.

    // ── Set initial store state when we have a socket ID ──
    useEffect(() => {
        if (socketId) {
            updateUser(socketId, { isMuted: isMutedRef.current, isVideoOn: isVideoOnRef.current });
        }
    }, [socketId, updateUser]);

    useEffect(() => {
        if (status === "unauthenticated") {
            router.push("/");
            return;
        }

        if (!session?.user) return;

        let activeSocket: Socket | null = null;

        const connectAndJoin = async () => {
            const finalName = session.user?.name || "Guest";
            const finalImage = session.user?.image || "";

            const newSocket = io(SOCKET_URL, {
                transports: ["websocket"],
                withCredentials: true,
                reconnection: true,
                reconnectionAttempts: Infinity,
                reconnectionDelay: 1000,
                reconnectionDelayMax: 5000,
            });
            activeSocket = newSocket;
            setSocket(newSocket);

            newSocket.on("connect", () => {
                if (newSocket.id) {
                    setSocketId(newSocket.id);
                    newSocket.emit("join-room", {
                        roomId,
                        user: {
                            userId: (session.user as { id?: string }).id || "anonymous",
                            name: finalName,
                            image: finalImage,
                            isMuted: isMutedRef.current,
                            isVideoOn: isVideoOnRef.current,
                        }
                    });

                    // Record recent room in backend
                    if (session.user?.email) {
                        const apiUrl = process.env.NEXT_PUBLIC_SOCKET_URL?.replace(/\/$/, '') || "http://localhost:4000";
                        fetch(`${apiUrl}/api/users/recent-rooms`, {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ email: session.user.email, roomId })
                        }).catch(err => console.error("Failed to record recent room", err));
                    }
                }
            });

            // Socket Listeners
            newSocket.on("room-users", (usersInRoom) => {
                const formattedUsers = usersInRoom.map((u: { socketId: string, user: { name: string, image: string, isMuted: boolean, isVideoOn: boolean } }) => ({
                    id: u.socketId,
                    ...u.user
                }));
                setUsers(formattedUsers);
            });

            newSocket.on("user-joined", ({ socketId, user }) => {
                addUser({ id: socketId, ...user });
                toast({ title: `${user.name || "Someone"} joined`, description: "A new participant entered the room." });
            });

            newSocket.on("user-left", ({ socketId }) => {
                const leftUser = useRoomStore.getState().users.find(u => u.id === socketId);
                removeUser(socketId);
                if (leftUser) {
                    toast({ title: `${leftUser.name} left`, description: "They have left the room." });
                }
            });

            newSocket.on("room-closed", () => {
                toast({
                    title: "Room deleted",
                    description: "The creator closed this room, so it can no longer be joined.",
                });
                newSocket.disconnect();
                router.push("/dashboard");
            });

            newSocket.on("room-delete-error", ({ message }: { message: string }) => {
                toast({ title: "Could not delete room", description: message, variant: "destructive" });
            });

            newSocket.on("chat-message", (message) => {
                addMessage(message);
            });

            newSocket.on("chat-history", ({ messages }: { messages: Parameters<typeof setMessages>[0] }) => {
                setMessages(messages);
            });

            // Room name changes
            newSocket.on("room-name-changed", ({ name }: { name: string }) => {
                setRoomName(name);
            });

            // Video Queue sync
            newSocket.on("queue-updated", ({ queue }: { queue: (string | import("@/store/useRoomStore").VideoInfo)[] }) => {
                setVideoQueue(queue);
            });

            newSocket.on("video-history-updated", ({ history }: { history: import("@/store/useRoomStore").VideoInfo[] }) => {
                setVideoHistory(history);
            });

            // Collaborative Polls & Q&A
            newSocket.on("polls-updated", ({ polls }) => {
                setPolls(polls);
            });

            newSocket.on("qa-updated", ({ questions }) => {
                setQuestions(questions);
            });

            newSocket.on("new-poll", ({ poll }) => {
                toast({
                    title: "🗳️ New Poll Launched!",
                    description: `"${poll.question}" is now active.`,
                    action: (
                        <ToastAction altText="Vote Now" onClick={() => setShowSidebar("polls")}>
                            Vote Now
                        </ToastAction>
                    ),
                });
            });

            // Host Controls
            newSocket.on("force-mute", () => {
                isMutedRef.current = true;
                setIsMuted(true);
                if (localStreamRef.current) {
                    localStreamRef.current.getAudioTracks().forEach(track => track.enabled = false);
                }
                newSocket.emit("toggle-mic", { roomId, userId: newSocket.id, isMuted: true });
                if (newSocket.id) updateUser(newSocket.id, { isMuted: true });
            });

            newSocket.on("hand-toggled", ({ userId, isHandRaised }: { userId: string, isHandRaised: boolean }) => {
                updateUser(userId, { isHandRaised });
            });

            newSocket.on("hands-lowered", () => {
                setIsHandRaised(false);
                useRoomStore.getState().users.forEach(u => updateUser(u.id, { isHandRaised: false }));
            });

            // Auto-reconnect — re-join room after socket reconnects
            newSocket.on("disconnect", () => {
                // Socket disconnects are normal during tab switches;
                // the "Connection Lost" overlay is driven by navigator.onLine
                // (see the useEffect below), NOT by socket events.
            });

            // Sync remote users' camera and mic state changes
            newSocket.on("user-toggled-camera", ({ userId, isVideoOn }: { userId: string; isVideoOn: boolean }) => {
                updateUser(userId, { isVideoOn });
            });

            newSocket.on("user-toggled-mic", ({ userId, isMuted }: { userId: string; isMuted: boolean }) => {
                updateUser(userId, { isMuted });
            });

            // User dynamic profile updates inside the room
            newSocket.on("user-profile-updated", ({ socketId, name, image }) => {
                updateUser(socketId, { name, image });
            });

            newSocket.on("kicked", () => {
                alert("You have been kicked from the room by the owner.");
                router.push("/dashboard");
            });

            newSocket.on("owner-status-update", ({ socketId, isOwner, isPrimaryOwner }: { socketId: string, isOwner: boolean, isPrimaryOwner: boolean }) => {
                updateUser(socketId, { isOwner, isPrimaryOwner });
                if (socketId === newSocket.id) {
                    toast({
                        title: "Owner Access Granted",
                        description: "You now have owner privileges in this room.",
                    });
                }
            });

            newSocket.on("owner-request", ({ socketId, userName }: { socketId: string, userName: string }) => {
                toast({
                    title: "Owner Access Request",
                    description: `${userName} is requesting owner access.`,
                    action: (
                        <ToastAction altText="Accept" onClick={() => newSocket.emit("accept-owner-request", { roomId, targetSocketId: socketId })}>
                            Accept
                        </ToastAction>
                    ),
                });
            });
        };

        connectAndJoin();

        return () => {
            if (activeSocket) {
                activeSocket.disconnect();
            }
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [roomId, session, status, router]);

    // ── Network-based disconnect detection ───────────────────────────────
    // Only show the "Connection Lost" overlay when the actual WiFi/internet
    // goes offline — NOT on brief socket reconnects from tab switching.
    useEffect(() => {
        const goOffline = () => setIsDisconnected(true);
        const goOnline = () => setIsDisconnected(false);

        // Sync initial state
        setIsDisconnected(!navigator.onLine);

        window.addEventListener("offline", goOffline);
        window.addEventListener("online", goOnline);
        return () => {
            window.removeEventListener("offline", goOffline);
            window.removeEventListener("online", goOnline);
        };
    }, []);

    // ── Stabilized toggle handlers (no re-creation on each render) ──────
    const toggleMic = useCallback(async () => {
        const newMuted = !isMutedRef.current;
        let acquiredTrack = false;

        if (newMuted) {
            localStream?.getAudioTracks().forEach(track => { track.enabled = false; });
        } else {
            let track = localStreamRef.current?.getAudioTracks().find(audioTrack => audioTrack.readyState === "live");
            if (!track) {
                track = await ensureAudioTrack() ?? undefined;
                acquiredTrack = Boolean(track);
            }
            if (!track) {
                toast({
                    title: "Microphone unavailable",
                    description: "Allow microphone access in your browser, then try again.",
                    variant: "destructive",
                });
                return;
            }
            track.enabled = true;
        }
        isMutedRef.current = newMuted;
        setIsMuted(newMuted);
        if (socketId) updateUser(socketId, { isMuted: newMuted });
        if (socket) socket.emit("toggle-mic", { roomId, userId: socketId, isMuted: newMuted });
        if (acquiredTrack) refreshMediaConnections();
    }, [localStream, socketId, socket, roomId, updateUser, ensureAudioTrack, toast, refreshMediaConnections]);

    const toggleCamera = useCallback(async () => {
        if (isTogglingCamera.current) return;
        isTogglingCamera.current = true;

        try {
            const newVideoOn = !isVideoOnRef.current;

            if (newVideoOn) {
                let track: MediaStreamTrack | null | undefined = localStreamRef.current?.getVideoTracks().find(videoTrack => videoTrack.readyState === "live");
                if (track && track.readyState === "live") {
                    track.enabled = true;
                } else {
                    track = await ensureVideoTrack();
                    if (track) {
                        track.enabled = true;
                    } else {
                        toast({
                            title: "Camera unavailable",
                            description: "Allow camera access in your browser, then try again.",
                            variant: "destructive",
                        });
                        return;
                    }
                }
            } else {
                isVideoOnRef.current = false;
                try {
                    await disableVideoTrack();
                } catch (error) {
                    console.error("Could not turn off camera:", error);
                    toast({
                        title: "Could not turn off camera",
                        description: "Check your camera settings and try again.",
                        variant: "destructive",
                    });
                    return;
                }
            }

            isVideoOnRef.current = newVideoOn;
            setIsVideoOn(newVideoOn);
            if (socketId) updateUser(socketId, { isVideoOn: newVideoOn });
            if (socket) socket.emit("toggle-camera", { roomId, userId: socketId, isVideoOn: newVideoOn });
            refreshMediaConnections();
        } finally {
            isTogglingCamera.current = false;
        }
    }, [socketId, socket, roomId, updateUser, ensureVideoTrack, disableVideoTrack, toast, refreshMediaConnections]);

    const toggleHand = useCallback(() => {
        setIsHandRaised(prev => {
            const next = !prev;
            if (socket) {
                socket.emit("toggle-hand", { roomId, isHandRaised: next });
            }
            return next;
        });
    }, [socket, roomId]);



    const handleScreenShare = useCallback(async () => {
        const targetState = !isScreenSharing;
        const result = await toggleScreenShare(targetState, () => {
            setIsScreenSharing(false);
        });

        if (result === targetState) {
            setIsScreenSharing(result);
        }
    }, [isScreenSharing, toggleScreenShare]);

    // ── Keyboard Shortcuts ──────────────────────────────────────────────
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            const target = e.target as HTMLElement;
            if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable) return;

            const key = e.code === "Space" ? "space" : e.key.toLowerCase();

            if (key === shortcuts.mute) {
                e.preventDefault();
                toggleMic();
            } else if (key === shortcuts.camera) {
                e.preventDefault();
                toggleCamera();
            } else if (key === shortcuts.polls) {
                e.preventDefault();
                setShowSidebar(prev => prev === "polls" ? null : "polls");
            } else if (key === shortcuts.chat) {
                e.preventDefault();
                setShowSidebar(prev => prev === "chat" ? null : "chat");
            } else if (key === shortcuts.fullscreen) {
                if (!e.ctrlKey && !e.metaKey) {
                    e.preventDefault();
                    if (document.fullscreenElement) {
                        document.exitFullscreen();
                    } else {
                        document.getElementById("syncroom-video-wrapper")?.requestFullscreen();
                    }
                }
            } else if (key === "escape") {
                if (document.fullscreenElement) {
                    document.exitFullscreen();
                }
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [toggleMic, toggleCamera, shortcuts]);

    const copyRoomId = useCallback(() => {
        navigator.clipboard.writeText(roomId as string);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    }, [roomId]);

    const copyShareLink = useCallback(() => {
        const shareUrl = `${window.location.origin}/room/${roomId}`;
        navigator.clipboard.writeText(shareUrl);
        setCopiedLink(true);
        setTimeout(() => setCopiedLink(false), 2000);
    }, [roomId]);

    if (status === "loading" || status === "unauthenticated" || !socket) {
        return (
            <div className="min-h-screen flex flex-col items-center justify-center bg-[#070709] text-white gap-4 noise">
                <div className="relative w-12 h-12">
                    <div className="absolute inset-0 rounded-full border-2 border-indigo-500/30 animate-ping" />
                    <div className="absolute inset-0 rounded-full border-2 border-t-indigo-500 animate-spin" />
                </div>
                <p className="text-sm text-zinc-400 font-mono animate-pulse uppercase tracking-widest">Connecting to room...</p>
            </div>
        );
    }

    return (
        <div className="h-screen flex flex-col bg-[#070709] text-white overflow-hidden noise">
            {/* ── Top Navigation Bar ─────────────────────────────────── */}
            <header className="border-b border-zinc-900 bg-[#070709]/80 backdrop-blur-xl shrink-0 z-10">
                <div className="h-13 flex items-center justify-between px-5">
                    <div className="flex items-center gap-3">
                        <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-indigo-500 via-violet-500 to-fuchsia-500 flex items-center justify-center shadow-lg shadow-indigo-500/10">
                            <Play size={12} fill="white" className="ml-0.5" />
                        </div>
                        <h1 className="text-base font-bold tracking-tight text-white">
                            {roomName || "SyncRoom"}
                        </h1>
                    </div>
                    <div className="flex items-center gap-1 tour-topbar-actions">
                        <Button
                            variant="ghost"
                            size="sm"
                            className={`h-9 px-3 gap-1.5 rounded-lg transition-all ${showSidebar === "participants" ? "bg-indigo-500/10 text-indigo-400" : "text-zinc-400 hover:text-white hover:bg-zinc-800/50"}`}
                            onClick={() => setShowSidebar(showSidebar === "participants" ? null : "participants")}
                        >
                            <UsersIcon size={16} />
                            <span className="hidden sm:inline text-xs">People</span>
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            className={`h-9 px-3 gap-1.5 rounded-lg transition-all ${showSidebar === "chat" ? "bg-indigo-500/10 text-indigo-400" : "text-zinc-400 hover:text-white hover:bg-zinc-800/50"}`}
                            onClick={() => setShowSidebar(showSidebar === "chat" ? null : "chat")}
                        >
                            <MessageSquare size={16} />
                            <span className="hidden sm:inline text-xs">Chat</span>
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            className={`h-9 px-3 gap-1.5 rounded-lg transition-all ${showSidebar === "polls" ? "bg-indigo-500/10 text-indigo-400" : "text-zinc-400 hover:text-white hover:bg-zinc-800/50"}`}
                            onClick={() => setShowSidebar(showSidebar === "polls" ? null : "polls")}
                        >
                            <Vote size={16} />
                            <span className="hidden sm:inline text-xs">Polls & Q&A</span>
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            className={`h-9 px-3 gap-1.5 rounded-lg transition-all ${showSidebar === "queue" ? "bg-indigo-500/10 text-indigo-400" : "text-zinc-400 hover:text-white hover:bg-zinc-800/50"}`}
                            onClick={() => setShowSidebar(showSidebar === "queue" ? null : "queue")}
                        >
                            <ListVideo size={16} />
                            <span className="hidden sm:inline text-xs">Up Next</span>
                        </Button>

                        <Button
    variant="ghost"
    size="sm"
    className={`h-9 px-3 gap-1.5 rounded-lg transition-all ${
        showSidebar === "study"
            ? "bg-indigo-500/10 text-indigo-400"
            : "text-zinc-400 hover:text-white hover:bg-zinc-800/50"
    }`}
    onClick={() =>
        setShowSidebar(showSidebar === "study" ? null : "study")
    }
>
    <BookOpen size={16} />
    <span className="hidden sm:inline text-xs">Study Room</span>
</Button>

                        {isOwner && <HostControls socket={socket} roomId={roomId as string} />}
                        {isRoomCreator && (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => {
                                    if (window.confirm("Delete this room for everyone? No one will be able to rejoin, and its chat, pinned notes, and saved video list will be deleted.")) {
                                        socket?.emit("delete-room", { roomId });
                                    }
                                }}
                                className="h-9 px-3 gap-1.5 rounded-lg text-red-400 hover:text-red-300 hover:bg-red-500/10 transition-all"
                                title="Delete this room for everyone"
                            >
                                <Trash2 size={16} />
                                <span className="hidden sm:inline text-xs">Delete Room</span>
                            </Button>
                        )}
                        <Button
                            variant="ghost"
                            size="sm"
                            className="h-9 px-3 gap-1.5 rounded-lg text-zinc-400 hover:text-white hover:bg-zinc-800/50 transition-all"
                            onClick={() => setShowSettingsModal(true)}
                            title="Settings"
                        >
                            <Settings size={16} />
                            <span className="hidden sm:inline text-xs">Settings</span>
                        </Button>
                        <div className="w-px h-6 bg-zinc-800 mx-1" />
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => router.push("/dashboard")}
                            className="h-9 px-3 gap-1.5 rounded-lg text-red-400 hover:text-red-300 hover:bg-red-500/10 transition-all"
                            title="Leave Room to Dashboard"
                        >
                            <LogOut size={16} />
                            <span className="hidden sm:inline text-xs">Leave</span>
                        </Button>
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => signOut({ callbackUrl: "/" })}
                            className="h-9 px-3 gap-1.5 rounded-lg text-rose-500 hover:text-rose-450 hover:bg-rose-500/10 border border-zinc-900/60 hover:border-rose-500/20 transition-all ml-1"
                            title="Sign Out Completely"
                        >
                            <LogOut size={16} />
                            <span className="hidden sm:inline text-xs">Sign Out</span>
                        </Button>
                    </div>
                </div>

                {/* ── Persistent Room Code Banner ─────────────────────── */}
                {showRoomBanner && (
                    <div className="flex items-center justify-between px-5 py-2 bg-indigo-500/5 border-t border-indigo-500/10">
                        <div className="flex items-center gap-3 flex-1 min-w-0">
                            <div className="flex items-center gap-1.5 shrink-0">
                                <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                                <span className="text-[11px] uppercase tracking-wider text-zinc-500 font-medium">Room Code</span>
                            </div>
                            <span className="font-mono text-sm font-bold text-indigo-300 tracking-wider">{roomId}</span>
                            <button
                                onClick={copyRoomId}
                                className={`flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-medium transition-all cursor-pointer ${
                                    copied
                                        ? "bg-emerald-500/10 text-emerald-400"
                                        : "bg-zinc-800/50 text-zinc-400 hover:text-zinc-300 hover:bg-zinc-800"
                                }`}
                            >
                                {copied ? <><Check size={10} /> Copied</> : <><Copy size={10} /> Copy</>}
                            </button>
                            <button
                                onClick={() => setShowShareDialog(true)}
                                className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-500/10 text-[11px] font-medium text-indigo-400 hover:bg-indigo-500/20 transition-all cursor-pointer"
                            >
                                <Share2 size={10} />
                                Invite
                            </button>
                        </div>
                        <button
                            onClick={() => setShowRoomBanner(false)}
                            className="text-zinc-600 hover:text-zinc-400 transition-colors ml-2 text-lg leading-none cursor-pointer"
                            title="Dismiss"
                        >
                            ×
                        </button>
                    </div>
                )}
            </header>

            {/* ── Main Layout ────────────────────────────────────────── */}
            <div className="flex flex-1 overflow-hidden">
                {/* Onboarding Tour */}
                <RoomTour />

                {/* ── Center Canvas ───────────────────────────────────── */}
                <main className="flex-1 flex flex-col overflow-hidden">
                    {/* Content Area */}
                    <div className="flex-1 flex flex-col p-4 overflow-y-auto gap-3">
                        {/* Camera Grid */}
                        <CameraGrid currentUserId={socketId} />

                        {/* Video Player */}
                        <div className="flex-1 flex flex-col glass rounded-xl overflow-hidden min-h-[300px] tour-video-player">
                            <VideoPlayer 
                                socket={socket} 
                                roomId={roomId as string} 
                                onToggleQueue={() => setShowSidebar(prev => prev === "queue" ? null : "queue")}
                                isQueueOpen={showSidebar === "queue"}
                            />
                        </div>
                    </div>

                    {/* ── Bottom Control Bar ──────────────────────────────── */}
                    <div className="h-16 border-t border-zinc-800/40 flex items-center justify-center gap-3 px-6 shrink-0 tour-controls">
                        <button
                            onClick={toggleMic}
                            className={`h-11 w-11 rounded-full flex items-center justify-center transition-all duration-200 ${
                                isMuted
                                    ? "bg-red-500 hover:bg-red-600 text-white shadow-lg shadow-red-500/20"
                                    : "bg-zinc-800 hover:bg-zinc-700 text-zinc-200"
                            }`}
                        >
                            {isMuted ? <MicOff size={18} /> : <Mic size={18} />}
                        </button>
                        <button
                            onClick={toggleCamera}
                            className={`h-11 w-11 rounded-full flex items-center justify-center transition-all duration-200 ${
                                !isVideoOn
                                    ? "bg-red-500 hover:bg-red-600 text-white shadow-lg shadow-red-500/20"
                                    : "bg-zinc-800 hover:bg-zinc-700 text-zinc-200"
                            }`}
                        >
                            {isVideoOn ? <Video size={18} /> : <VideoOff size={18} />}
                        </button>
                        <button
                            onClick={handleScreenShare}
                            className={`h-11 w-11 rounded-full flex items-center justify-center transition-all duration-200 ${
                                isScreenSharing
                                    ? "bg-indigo-500 hover:bg-indigo-600 text-white shadow-lg shadow-indigo-500/20"
                                    : "bg-zinc-800 hover:bg-zinc-700 text-zinc-200"
                            }`}
                        >
                        <MonitorUp size={18} />
                        </button>
                        <button
                            onClick={toggleHand}
                            className={`h-11 w-11 rounded-full flex items-center justify-center transition-all duration-200 ${
                                isHandRaised
                                    ? "bg-yellow-500 hover:bg-yellow-600 text-white shadow-lg shadow-yellow-500/20"
                                    : "bg-zinc-800 hover:bg-zinc-700 text-zinc-200"
                            }`}
                            title={isHandRaised ? "Lower Hand" : "Raise Hand"}
                        >
                            <Hand size={18} />
                        </button>
                    </div>

                    {/* ── Emoji Reactions Bar ─────────────────────────── */}
                    {socket && (
                        <div className="mt-2 flex justify-center">
                            <EmojiReactions socket={socket} roomId={roomId as string} />
                        </div>
                    )}

                    {/* ── Keyboard Shortcuts Hint ─────────────────────── */}
                    <div className="mt-3 mb-1 flex justify-center gap-6">
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.mute}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Mute</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.camera}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Camera</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.fullscreen}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Fullscreen</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.chat}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Toggle Chat</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.whiteboard}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Whiteboard</span>
                        </div>
                        <div className="flex items-center gap-2">
                            <kbd className="px-2 py-1 bg-zinc-800 border border-zinc-700/50 rounded-md text-[10px] font-mono text-zinc-300 shadow-sm uppercase min-w-[24px] text-center">{shortcuts.polls}</kbd>
                            <span className="text-[11px] text-zinc-500 font-medium">Polls & Q&A</span>
                        </div>
                    </div>
                </main>

                {/* ── Sidebar ────────────────────────────────────────── */}
                {showSidebar && (
                    <aside className={`border-l border-zinc-900 bg-[#070709]/80 backdrop-blur-xl flex flex-col shrink-0 z-10 tour-sidebar transition-all duration-300 ease-out ${
                        showSidebar === "queue" && queueTab === "search" ? "w-[560px]" : "w-[340px]"
                    }`}>
                        {showSidebar === "chat" ? (
                            <ChatPanel socket={socket} roomId={roomId as string} />
                        ) : showSidebar === "participants" ? (
                            <ParticipantsPanel socket={socket} roomId={roomId as string} />
                        ) : showSidebar === "polls" ? (
                            <PollsPanel socket={socket} roomId={roomId as string} />
                        ) : showSidebar === "study" ? (
    <StudyRoomPanel socket={socket} roomId={roomId as string} />
) : (

                            <QueuePanel 
                                socket={socket} 
                                roomId={roomId as string} 
                                onClose={() => setShowSidebar(null)} 
                                onActiveTabChange={setQueueTab}
                            />
                        )}
                    </aside>
                )}
            </div>

            {/* ── Share / Invite Dialog ──────────────────────────────── */}
            <Dialog open={showShareDialog} onOpenChange={setShowShareDialog}>
                <DialogContent className="bg-zinc-950 border-zinc-800 text-white max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold flex items-center gap-2">
                            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 flex items-center justify-center">
                                <Share2 size={16} className="text-indigo-400" />
                            </div>
                            Invite People
                        </DialogTitle>
                        <DialogDescription className="text-zinc-400">
                            Share the room code or link below so others can join your session.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4 mt-2">
                        {/* Room Code */}
                        <div>
                            <label className="text-[11px] uppercase tracking-wider text-zinc-500 font-medium mb-2 block">Room Code</label>
                            <div className="flex items-center gap-2">
                                <div className="flex-1 flex items-center justify-center px-4 py-3 rounded-xl bg-zinc-900/80 border border-zinc-800/60 font-mono text-xl tracking-[0.3em] text-white font-bold select-all">
                                    {roomId}
                                </div>
                                <button
                                    onClick={copyRoomId}
                                    className={`h-12 w-12 rounded-xl flex items-center justify-center transition-all duration-200 ${
                                        copied
                                            ? "bg-emerald-500/10 border border-emerald-500/30 text-emerald-400"
                                            : "bg-zinc-800 border border-zinc-700/50 text-zinc-300 hover:bg-zinc-700 hover:text-white"
                                    }`}
                                >
                                    {copied ? <Check size={18} /> : <Copy size={18} />}
                                </button>
                            </div>
                        </div>

                        {/* Divider */}
                        <div className="flex items-center gap-3">
                            <div className="flex-1 h-px bg-zinc-800" />
                            <span className="text-[11px] text-zinc-600 uppercase">or</span>
                            <div className="flex-1 h-px bg-zinc-800" />
                        </div>

                        {/* Share Link */}
                        <div>
                            <label className="text-[11px] uppercase tracking-wider text-zinc-500 font-medium mb-2 block">Shareable Link</label>
                            <div className="flex items-center gap-2">
                                <div className="flex-1 flex items-center gap-2 px-3 py-2.5 rounded-xl bg-zinc-900/80 border border-zinc-800/60 text-sm text-zinc-400 overflow-hidden">
                                    <Link2 size={14} className="text-zinc-500 shrink-0" />
                                    <span className="truncate">{typeof window !== "undefined" ? `${window.location.origin}/room/${roomId}` : ``}</span>
                                </div>
                                <button
                                    onClick={copyShareLink}
                                    className={`h-10 px-4 rounded-xl flex items-center gap-1.5 text-sm font-medium transition-all duration-200 ${
                                        copiedLink
                                            ? "bg-emerald-500/10 border border-emerald-500/30 text-emerald-400"
                                            : "bg-indigo-500 text-white hover:bg-indigo-600 shadow-lg shadow-indigo-500/20"
                                    }`}
                                >
                                    {copiedLink ? <><Check size={14} /> Copied!</> : <><Copy size={14} /> Copy Link</>}
                                </button>
                            </div>
                        </div>

                        {/* Hint */}
                        <p className="text-[11px] text-zinc-600 text-center pt-1">
                            Anyone with the code or link can join this room directly.
                        </p>
                    </div>
                </DialogContent>
            </Dialog>
            {/* ── Disconnected Overlay ─────────────────────────────── */}
            {isDisconnected && (
                <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center">
                    <div className="flex flex-col items-center gap-3 text-center">
                        <WifiOff size={32} className="text-red-400 animate-pulse" />
                        <h3 className="text-lg font-semibold text-white">Connection Lost</h3>
                        <p className="text-sm text-zinc-400">Reconnecting automatically...</p>
                        <div className="w-8 h-8 border-2 border-t-indigo-500 border-zinc-700 rounded-full animate-spin" />
                    </div>
                </div>
            )}
            


            {/* Settings Modal */}
            <SettingsModal open={showSettingsModal} onOpenChange={setShowSettingsModal} />
        </div>
    );
}
