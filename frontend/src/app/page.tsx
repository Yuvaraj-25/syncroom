"use client";

import { signIn, useSession } from "next-auth/react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
    Play, Zap, Video, Sparkles, 
    Laptop, Edit3, ChevronDown, 
    ArrowRight, Info, Layers, HelpCircle, X
} from "lucide-react";

export default function Home() {
    const { status } = useSession();
    const router = useRouter();

    // FAQ state management (clicking accordions)
    const [expandedFaq, setExpandedFaq] = useState<number | null>(null);

    // Active Feature showcase tab
    const [activeFeature, setActiveFeature] = useState<number>(0);
    const [showGuestSignIn, setShowGuestSignIn] = useState(false);
    const [guestName, setGuestName] = useState("");
    const [guestEmail, setGuestEmail] = useState("");
    const [signInError, setSignInError] = useState("");
    const [isSigningIn, setIsSigningIn] = useState(false);



    useEffect(() => {
        if (status === "authenticated") {
            router.push("/dashboard");
        }
    }, [status, router]);



    const handleSignIn = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setSignInError("");
        setIsSigningIn(true);

        try {
            const res = await signIn("credentials", {
                redirect: false,
                callbackUrl: "/dashboard",
                name: guestName.trim(),
                email: guestEmail.trim()
            });
            if (res?.ok) {
                router.push("/dashboard");
                router.refresh();
            } else {
                setSignInError("We couldn't sign you in. Please check your details and try again.");
            }
        } catch (err) {
            console.error("Sign in error:", err);
            setSignInError("Sign-in failed. Please try again.");
        } finally {
            setIsSigningIn(false);
        }
    };

    if (status === "loading") {
        return (
            <div className="min-h-screen flex items-center justify-center bg-[#050506]">
                <div className="relative w-12 h-12">
                    <div className="absolute inset-0 rounded-full border-2 border-indigo-500/20 animate-ping" />
                    <div className="absolute inset-0 rounded-full border-2 border-t-indigo-500 animate-spin" />
                </div>
            </div>
        );
    }

    const faqItems = [
        {
            q: "How does the millisecond playback synchronization work under the hood?",
            a: "SyncRoom operates on a state-driven socket synchronization architecture. When a Room Owner pauses, plays, or seeks, the system immediately captures the high-precision timestamp of the HTML5 Media elements and broadcasts a room coordinate frame. All peer endpoints intercept this frame, perform offset calculations to account for individual network latency, and adjust their local players within a margin of 15ms."
        },
        {
            q: "Does SyncRoom support native vertical Reels & Shorts files?",
            a: "Yes, fully! When you drag-and-drop or select any vertical video from your local device, SyncRoom automatically calculates the media aspect ratio. If it is 9:16, the main canvas transitions seamlessly into an elegant physical smartphone bezel frame. This bezel is backlit with ambient, content-aware glass shadows, making co-watching vertical mobile recordings look incredibly high-end."
        },
        {
            q: "How secure is the built-in P2P WebRTC media pipeline?",
            a: "Completely private and decentralized. Camera and microphone pipelines bypass our databases entirely, establishing peer-to-peer tunnels using low-latency WebRTC streams. Furthermore, room owners are loaded with high-authority moderation credentials, giving them active triggers to force-mute participants or kick users to preserve session security."
        },
        {
            q: "Can we sketch on top of standard video streams in real-time?",
            a: "Absolutely. The built-in whiteboard layer overlays right on top of standard streaming containers. Every drag stroke is captured as high-fidelity SVG coordinates, broadcasted instantly to all socket peers, and drawn locally with zero canvas lag. It is perfect for highlighting frames, coaching, or simply having creative fun with friends."
        }
    ];

    const deepFeatures = [
        {
            title: "Millisecond Playback Core",
            subtitle: "Socket-Driven Latency Calibration",
            desc: "Engineered specifically for zero-delay co-watching. Rather than polling servers continuously, SyncRoom intercepts the direct state flow of the web media controller. Command synchronization coordinates seek events, speed rates, and pause frames within 15ms globally. The loop ensures you and your friends react to identical frames at the exact same heartbeat.",
            icon: <Zap className="text-amber-400 h-5 w-5" />,
            badge: "15ms Sync Accuracy",
            tech: ["Socket.IO Engine", "HTML5 Media Pipeline", "Buffer Offset Interpolator"],
            gradient: "from-amber-500 via-orange-500 to-rose-500"
        },
        {
            title: "Adaptive Bezel Orientations",
            subtitle: "Vertical Reels & Landscape Widescreen",
            desc: "Experience native device frames inside your browser. SyncRoom detects video dimensions automatically. If a vertical 9:16 mobile clip is shared, the layout morphs into an elegant floating smartphone glass bezel with responsive, soft shadow drops and ambient backlight filters. Landscape co-streams scale automatically into ultra-wide theater formats.",
            icon: <Laptop className="text-teal-400 h-5 w-5" />,
            badge: "Orientation Morpher",
            tech: ["Aspect Dimension Listeners", "Smartphone Bezel Renderers", "Backlit Drop Shadows"],
            gradient: "from-teal-500 via-emerald-500 to-cyan-500"
        },
        {
            title: "Hi-Fi WebRTC Media Grid",
            subtitle: "Decentralized Audio & Video Streams",
            desc: "Engage in crystal-clear P2P audio and video communication that fits perfectly next to your co-watching canvas. WebRTC coordinates live media pipes with automated echo cancellation and volume sliders. Active participants gain access to single-click Hand Raises, and hosts are equipped with authoritative force-mute and kick command blocks.",
            icon: <Video className="text-rose-400 h-5 w-5" />,
            badge: "Decentralized P2P",
            tech: ["WebRTC P2P Tunnels", "Host Command Modifiers", "Echo Cancellation Filters"],
            gradient: "from-rose-500 via-fuchsia-500 to-violet-500"
        },
        {
            title: "Shared Vector Whiteboard",
            subtitle: "Collaborative Coordinate Drawing Canvas",
            desc: "Annotate and sketch directly over your streams. The transparent overlay tracks mouse/touch paths, maps vectors, and redraws strokes on everyone's screen in real time. Features full pencil stroke thickness customization, quick eraser, and owner-only absolute canvas clearing controls, perfect for interactive presentations, reviews, or co-sketch play.",
            icon: <Edit3 className="text-indigo-400 h-5 w-5" />,
            badge: "Zero-Lag SVG Vectors",
            tech: ["HTML5 Vector Overlay", "High-Frequency Stroke Broadcasting", "Layer Synchronization"],
            gradient: "from-indigo-500 via-violet-500 to-purple-500"
        },
        {
            title: "Up Next Playlist Queue",
            subtitle: "Dual YouTube Search & Direct Link Piping",
            desc: "Manage media schedules effortlessly. Side-panels support direct URL drag-pasting of multiple links simultaneously, alongside a beautifully expanded direct YouTube Search engine. Search cards feature big, aspect-ratio thumbnails with clear channel titles, allowing peers to curate active rooms dynamically.",
            icon: <Layers className="text-fuchsia-400 h-5 w-5" />,
            badge: "Dynamic Playlist Pipeline",
            tech: ["YouTube Search V3 Proxy", "Batch Queue Parsers", "Instant Playlist Sync"],
            gradient: "from-fuchsia-500 via-pink-500 to-rose-500"
        }
    ];

    return (
        <div className="min-h-screen bg-[#050506] text-zinc-100 relative overflow-hidden noise selection:bg-indigo-600/40">
            {/* High-Vibe Bright Ambient Mesh Gradients */}
            <div className="absolute top-[-100px] left-1/4 w-[700px] h-[700px] rounded-full bg-gradient-to-tr from-indigo-600/10 via-fuchsia-500/10 to-transparent filter blur-[130px] pointer-events-none animate-pulse" />
            <div className="absolute bottom-[10%] right-1/4 w-[600px] h-[600px] rounded-full bg-gradient-to-br from-rose-500/10 via-violet-600/5 to-transparent filter blur-[120px] pointer-events-none" />
            <div className="absolute top-[40%] right-5 w-[400px] h-[400px] rounded-full bg-cyan-500/5 filter blur-[100px] pointer-events-none" />

            {/* ── Top Navigation Bar ─────────────────────────────────── */}
            <nav className="relative z-20 flex items-center justify-between px-6 md:px-16 py-5 border-b border-zinc-900 bg-[#050506]/75 backdrop-blur-xl">
                <div className="flex items-center gap-3.5">
                    <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center shadow-lg shadow-indigo-500/20 hover:scale-105 transition-transform duration-300">
                        <Play size={16} fill="white" className="ml-0.5 text-white" />
                    </div>
                    <div>
                        <span className="text-base font-extrabold tracking-tight text-white leading-none">SyncRoom</span>
                        <span className="text-[10px] block text-indigo-400 tracking-wider font-extrabold uppercase mt-0.5">Aesthetic Edition</span>
                    </div>
                </div>

                <div className="flex items-center gap-5">
                    <span className="text-xs text-zinc-400 font-mono hidden md:inline-flex items-center gap-1.5 bg-zinc-900/60 border border-zinc-800 px-3 py-1.5 rounded-full">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                        Status: Fully Optimized
                    </span>
                    <button
                        onClick={() => setShowGuestSignIn(true)}
                        className="px-5 py-2.5 text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 border border-indigo-500/30 rounded-xl transition-all duration-300 cursor-pointer active:scale-95 shadow-md shadow-indigo-600/20"
                    >
                        Sign In
                    </button>
                </div>
            </nav>

            {/* ── Hero Segment ────────────────────────────────────────── */}
            <section className="relative z-10 max-w-6xl mx-auto px-6 pt-16 md:pt-28 pb-12 flex flex-col lg:flex-row items-center gap-12">
                <div className="flex-1 text-left space-y-6">
                    <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full border border-indigo-500/30 bg-indigo-500/5 text-xs font-bold text-indigo-300 shadow-inner">
                        <Sparkles size={13} className="text-pink-400 animate-pulse" />
                        <span>The Ultimate Co-Watching Suite</span>
                    </div>

                    <h1 className="text-4xl sm:text-6xl md:text-7xl font-black tracking-tight leading-[1.05] text-white">
                        Watch Videos
                        <br />
                        Together. Synced
                        <br />
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-400 to-pink-400">
                            In Milliseconds.
                        </span>
                    </h1>

                    <p className="text-zinc-400 text-sm md:text-base leading-relaxed max-w-xl">
                        SyncRoom brings your group video streams, real-time voice, and shared creative vector whiteboards together into a gorgeous, lag-free browser suite. Upload vertical phone Reels, queue YouTube playlists, and co-watch seamlessly with crystal-clear P2P audio and video.
                    </p>

                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 pt-3">
                        <button
                            onClick={() => setShowGuestSignIn(true)}
                            className="inline-flex items-center justify-center gap-2.5 px-7 py-4 rounded-2xl bg-gradient-to-r from-indigo-600 via-purple-600 to-pink-600 text-white font-extrabold text-sm hover:shadow-[0_0_40px_rgba(99,102,241,0.3)] hover:scale-[1.01] active:scale-[0.99] transition-all duration-300 cursor-pointer"
                        >
                            Open Dashboard Console
                            <ArrowRight size={16} strokeWidth={2.5} />
                        </button>
                    </div>
                </div>

                {/* Aesthetic Interactive Video Box Simulation */}
                <div className="flex-1 w-full relative">
                    <div className="absolute inset-0 bg-gradient-to-r from-indigo-500 to-pink-500 rounded-3xl blur-[40px] opacity-10" />
                    
                    <div className="relative border border-zinc-800 bg-zinc-950/80 p-4 rounded-3xl shadow-2xl backdrop-blur-xl overflow-hidden">
                        <div className="flex items-center justify-between border-b border-zinc-900 pb-3 mb-3 text-xs text-zinc-400">
                            <div className="flex items-center gap-2">
                                <span className="w-2.5 h-2.5 rounded-full bg-red-500" />
                                <span className="w-2.5 h-2.5 rounded-full bg-yellow-500" />
                                <span className="w-2.5 h-2.5 rounded-full bg-green-500" />
                                <span className="ml-1 text-[10px] uppercase font-mono tracking-widest text-indigo-400 font-bold">Room Active</span>
                            </div>
                            <span className="font-mono text-[10px] bg-zinc-900 border border-zinc-800 px-2 py-0.5 rounded text-zinc-500">
                                4 Users Connected
                            </span>
                        </div>

                        {/* Interactive UI Mock Window */}
                        <div className="relative aspect-video rounded-2xl overflow-hidden bg-[#0a0a0e] border border-zinc-900 flex items-center justify-center group">
                            {/* Ambient glowing video mockup */}
                            <div className="absolute inset-0 bg-cover bg-center opacity-60 filter saturate-150" style={{ backgroundImage: "url('https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800')" }} />
                            
                            {/* Floating Camera Feeds Grid Simulation */}
                            <div className="absolute top-3 right-3 flex flex-col gap-2 z-10">
                                <div className="w-16 h-12 rounded-lg border border-white/20 overflow-hidden bg-zinc-900/90 shadow-md flex items-center justify-center">
                                    <div className="w-4 h-4 rounded-full bg-indigo-500/20 border border-indigo-400 animate-pulse flex items-center justify-center text-[7px] text-white">Ad</div>
                                </div>
                                <div className="w-16 h-12 rounded-lg border border-white/20 overflow-hidden bg-zinc-900/90 shadow-md flex items-center justify-center">
                                    <div className="w-4 h-4 rounded-full bg-pink-500/20 border border-pink-400 flex items-center justify-center text-[7px] text-white">Sa</div>
                                </div>
                            </div>

                            {/* Whiteboard Mock Vector Draw Lines */}
                            <svg className="absolute inset-0 w-full h-full pointer-events-none z-10">
                                <motion.path
                                    d="M 50,80 Q 150,30 250,90 T 350,110"
                                    fill="none"
                                    stroke="url(#svg-gradient)"
                                    strokeWidth="4"
                                    strokeLinecap="round"
                                    initial={{ pathLength: 0 }}
                                    animate={{ pathLength: 1 }}
                                    transition={{ duration: 3, repeat: Infinity, repeatType: "reverse" }}
                                />
                                <defs>
                                    <linearGradient id="svg-gradient" x1="0" y1="0" x2="1" y2="0">
                                        <stop offset="0%" stopColor="#818cf8" />
                                        <stop offset="100%" stopColor="#f472b6" />
                                    </linearGradient>
                                </defs>
                            </svg>

                            {/* Floating Emoji Popups Simulation */}
                            <div className="absolute bottom-4 left-4 z-20 flex gap-1.5">
                                <motion.span 
                                    className="text-lg" 
                                    animate={{ y: [-10, -80], opacity: [0, 1, 0] }} 
                                    transition={{ duration: 2.5, repeat: Infinity }}
                                >
                                    🔥
                                </motion.span>
                                <motion.span 
                                    className="text-lg" 
                                    animate={{ y: [-5, -60], opacity: [0, 1, 0] }} 
                                    transition={{ duration: 2, delay: 0.5, repeat: Infinity }}
                                >
                                    😂
                                </motion.span>
                                <motion.span 
                                    className="text-lg" 
                                    animate={{ y: [-8, -75], opacity: [0, 1, 0] }} 
                                    transition={{ duration: 2.8, delay: 1, repeat: Infinity }}
                                >
                                    ❤️
                                </motion.span>
                            </div>

                            <Play size={36} fill="white" className="text-white relative z-15 drop-shadow-md cursor-pointer hover:scale-110 transition-transform" />
                        </div>
                    </div>
                </div>
            </section>

            {/* ── Visual Architecture Overview ─────────────────────────── */}
            <section className="relative z-10 max-w-5xl mx-auto px-6 py-12">
                <div className="border border-zinc-900 bg-zinc-950/40 rounded-3xl p-6 md:p-8 backdrop-blur-md">
                    <div className="text-center mb-8">
                        <span className="text-xs uppercase font-black text-indigo-400 tracking-wider">System Flow</span>
                        <h3 className="text-2xl font-black text-white mt-1">Real-Time Core Architecture</h3>
                        <p className="text-xs text-zinc-400 max-w-md mx-auto mt-2 leading-relaxed">
                            SyncRoom maps and syncs assets instantly. Review our pipeline mapping model below:
                        </p>
                    </div>

                    {/* Architectural Flow Layout */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-center">
                        <div className="bg-zinc-900/40 border border-zinc-800 p-5 rounded-2xl relative space-y-2 group hover:border-indigo-500/30 transition-colors">
                            <div className="w-9 h-9 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center mx-auto">
                                <Layers size={16} />
                            </div>
                            <h4 className="text-xs font-extrabold uppercase text-white tracking-wide">1. Central Socket Controller</h4>
                            <p className="text-[11px] text-zinc-400 leading-normal">
                                Intercepts HTML5 media triggers, generates time logs, and pipes millisecond coordinate events to all active connected browsers.
                            </p>
                        </div>
                        <div className="bg-zinc-900/40 border border-zinc-800 p-5 rounded-2xl relative space-y-2 group hover:border-purple-500/30 transition-colors">
                            <div className="w-9 h-9 rounded-xl bg-purple-500/10 text-purple-400 flex items-center justify-center mx-auto">
                                <Video size={16} />
                            </div>
                            <h4 className="text-xs font-extrabold uppercase text-white tracking-wide">2. WebRTC P2P Media Loop</h4>
                            <p className="text-[11px] text-zinc-400 leading-normal">
                                Establishes low-latency voice and video grids, complete with high-priority moderator mute tools and hand raised visual cues.
                            </p>
                        </div>
                        <div className="bg-zinc-900/40 border border-zinc-800 p-5 rounded-2xl relative space-y-2 group hover:border-pink-500/30 transition-colors">
                            <div className="w-9 h-9 rounded-xl bg-pink-500/10 text-pink-400 flex items-center justify-center mx-auto">
                                <Edit3 size={16} />
                            </div>
                            <h4 className="text-xs font-extrabold uppercase text-white tracking-wide">3. SVG Drawing Pipeline</h4>
                            <p className="text-[11px] text-zinc-400 leading-normal">
                                Tracks vector strokes, broadcasts points globally, and renders real-time sketch lines directly over standard co-streaming elements.
                            </p>
                        </div>
                    </div>
                </div>
            </section>

            {/* ── Deep-Dive Interactive Features Segment ───────────────── */}
            <section className="relative z-10 max-w-5xl mx-auto px-6 py-16">
                <div className="text-center mb-12">
                    <span className="text-xs uppercase font-extrabold text-pink-400 tracking-widest block mb-1.5">Deep Feature Presentation</span>
                    <h2 className="text-3xl sm:text-4xl font-extrabold text-white">Built for Hardware-Accelerated Performance</h2>
                    <p className="text-xs text-zinc-500 max-w-lg mx-auto mt-2 leading-relaxed">
                        SyncRoom is built without heavy frames or bloated modules. Review each key system component in deep detail:
                    </p>
                </div>

                {/* Dynamic Tab Switcher */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                    
                    {/* Left: Tab Selectors */}
                    <div className="lg:col-span-5 flex flex-col gap-2.5">
                        {deepFeatures.map((feat, idx) => {
                            const isActive = activeFeature === idx;
                            return (
                                <button
                                    key={idx}
                                    onClick={() => setActiveFeature(idx)}
                                    className={`w-full flex items-center justify-between p-4 rounded-2xl border text-left cursor-pointer transition-all duration-300 ${
                                        isActive 
                                            ? "bg-zinc-900 border-zinc-800 shadow-lg text-white scale-[1.01]" 
                                            : "border-transparent text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/30"
                                    }`}
                                >
                                    <div className="flex items-center gap-3.5 min-w-0">
                                        <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 bg-zinc-950 border border-zinc-900`}>
                                            {feat.icon}
                                        </div>
                                        <div className="min-w-0">
                                            <h4 className="text-xs font-bold leading-tight">{feat.title}</h4>
                                            <p className="text-[10px] text-zinc-500 truncate leading-none mt-1">{feat.subtitle}</p>
                                        </div>
                                    </div>
                                    <ArrowRight size={12} className={`transition-transform duration-300 ${isActive ? "text-indigo-400 translate-x-1" : "text-zinc-600"}`} />
                                </button>
                            );
                        })}
                    </div>

                    {/* Right: Technical Spec Card */}
                    <div className="lg:col-span-7">
                        <AnimatePresence mode="wait">
                            <motion.div
                                key={activeFeature}
                                initial={{ opacity: 0, y: 15 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -15 }}
                                transition={{ duration: 0.35 }}
                                className="bg-gradient-to-br from-zinc-950 to-zinc-900 border border-zinc-800/80 p-6 md:p-8 rounded-3xl shadow-xl flex flex-col justify-between min-h-[380px] relative overflow-hidden"
                            >
                                {/* Glowing corner orb specific to active gradient */}
                                <div className={`absolute top-0 right-0 w-24 h-24 rounded-full bg-gradient-to-br ${deepFeatures[activeFeature].gradient} blur-[50px] opacity-25`} />

                                <div className="space-y-5">
                                    <div className="flex items-center gap-2">
                                        <span className="text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 shadow-sm">
                                            {deepFeatures[activeFeature].badge}
                                        </span>
                                        <span className="text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-md bg-zinc-900 text-zinc-400 border border-zinc-800">
                                            SyncRoom Core
                                        </span>
                                    </div>

                                    <h3 className="text-xl sm:text-2xl font-black text-white leading-snug">
                                        {deepFeatures[activeFeature].title}
                                    </h3>

                                    <p className="text-xs text-zinc-400 leading-relaxed">
                                        {deepFeatures[activeFeature].desc}
                                    </p>

                                    <div className="p-4 rounded-2xl bg-zinc-950/80 border border-zinc-900/80 text-[11px] leading-relaxed text-zinc-300">
                                        <span className="font-extrabold text-zinc-400 uppercase tracking-widest text-[9px] block mb-1.5 flex items-center gap-1.5">
                                            <Info size={11} className="text-indigo-400" /> System Architecture Detail
                                        </span>
                                        Ideal for remote watching, sketching overlays, co-gaming, and hosting coordinated webinars.
                                    </div>
                                </div>

                                <div className="space-y-2 pt-4 border-t border-zinc-900/85 mt-4">
                                    <span className="text-[9px] font-bold uppercase text-zinc-500 tracking-wider block">Tech Stack Modules</span>
                                    <div className="flex flex-wrap gap-1.5">
                                        {deepFeatures[activeFeature].tech.map((techItem) => (
                                            <span 
                                                key={techItem} 
                                                className="text-[10px] font-mono bg-zinc-900 border border-zinc-850 px-2.5 py-1 rounded-lg text-zinc-300 font-medium"
                                            >
                                                {techItem}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </motion.div>
                        </AnimatePresence>
                    </div>

                </div>
            </section>

            {/* ── Interactive Clicking FAQ Accordion ───────────────────── */}
            <section className="relative z-10 max-w-4xl mx-auto px-6 py-12">
                <div className="text-center mb-10">
                    <span className="w-8 h-8 rounded-full bg-indigo-500/10 flex items-center justify-center mx-auto text-indigo-400 mb-2">
                        <HelpCircle size={16} />
                    </span>
                    <h3 className="text-2xl font-black text-white">Frequently Asked Questions</h3>
                    <p className="text-xs text-zinc-400 mt-1">
                        Click on any question below to expand the detailed technical explanation.
                    </p>
                </div>

                <div className="space-y-3">
                    {faqItems.map((item, idx) => {
                        const isExpanded = expandedFaq === idx;
                        return (
                            <div 
                                key={idx}
                                className="border border-zinc-900 bg-zinc-950/60 rounded-2xl overflow-hidden transition-all duration-300"
                            >
                                <button
                                    onClick={() => setExpandedFaq(isExpanded ? null : idx)}
                                    className="w-full flex items-center justify-between p-5 text-left font-semibold text-xs sm:text-sm text-zinc-100 hover:text-white transition-colors cursor-pointer select-none"
                                >
                                    <span className="pr-4">{item.q}</span>
                                    <ChevronDown 
                                        size={16} 
                                        className={`text-zinc-500 shrink-0 transition-transform duration-300 ${isExpanded ? "transform rotate-180 text-indigo-400" : ""}`} 
                                    />
                                </button>
                                
                                <AnimatePresence initial={false}>
                                    {isExpanded && (
                                        <motion.div
                                            initial={{ height: 0, opacity: 0 }}
                                            animate={{ height: "auto", opacity: 1 }}
                                            exit={{ height: 0, opacity: 0 }}
                                            transition={{ duration: 0.25, ease: "easeInOut" }}
                                        >
                                            <div className="px-5 pb-5 pt-1 text-xs text-zinc-400 leading-relaxed border-t border-zinc-900/60 bg-zinc-950/20">
                                                {item.a}
                                            </div>
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </div>
                        );
                    })}
                </div>
            </section>

            {/* ── Footer ───────────────────────────────────────────────── */}
            <footer className="relative z-10 border-t border-zinc-900 bg-[#050506] py-12 px-6 text-center">
                <div className="max-w-5xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
                    <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-xl bg-gradient-to-tr from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center">
                            <Play size={11} fill="white" className="ml-0.5 text-white" />
                        </div>
                        <span className="text-xs font-extrabold text-white uppercase tracking-wider">SyncRoom</span>
                    </div>

                    <p className="text-[10px] text-zinc-500 font-mono">
                        Handcrafted with precision using Next.js 14, WebRTC, PeerJS, and Socket.IO &middot; © {new Date().getFullYear()}
                    </p>

                    <div className="flex gap-4">
                        <button 
                            onClick={() => signIn("google", { callbackUrl: "/dashboard" })}
                            className="text-[10px] text-indigo-400 hover:text-indigo-300 transition-colors uppercase tracking-wider font-extrabold cursor-pointer"
                        >
                            Open Dashboard Console
                        </button>
                    </div>
                </div>
            </footer>

            {showGuestSignIn && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 px-4 backdrop-blur-sm"
                    onMouseDown={(event) => {
                        if (event.target === event.currentTarget) setShowGuestSignIn(false);
                    }}
                >
                    <form
                        onSubmit={handleSignIn}
                        className="w-full max-w-md rounded-2xl border border-zinc-800 bg-zinc-950 p-6 shadow-2xl"
                    >
                        <div className="mb-5 flex items-start justify-between">
                            <div>
                                <h2 className="text-lg font-bold text-white">Join SyncRoom</h2>
                                <p className="mt-1 text-sm text-zinc-400">
                                    Choose the name others will see in the room.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setShowGuestSignIn(false)}
                                className="rounded-md p-1 text-zinc-400 hover:bg-zinc-800 hover:text-white"
                                aria-label="Close sign-in form"
                            >
                                <X size={18} />
                            </button>
                        </div>

                        <label className="mb-1.5 block text-sm font-medium text-zinc-200" htmlFor="guest-name">
                            Display name
                        </label>
                        <input
                            id="guest-name"
                            name="name"
                            autoComplete="nickname"
                            required
                            maxLength={60}
                            value={guestName}
                            onChange={(event) => setGuestName(event.target.value)}
                            placeholder="For example, Alex"
                            className="mb-4 h-11 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 text-sm text-white outline-none placeholder:text-zinc-500 focus:border-indigo-500"
                        />

                        <label className="mb-1.5 block text-sm font-medium text-zinc-200" htmlFor="guest-email">
                            Email
                        </label>
                        <input
                            id="guest-email"
                            name="email"
                            type="email"
                            autoComplete="email"
                            required
                            maxLength={254}
                            value={guestEmail}
                            onChange={(event) => setGuestEmail(event.target.value)}
                            placeholder="you@example.com"
                            className="h-11 w-full rounded-lg border border-zinc-700 bg-zinc-900 px-3 text-sm text-white outline-none placeholder:text-zinc-500 focus:border-indigo-500"
                        />
                        <p className="mt-2 text-xs text-zinc-500">
                            This is a guest identity for testing; no account is created.
                        </p>

                        {signInError && (
                            <p role="alert" className="mt-3 text-sm text-red-400">
                                {signInError}
                            </p>
                        )}

                        <button
                            type="submit"
                            disabled={isSigningIn}
                            className="mt-5 h-11 w-full rounded-lg bg-indigo-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-indigo-500 disabled:cursor-wait disabled:opacity-60"
                        >
                            {isSigningIn ? "Signing in..." : "Continue as guest"}
                        </button>
                    </form>
                </div>
            )}
        </div>
    );
}
