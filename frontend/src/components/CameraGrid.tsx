"use client";

import { useRoomStore } from "@/store/useRoomStore";
import { MicOff, VideoOff, Hand } from "lucide-react";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";

/**
 * Renders a <video> element attached to a MediaStream.
 * The video element is ALWAYS mounted — we toggle visibility via CSS
 * instead of unmounting/remounting, which eliminates the flickering.
 */
function VideoTile({ stream, isVideoOn }: { stream: MediaStream | undefined; isVideoOn?: boolean }) {
    const videoRef = useRef<HTMLVideoElement>(null);

    useEffect(() => {
        const video = videoRef.current;
        if (!video || !stream) return;

        const attach = async () => {
            if (!video || !stream) return;
            if (video.srcObject !== stream) {
                video.srcObject = stream;
            }
            try {
                await video.play();
            } catch {
                // Ignore autoplay policy errors safely
            }
        };

        attach();

        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") attach();
        };
        const handleFocus = () => attach();
        const onTrackAdded = () => attach();

        document.addEventListener("visibilitychange", handleVisibilityChange);
        window.addEventListener("focus", handleFocus);
        stream.addEventListener("addtrack", onTrackAdded);

        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            window.removeEventListener("focus", handleFocus);
            stream.removeEventListener("addtrack", onTrackAdded);
        };
    }, [stream]);

    return (
        <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            disablePictureInPicture={true}
            className={`absolute inset-0 w-full h-full object-cover transition-opacity duration-300 ${isVideoOn && stream ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
        />
    );
}

// Separate persistent audio element for remote users' audio
function AudioTile({ stream }: { stream: MediaStream }) {
    const audioRef = useRef<HTMLAudioElement>(null);
    const [needsUserGesture, setNeedsUserGesture] = useState(false);

    useEffect(() => {
        const audio = audioRef.current;
        if (!audio || !stream) return;

        const attachAudio = async () => {
            if (!audio || !stream) return;
            if (audio.srcObject !== stream) {
                audio.srcObject = stream;
            }
            if (audio.paused) {
                try {
                    await audio.play();
                    setNeedsUserGesture(false);
                } catch {
                    setNeedsUserGesture(true);
                }
            }
        };

        const retryAudio = () => { void attachAudio(); };
        void attachAudio();

        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") attachAudio();
        };
        const handleFocus = () => attachAudio();

        document.addEventListener("visibilitychange", handleVisibilityChange);
        window.addEventListener("focus", handleFocus);
        document.addEventListener("pointerdown", retryAudio);
        document.addEventListener("keydown", retryAudio);
        stream.addEventListener("addtrack", retryAudio);
        const audioTracks = stream.getAudioTracks();
        audioTracks.forEach((track) => track.addEventListener("unmute", retryAudio));
        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            window.removeEventListener("focus", handleFocus);
            document.removeEventListener("pointerdown", retryAudio);
            document.removeEventListener("keydown", retryAudio);
            stream.removeEventListener("addtrack", retryAudio);
            audioTracks.forEach((track) => track.removeEventListener("unmute", retryAudio));
        };
    }, [stream]);

    return (
        <>
            <audio ref={audioRef} autoPlay playsInline />
            {needsUserGesture && (
                <button
                    type="button"
                    onClick={async () => {
                        const audio = audioRef.current;
                        if (!audio) return;
                        try {
                            await audio.play();
                            setNeedsUserGesture(false);
                        } catch (error) {
                            console.error("Could not start remote audio:", error);
                        }
                    }}
                    className="absolute inset-x-1 bottom-5 z-20 rounded bg-zinc-950/90 px-2 py-1 text-[10px] font-medium text-white"
                    title="Click to enable this participant's audio"
                >
                    Enable sound
                </button>
            )}
        </>
    );
}

export default function CameraGrid({ currentUserId }: { currentUserId: string }) {
    const { users } = useRoomStore();

    if (users.length === 0) return null;

    return (
        <div className="flex gap-2 pb-2 mb-1 overflow-x-auto scrollbar-thin" style={{ scrollbarWidth: 'thin' }}>
            {users.map((user) => {
                const isLocal = user.id === currentUserId;
                const showVideo = user.isVideoOn && user.stream;

                return (
                    <div
                        key={user.id}
                        className="relative w-20 h-20 shrink-0 rounded-xl overflow-hidden glass group"
                    >
                        {/* Always-mounted video element — shown/hidden via opacity */}
                        {user.stream && (
                            <VideoTile stream={user.stream} isVideoOn={user.isVideoOn} />
                        )}

                        {/* Avatar fallback — shown when video is off */}
                        <div className={`absolute inset-0 flex flex-col items-center justify-center transition-opacity duration-300 ${showVideo ? 'opacity-0' : 'opacity-100'}`}>
                            <div className="w-10 h-10 rounded-full bg-zinc-800 border border-zinc-700/50 flex items-center justify-center overflow-hidden">
                                <Image
                                    src={user.image || `https://api.dicebear.com/7.x/initials/svg?seed=${user.name}`}
                                    alt={user.name}
                                    width={40}
                                    height={40}
                                    unoptimized
                                    className="w-full h-full object-cover"
                                />
                            </div>
                        </div>

                        {/* Persistent audio for remote users — always mounted.
                            Muting is controlled at the track level (track.enabled),
                            not by unmounting the audio element. */}
                        {!isLocal && user.stream && (
                            <AudioTile stream={user.stream} />
                        )}

                        {/* Hand Raised Indicator */}
                        {user.isHandRaised && (
                            <div className="absolute top-1 left-1 bg-zinc-900/80 p-1 rounded-md shadow-lg border border-yellow-500/30 animate-pulse z-10">
                                <Hand size={12} className="text-yellow-500 fill-yellow-500" />
                            </div>
                        )}

                        {/* Status indicators */}
                        <div className="absolute top-1 right-1 flex gap-0.5">
                            {user.isMuted && (
                                <div className="bg-red-500/80 p-0.5 rounded">
                                    <MicOff size={8} className="text-white" />
                                </div>
                            )}
                            {!user.isVideoOn && (
                                <div className="bg-red-500/80 p-0.5 rounded">
                                    <VideoOff size={8} className="text-white" />
                                </div>
                            )}
                        </div>

                        {/* Name label */}
                        <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent px-1.5 py-1">
                            <span className="text-[10px] font-medium text-white/90 truncate block leading-tight">
                                {isLocal ? "You" : user.name?.split(" ")[0]}
                            </span>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
