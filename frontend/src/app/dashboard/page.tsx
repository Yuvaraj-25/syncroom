"use client";

import { useSession, signOut } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useState, useEffect, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { 
    LogOut, Plus, Tv2, ArrowRight, Play, 
    Sparkles, KeyRound, Calendar, History
} from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { motion } from "framer-motion";
import { useToast } from "@/hooks/use-toast";

export default function Dashboard() {
    const { data: session, status } = useSession();
    const router = useRouter();
    const { toast } = useToast();
    
    // Core state management
    const [recentRooms, setRecentRooms] = useState<{roomId: string, joinedAt: string}[]>([]);
    const [isLoadingRooms, setIsLoadingRooms] = useState(true);
    const [isNavigating, setIsNavigating] = useState(false);
    
    // User manual room join code state
    const [joinRoomCode, setJoinRoomCode] = useState("");

    useEffect(() => {
        if (status === "unauthenticated") {
            router.push("/");
        }
    }, [status, router]);

    // Fetch recent rooms
    useEffect(() => {
        if (status === "authenticated" && session?.user?.email) {
            const apiUrl = process.env.NEXT_PUBLIC_SOCKET_URL?.replace(/\/$/, '') || "http://localhost:4000";
            
            fetch(`${apiUrl}/api/users/sync`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    name: session.user?.name,
                    email: session.user?.email,
                    image: session.user?.image,
                })
            })
            .then(() => {
                return fetch(`${apiUrl}/api/users/recent-rooms?email=${encodeURIComponent((session.user?.email as string) || "")}`);
            })
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setRecentRooms(data);
                }
            })
            .catch(err => console.error("Failed to load recent rooms:", err))
            .finally(() => setIsLoadingRooms(false));
        }
    }, [status, session]);

    // Command Handlers
    const handleCreateRoom = useCallback(async () => {
        if (isNavigating) return;
        setIsNavigating(true);
        const newRoomId = Math.random().toString(36).substring(2, 9).toUpperCase();
        const ownerId = (session?.user as { id?: string } | undefined)?.id;

        if (!ownerId) {
            setIsNavigating(false);
            toast({
                title: "Couldn't create room",
                description: "Your account ID is missing. Please sign in again.",
                variant: "destructive",
            });
            return;
        }

        try {
            const apiUrl = process.env.NEXT_PUBLIC_SOCKET_URL?.replace(/\/$/, "") || "http://localhost:4000";
            const response = await fetch(`${apiUrl}/api/rooms/create`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ roomId: newRoomId, ownerId, isPrivate: false }),
            });

            if (!response.ok) {
                throw new Error("The server couldn't save the new room.");
            }

            router.push(`/room/${newRoomId}`);
        } catch (error) {
            console.error("Failed to create room:", error);
            setIsNavigating(false);
            toast({
                title: "Couldn't create room",
                description: error instanceof Error ? error.message : "Please try again.",
                variant: "destructive",
            });
        }
    }, [isNavigating, router, session, toast]);

    const handleJoinRoomId = useCallback((id: string) => {
        if (isNavigating) return;
        const cleanId = id.trim().toUpperCase();
        if (cleanId) {
            setIsNavigating(true);
            router.push(`/room/${cleanId}`);
        }
    }, [isNavigating, router]);

    if (status === "loading" || !session || isNavigating) {
        return (
            <div className="min-h-screen flex flex-col items-center justify-center bg-[#050506] gap-3">
                <div className="relative w-12 h-12">
                    <div className="absolute inset-0 rounded-full border-2 border-indigo-500/20 animate-ping" />
                    <div className="absolute inset-0 rounded-full border-2 border-t-indigo-500 animate-spin" />
                </div>
                {isNavigating && (
                    <p className="text-xs text-indigo-300 font-mono animate-pulse uppercase tracking-widest mt-3">
                        Connecting to Watchroom Session...
                    </p>
                )}
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-[#050506] text-zinc-100 relative overflow-hidden noise selection:bg-indigo-600/40">
            {/* Bright High-Vibe Ambient Glow Mesh Orbs */}
            <div className="absolute top-0 right-1/4 w-[600px] h-[600px] rounded-full bg-gradient-to-br from-indigo-500/10 via-fuchsia-500/10 to-transparent filter blur-[120px] pointer-events-none animate-pulse" />
            <div className="absolute bottom-1/4 left-1/4 w-[500px] h-[500px] rounded-full bg-gradient-to-tr from-rose-500/10 via-violet-500/5 to-transparent filter blur-[100px] pointer-events-none" />

            {/* ── Top Bar ────────────────────────────────────────────── */}
            <header className="relative z-20 flex items-center justify-between px-6 md:px-16 py-5 border-b border-zinc-900 bg-[#050506]/75 backdrop-blur-xl">
                <div className="flex items-center gap-3.5">
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-lg shadow-indigo-500/15">
                        <Play size={14} fill="white" className="ml-0.5 text-white" />
                    </div>
                    <div>
                        <span className="text-sm font-black tracking-tight text-white block">SyncRoom</span>
                        <span className="text-[9px] text-zinc-500 font-mono tracking-widest block leading-none">DASHBOARD</span>
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2.5 px-3 py-1.5 rounded-2xl bg-zinc-900/60 border border-zinc-800/80 shadow-inner">
                        <Avatar className="h-6 w-6 border border-zinc-850">
                            <AvatarImage src={session?.user?.image || ""} />
                            <AvatarFallback className="text-[10px] font-black bg-zinc-800 text-zinc-300">
                                {session?.user?.name?.[0]?.toUpperCase()}
                            </AvatarFallback>
                        </Avatar>
                        <span className="text-xs text-zinc-300 font-bold hidden sm:block">
                            {session?.user?.name?.split(" ")[0]}
                        </span>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => signOut({ callbackUrl: "/" })}
                        className="h-9 px-3 gap-1.5 rounded-2xl text-zinc-400 hover:text-rose-400 hover:bg-rose-500/10 border border-zinc-800/80 hover:border-rose-500/25 transition-all cursor-pointer"
                        title="Sign Out Completely"
                    >
                        <LogOut size={14} />
                        <span className="hidden sm:inline text-xs font-bold">Sign Out</span>
                    </Button>
                </div>
            </header>

            {/* ── Main Dashboard Layout ──────────────────────────────── */}
            <main className="relative z-10 max-w-5xl mx-auto px-6 py-12 md:py-16 space-y-12">
                
                {/* Visual Greeting Header */}
                <motion.div
                    initial={{ opacity: 0, y: 15 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
                    className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-zinc-900/80 pb-6"
                >
                    <div>
                        <span className="inline-flex items-center gap-1 text-[10px] uppercase font-mono bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-0.5 rounded-md font-bold">
                            <Sparkles size={11} className="animate-spin-slow" /> Active Console
                        </span>
                        <h1 className="text-3xl sm:text-4xl font-black tracking-tight text-white mt-2">
                            Welcome back,{" "}
                            <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400">
                                {session?.user?.name || "Guest User"}
                            </span>
                        </h1>
                    </div>
                    
                    <div className="text-left sm:text-right">
                        <span className="text-[10px] text-zinc-500 font-mono block">LOGGED IN AS</span>
                        <span className="text-xs font-mono text-zinc-300 font-medium">{session.user?.email}</span>
                    </div>
                </motion.div>

                {/* ── Card Action Panels ───────────────────────────────── */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                    
                    {/* CARD 1: Launch Room */}
                    <motion.div
                        initial={{ opacity: 0, x: -20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.5, delay: 0.1 }}
                        className="group relative rounded-3xl border border-zinc-800 bg-zinc-950/40 p-6 md:p-8 hover:border-indigo-500/30 transition-all duration-300 shadow-xl backdrop-blur-md flex flex-col justify-between overflow-hidden"
                    >
                        {/* Decorative glow element */}
                        <div className="absolute -top-12 -left-12 w-32 h-32 rounded-full bg-indigo-500/5 filter blur-2xl group-hover:bg-indigo-500/10 transition-all" />

                        <div className="space-y-4 relative z-10">
                            <div className="w-12 h-12 rounded-2xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 flex items-center justify-center shadow-inner">
                                <Plus size={22} className="group-hover:rotate-90 transition-transform duration-300" />
                            </div>
                            <div>
                                <h3 className="text-lg font-black text-white group-hover:text-indigo-300 transition-colors">
                                    Launch New Watchroom
                                </h3>
                                <p className="text-xs text-zinc-400 leading-relaxed mt-1">
                                    Start a customized synchronous room session instantly. Perfect for co-streaming YouTube playlists, uploading vertical mobile Reels/Shorts, and sketching on live canvases with friends.
                                </p>
                            </div>
                        </div>

                        <div className="pt-6 relative z-10">
                            <button
                                onClick={handleCreateRoom}
                                className="w-full inline-flex items-center justify-center gap-2 px-5 py-3.5 rounded-2xl bg-indigo-600 hover:bg-indigo-500 text-white font-extrabold text-xs transition-all duration-300 active:scale-95 shadow-md shadow-indigo-600/15 cursor-pointer"
                            >
                                <Play size={12} fill="white" className="text-white" />
                                Create Watch Session
                            </button>
                        </div>
                    </motion.div>

                    {/* CARD 2: Enter Room Code */}
                    <motion.div
                        initial={{ opacity: 0, x: 20 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ duration: 0.5, delay: 0.1 }}
                        className="group relative rounded-3xl border border-zinc-800 bg-zinc-950/40 p-6 md:p-8 hover:border-pink-500/30 transition-all duration-300 shadow-xl backdrop-blur-md flex flex-col justify-between overflow-hidden"
                    >
                        {/* Decorative glow element */}
                        <div className="absolute -top-12 -right-12 w-32 h-32 rounded-full bg-pink-500/5 filter blur-2xl group-hover:bg-pink-500/10 transition-all" />

                        <div className="space-y-4 relative z-10">
                            <div className="w-12 h-12 rounded-2xl bg-pink-500/10 text-pink-400 border border-pink-500/20 flex items-center justify-center shadow-inner">
                                <KeyRound size={20} />
                            </div>
                            <div>
                                <h3 className="text-lg font-black text-white group-hover:text-pink-300 transition-colors">
                                    Enter Active Room Code
                                </h3>
                                <p className="text-xs text-zinc-400 leading-relaxed mt-1">
                                    Have a friend&apos;s watchroom coordinate code or active lobby link? Paste or type the ID code directly below to sync playback, camera streams, and overlay sketches.
                                </p>
                            </div>
                        </div>

                        <div className="space-y-3 pt-6 relative z-10">
                            <div className="relative">
                                <Input
                                    value={joinRoomCode}
                                    onChange={(e) => setJoinRoomCode(e.target.value)}
                                    placeholder="PASTE ROOM CODE (E.G. ROOM-77X9)"
                                    className="w-full bg-zinc-950 border-zinc-800 text-white placeholder:text-zinc-600 focus-visible:ring-1 focus-visible:ring-pink-500/50 focus-visible:ring-offset-0 text-xs font-mono h-11 px-4 rounded-xl uppercase tracking-wider"
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter" && joinRoomCode.trim()) {
                                            handleJoinRoomId(joinRoomCode);
                                        }
                                    }}
                                />
                            </div>
                            
                            <button
                                onClick={() => handleJoinRoomId(joinRoomCode)}
                                disabled={!joinRoomCode.trim()}
                                className={`w-full inline-flex items-center justify-center gap-2 px-5 py-3.5 rounded-2xl font-extrabold text-xs transition-all duration-300 cursor-pointer ${
                                    joinRoomCode.trim()
                                        ? "bg-pink-600 hover:bg-pink-500 text-white shadow-md shadow-pink-600/15 active:scale-95"
                                        : "bg-zinc-900 border border-zinc-850 text-zinc-600 cursor-not-allowed"
                                }`}
                            >
                                Join Watchroom
                                <ArrowRight size={12} strokeWidth={2.5} />
                            </button>
                        </div>
                    </motion.div>

                </div>

                {/* ── Recent Rooms Grid History Section ────────────────── */}
                <div className="space-y-6 pt-4">
                    <div className="flex items-center justify-between border-b border-zinc-900/80 pb-4">
                        <div className="flex items-center gap-2">
                            <History size={16} className="text-indigo-400" />
                            <h2 className="text-base font-extrabold text-white">Your Previous Watch Sessions</h2>
                        </div>
                        <span className="text-[10px] font-mono text-zinc-500 uppercase">
                            {recentRooms.length} Sessions Logged
                        </span>
                    </div>

                    {isLoadingRooms ? (
                        <div className="py-16 text-center text-zinc-500 flex flex-col items-center justify-center gap-3 bg-zinc-950/20 border border-zinc-900/60 rounded-3xl">
                            <div className="w-6 h-6 rounded-full border-2 border-t-indigo-500 border-zinc-800 animate-spin" />
                            <span className="text-[10px] font-mono tracking-widest text-zinc-500 uppercase">Synchronizing server records...</span>
                        </div>
                    ) : recentRooms.length === 0 ? (
                        <div className="py-16 text-center text-zinc-500 flex flex-col items-center justify-center gap-2 bg-zinc-950/20 border border-zinc-900/60 rounded-3xl">
                            <div className="w-10 h-10 rounded-2xl bg-zinc-900/40 border border-zinc-800/80 flex items-center justify-center text-zinc-600">
                                <Tv2 size={18} />
                            </div>
                            <h4 className="text-xs font-bold text-zinc-400">No Previous Rooms Visited</h4>
                            <p className="text-[10px] text-zinc-600 max-w-xs leading-normal">
                                Create a watchroom above and invite companions to populate your history list!
                            </p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                            {recentRooms.map((room, idx) => {
                                const formattedTime = new Date(room.joinedAt).toLocaleDateString(undefined, { 
                                    month: 'short', day: 'numeric' 
                                }) + " · " + new Date(room.joinedAt).toLocaleTimeString([], {
                                    hour: '2-digit', minute: '2-digit'
                                });

                                return (
                                    <motion.div
                                        key={idx}
                                        initial={{ opacity: 0, y: 10 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ duration: 0.3, delay: idx * 0.05 }}
                                        className="group relative rounded-2xl border border-zinc-900 bg-zinc-950/60 p-4 hover:border-zinc-800 transition-all flex flex-col justify-between gap-4"
                                    >
                                        <div className="space-y-3">
                                            <div className="flex items-center justify-between">
                                                <div className="w-8 h-8 rounded-xl bg-zinc-900 border border-zinc-800 flex items-center justify-center text-zinc-400 group-hover:bg-indigo-500/10 group-hover:text-indigo-400 transition-colors">
                                                    <Tv2 size={14} />
                                                </div>
                                                <span className="text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-zinc-900 text-zinc-500 border border-zinc-800">
                                                    Session ID
                                                </span>
                                            </div>

                                            <div>
                                                <h4 className="text-xs font-mono font-black text-white tracking-widest uppercase">
                                                    {room.roomId}
                                                </h4>
                                                <div className="flex items-center gap-1 text-[10px] text-zinc-500 mt-2">
                                                    <Calendar size={11} className="shrink-0" />
                                                    <span>Last visited {formattedTime}</span>
                                                </div>
                                            </div>
                                        </div>

                                        <button
                                            onClick={() => handleJoinRoomId(room.roomId)}
                                            className="w-full inline-flex items-center justify-center gap-1.5 py-2.5 rounded-xl bg-zinc-900 hover:bg-zinc-850 border border-zinc-800/80 group-hover:border-indigo-500/30 text-zinc-300 hover:text-white font-extrabold text-[10px] transition-all cursor-pointer"
                                        >
                                            Rejoin Room
                                            <ArrowRight size={10} strokeWidth={2.5} className="group-hover:translate-x-0.5 transition-transform" />
                                        </button>
                                    </motion.div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </main>

        </div>
    );
}
