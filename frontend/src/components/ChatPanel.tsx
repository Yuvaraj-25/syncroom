"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { Socket } from "socket.io-client";
import { useRoomStore } from "@/store/useRoomStore";
import { useSession } from "next-auth/react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Send, Smile, X } from "lucide-react";

// ── Comprehensive emoji list organised by category ──────────────────
const EMOJI_CATEGORIES: { name: string; icon: string; emojis: string[] }[] = [
    {
        name: "Smileys",
        icon: "😀",
        emojis: [
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
            "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "🥲","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢",
            "🫣","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥",
            "😏","😒","🙄","😬","🤥","😌","😔","😪","🤤","😴",
            "😷","🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","🤯",
            "🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁",
            "😮","😯","😲","😳","🥺","🥹","😦","😧","😨","😰",
            "😥","😢","😭","😱","😖","😣","😞","😓","😩","😫",
            "🥱","😤","😡","😠","🤬","😈","👿","💀","☠️","💩",
            "🤡","👹","👺","👻","👽","👾","🤖",
        ],
    },
    {
        name: "Gestures",
        icon: "👋",
        emojis: [
            "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴","👌",
            "🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
            "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛",
            "🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
            "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠",
            "🫀","🫁","🦷","🦴","👀","👁️","👅","👄","🫦",
        ],
    },
    {
        name: "People",
        icon: "👤",
        emojis: [
            "👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓",
            "👴","👵","🙍","🙎","🙅","🙆","💁","🙋","🧏","🙇",
            "🤦","🤷","💆","💇","🚶","🧍","🧎","🏃","💃","🕺",
            "👯","🧖","🧗","🤸","⛹️","🏋️","🚴","🚵","🤼","🤽",
            "🤾","🤺","⛷️","🏂","🏌️","🏇","🧘","🛀","🛌",
        ],
    },
    {
        name: "Hearts",
        icon: "❤️",
        emojis: [
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
            "❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝",
            "💟","♥️","💋","💌","💐","🌹","🥀","💍","💎",
        ],
    },
    {
        name: "Animals",
        icon: "🐶",
        emojis: [
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨",
            "🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐒",
            "🐔","🐧","🐦","🐤","🐣","🐥","🦆","🦅","🦉","🦇",
            "🐺","🐗","🐴","🦄","🐝","🪱","🐛","🦋","🐌","🐞",
            "🐜","🪰","🪲","🪳","🦟","🦗","🕷️","🦂","🐢","🐍",
            "🦎","🦖","🦕","🐙","🦑","🦐","🦞","🦀","🪸","🐡",
            "🐠","🐟","🐬","🐳","🐋","🦈","🦭","🐊","🐅","🐆",
            "🦓","🦍","🦧","🐘","🦛","🦏","🐪","🐫","🦒","🦘",
            "🦬","🐃","🐂","🐄","🐎","🐖","🐏","🐑","🦙","🐐",
            "🦌","🐕","🐩","🦮","🐈","🐈‍⬛","🪶","🐓","🦃","🦤",
            "🦚","🦜","🦢","🦩","🕊️","🐇","🦝","🦨","🦡","🦫",
            "🦦","🦥","🐁","🐀","🐿️","🦔",
        ],
    },
    {
        name: "Food",
        icon: "🍕",
        emojis: [
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐",
            "🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑",
            "🫛","🥦","🥬","🥒","🌶️","🫑","🌽","🥕","🫒","🧄",
            "🧅","🥔","🍠","🫚","🥐","🥖","🍞","🥨","🥯","🧀",
            "🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🦴",
            "🌭","🍔","🍟","🍕","🫓","🥪","🥙","🧆","🌮","🌯",
            "🫔","🥗","🥘","🫕","🥫","🍝","🍜","🍲","🍛","🍣",
            "🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥠","🥮",
            "🍢","🍡","🍧","🍨","🍦","🥧","🧁","🍰","🎂","🍮",
            "🍭","🍬","🍫","🍿","🍩","🍪","🌰","🥜","🍯","🥛",
            "☕","🫖","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂",
            "🍷","🫗","🥃","🍸","🍹","🧉","🍾","🧊","🥄","🍴",
            "🍽️","🥣","🥡","🥢",
        ],
    },
    {
        name: "Activities",
        icon: "⚽",
        emojis: [
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
            "🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
            "🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷",
            "⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️",
            "🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽","🚣","🧗",
            "🚴","🚵","🎪","🎗️","🎟️","🎫","🎖️","🏆","🏅","🥇",
            "🥈","🥉","🎃","🎄","🎆","🎇","🧨","✨","🎈","🎉",
            "🎊","🎋","🎍","🎎","🎏","🎐","🎑","🧧","🎀","🎁",
            "🎯","🎮","🕹️","🎰","🎲","🧩","🧸","🪄","🪅","🪆",
            "🪩","♠️","♥️","♦️","♣️","♟️","🃏","🀄","🎴",
        ],
    },
    {
        name: "Travel",
        icon: "✈️",
        emojis: [
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐",
            "🛻","🚚","🚛","🚜","🛵","🏍️","🛺","🚲","🛴","🛹",
            "🚏","🛣️","🛤️","🛞","⛽","🛞","🚨","🚥","🚦","🛑",
            "🚧","⚓","🛟","⛵","🛶","🚤","🛳️","⛴️","🛥️","🚢",
            "✈️","🛩️","🛫","🛬","🪂","💺","🚁","🚟","🚠","🚡",
            "🛰️","🚀","🛸","🌍","🌎","🌏","🗺️","🧭","🏔️","⛰️",
            "🌋","🗻","🏕️","🏖️","🏜️","🏝️","🏞️","🏟️","🏛️","🏗️",
            "🧱","🪨","🪵","🛖","🏘️","🏚️","🏠","🏡","🏢","🏣",
            "🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯",
            "🏰","💒","🗼","🗽","⛪","🕌","🛕","🕍","⛩️","🕋",
        ],
    },
    {
        name: "Objects",
        icon: "💡",
        emojis: [
            "⌚","📱","📲","💻","⌨️","🖥️","🖨️","🖱️","🖲️","🕹️",
            "🗜️","💽","💾","💿","📀","📼","📷","📸","📹","🎥",
            "📽️","🎞️","📞","☎️","📟","📠","📺","📻","🎙️","🎚️",
            "🎛️","🧭","⏱️","⏲️","⏰","🕰️","⌛","⏳","📡","🔋",
            "🪫","🔌","💡","🔦","🕯️","🪔","🧯","🛢️","💸","💵",
            "💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜","🧰",
            "🪛","🔧","🔨","⚒️","🛠️","⛏️","🪚","🔩","⚙️","🪤",
            "🧱","⛓️","🧲","🔫","💣","🧨","🪓","🔪","🗡️","⚔️",
            "🛡️","🚬","⚰️","🪦","⚱️","🏺","🔮","📿","🧿","🪬",
            "💈","⚗️","🔭","🔬","🕳️","🩹","🩺","🩻","🩼","💊",
            "💉","🩸","🧬","🦠","🧫","🧪","🌡️","🧹","🪠","🧺",
            "🧻","🚽","🚰","🚿","🛁","🛀","🧼","🪥","🪒","🧽",
            "🪣","🧴","🛎️","🔑","🗝️","🚪","🪑","🛋️","🛏️","🛌",
            "🧸","🪆","🖼️","🪞","🪟","🛍️","🛒","🎁","🎈","🎏",
        ],
    },
    {
        name: "Symbols",
        icon: "💯",
        emojis: [
            "💯","🔥","💫","⭐","🌟","✨","⚡","💥","💢","💦",
            "💨","🕳️","💬","👁️‍🗨️","🗨️","🗯️","💭","💤","🔔","🔕",
            "🎵","🎶","🔇","🔈","🔉","🔊","📢","📣","✅","❌",
            "❓","❔","❕","❗","‼️","⁉️","⚠️","🚸","♻️","✳️",
            "❇️","🔆","🔅","🔱","⚜️","🔰","♾️","💠","Ⓜ️","🌀",
            "💤","🏧","🚮","🚰","♿","🚹","🚺","🚻","🚼","🚾",
            "🛂","🛃","🛄","🛅","⬆️","↗️","➡️","↘️","⬇️","↙️",
            "⬅️","↖️","↕️","↔️","↩️","↪️","⤴️","⤵️","🔃","🔄",
            "🔙","🔚","🔛","🔜","🔝","🔀","🔁","🔂","▶️","⏩",
            "⏭️","⏯️","◀️","⏪","⏮️","🔼","⏫","🔽","⏬","⏸️",
            "⏹️","⏺️","⏏️","🔘","🔳","🔲","🏁","🚩","🎌","🏴",
            "🏳️","🏳️‍🌈","🏳️‍⚧️","🏴‍☠️",
        ],
    },
];

interface ChatPanelProps {
    socket: Socket;
    roomId: string;
}

export default function ChatPanel({ socket, roomId }: ChatPanelProps) {
    const { messages } = useRoomStore();
    const { data: session } = useSession();
    const [text, setText] = useState("");
    const [showEmojiPicker, setShowEmojiPicker] = useState(false);
    const [activeEmojiCategory, setActiveEmojiCategory] = useState(0);
    const [typingUsers, setTypingUsers] = useState<Set<string>>(new Set());
    
    const scrollRef = useRef<HTMLDivElement>(null);
    const emojiPickerRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const typingTimeoutsRef = useRef<{ [key: string]: NodeJS.Timeout }>({});
    const lastTypingEmitRef = useRef(0);
    const [chatSoundEnabled, setChatSoundEnabled] = useState(true);
    const prevMessageCountRef = useRef(messages.length);

    // Play a subtle notification sound
    const playNotificationSound = useCallback(() => {
        if (!chatSoundEnabled) return;
        try {
            const ctx = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)();
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.frequency.setValueAtTime(880, ctx.currentTime);
            osc.frequency.setValueAtTime(1100, ctx.currentTime + 0.05);
            gain.gain.setValueAtTime(0.08, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);
            osc.start(ctx.currentTime);
            osc.stop(ctx.currentTime + 0.3);
        } catch { /* Audio not available */ }
    }, [chatSoundEnabled]);

    // Auto-scroll to bottom + play sound for new messages
    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
        // Play sound if a new message arrived from someone else
        if (messages.length > prevMessageCountRef.current) {
            const latest = messages[messages.length - 1];
            if (latest && latest.userName !== session?.user?.name) {
                playNotificationSound();
            }
        }
        prevMessageCountRef.current = messages.length;
    }, [messages, session, playNotificationSound]);

    // Close emoji picker when clicking outside
    useEffect(() => {
        const handleClickOutside = (e: MouseEvent) => {
            if (emojiPickerRef.current && !emojiPickerRef.current.contains(e.target as Node)) {
                setShowEmojiPicker(false);
            }
        };
        if (showEmojiPicker) {
            document.addEventListener("mousedown", handleClickOutside);
        }
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, [showEmojiPicker]);

    // Handle typing events from socket
    useEffect(() => {
        const handleTyping = ({ userName }: { userName: string }) => {
            if (!userName || userName === session?.user?.name) return;
            
            setTypingUsers(prev => {
                const next = new Set(prev);
                next.add(userName);
                return next;
            });
            
            if (typingTimeoutsRef.current[userName]) {
                clearTimeout(typingTimeoutsRef.current[userName]);
            }
            
            typingTimeoutsRef.current[userName] = setTimeout(() => {
                setTypingUsers(prev => {
                    const next = new Set(prev);
                    next.delete(userName);
                    return next;
                });
                delete typingTimeoutsRef.current[userName];
            }, 2000);
        };

        socket.on("typing", handleTyping);
        return () => {
            socket.off("typing", handleTyping);
        };
    }, [socket, session]);

    const handleSend = (e: React.FormEvent) => {
        e.preventDefault();
        if (!text.trim() || !session?.user) return;

        const newMessage = {
            id: Math.random().toString(36).substring(2, 9),
            userId: (session.user as { id?: string }).id || "anonymous",
            userName: session.user.name || "Guest",
            userImage: session.user.image || "",
            text: text.trim(),
            timestamp: new Date().toISOString()
        };

        socket.emit("chat-message", { roomId, message: newMessage });
        setText("");
    };

    const handleTextChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setText(e.target.value);
        
        const now = Date.now();
        if (now - lastTypingEmitRef.current > 1000) {
            if (session?.user?.name) {
                socket.emit("typing", { roomId, userName: session.user.name });
                lastTypingEmitRef.current = now;
            }
        }
    };

    const insertEmoji = (emoji: string) => {
        setText(prev => prev + emoji);
        inputRef.current?.focus();
    };

    return (
        <div className="flex flex-col h-full">
            <div className="p-4 border-b border-zinc-800/50 flex items-center justify-between">
                <h2 className="font-semibold text-sm text-zinc-200">Chat</h2>
                <button
                    onClick={() => setChatSoundEnabled(!chatSoundEnabled)}
                    className={`text-xs px-2 py-0.5 rounded-md transition-colors cursor-pointer ${
                        chatSoundEnabled
                            ? "bg-indigo-500/10 text-indigo-400"
                            : "bg-zinc-800 text-zinc-500"
                    }`}
                    title={chatSoundEnabled ? "Mute chat sounds" : "Unmute chat sounds"}
                >
                    {chatSoundEnabled ? "🔔" : "🔕"}
                </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4" ref={scrollRef}>
                {messages.length === 0 ? (
                    <div className="h-full flex items-center justify-center text-zinc-500 text-sm">
                        No messages yet. Say hello!
                    </div>
                ) : (
                    messages.map((m) => {
                        const isMe = session?.user?.name === m.userName;
                        return (
                            <div key={m.id} className={`flex flex-col ${isMe ? 'items-end' : 'items-start'} animate-in slide-in-from-bottom-2 fade-in duration-300`}>
                                <div className="flex items-end gap-2 max-w-[85%]">
                                    {!isMe && (
                                        // eslint-disable-next-line @next/next/no-img-element
                                        <img
                                            src={m.userImage || `https://api.dicebear.com/7.x/initials/svg?seed=${m.userName}`}
                                            alt=""
                                            className="w-6 h-6 rounded-full shrink-0 shadow-sm"
                                        />
                                    )}
                                    <div className={`rounded-2xl px-4 py-2 text-sm shadow-md backdrop-blur-md ${
                                        isMe
                                        ? 'bg-indigo-600/90 text-white rounded-br-sm border border-indigo-500/50'
                                        : (m as any).isAi
                                            ? 'bg-gradient-to-r from-purple-500/20 to-indigo-500/20 text-zinc-200 rounded-bl-sm border border-purple-500/30'
                                            : 'bg-zinc-800/80 text-zinc-200 rounded-bl-sm border border-zinc-700/50'
                                        }`}>
                                        {!isMe && <div className={`text-xs font-semibold mb-1 ${(m as any).isAi ? 'text-purple-400' : 'text-indigo-400'}`}>{m.userName}</div>}
                                        <div className={(m as any).isAi ? 'whitespace-pre-wrap' : ''}>{m.text}</div>
                                    </div>
                                </div>
                                <div className="text-[10px] text-zinc-500 mt-1 mx-8 font-mono">
                                    {new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                                </div>
                            </div>
                        );
                    })
                )}
            </div>

            {/* Input Area */}
            <div className="p-3 border-t border-zinc-800/50 relative">
                {/* ── Emoji Picker ─────────────────────────────────────── */}
                {showEmojiPicker && (
                    <div
                        ref={emojiPickerRef}
                        className="absolute bottom-full left-2 right-2 mb-2 bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl overflow-hidden z-50 flex flex-col"
                        style={{ maxHeight: "340px" }}
                    >
                        {/* Category tabs */}
                        <div className="flex items-center gap-0.5 px-2 py-1.5 border-b border-zinc-800 overflow-x-auto shrink-0 scrollbar-thin scrollbar-thumb-zinc-700">
                            {EMOJI_CATEGORIES.map((cat, idx) => (
                                <button
                                    key={cat.name}
                                    onClick={() => setActiveEmojiCategory(idx)}
                                    title={cat.name}
                                    className={`shrink-0 p-1.5 rounded-lg text-base transition-colors ${
                                        idx === activeEmojiCategory
                                            ? "bg-indigo-600/30 ring-1 ring-indigo-500"
                                            : "hover:bg-zinc-800"
                                    }`}
                                >
                                    {cat.icon}
                                </button>
                            ))}
                            <div className="flex-1" />
                            <button
                                onClick={() => setShowEmojiPicker(false)}
                                className="shrink-0 p-1 rounded-lg hover:bg-zinc-800 text-zinc-400 hover:text-white transition-colors"
                            >
                                <X size={14} />
                            </button>
                        </div>

                        {/* Category label */}
                        <div className="px-3 pt-2 pb-1 text-xs font-semibold text-zinc-400">
                            {EMOJI_CATEGORIES[activeEmojiCategory].name}
                        </div>

                        {/* Emoji grid */}
                        <div className="flex-1 overflow-y-auto px-2 pb-2">
                            <div className="grid grid-cols-8 gap-0.5">
                                {EMOJI_CATEGORIES[activeEmojiCategory].emojis.map((emoji, i) => (
                                    <button
                                        key={`${emoji}-${i}`}
                                        onClick={() => insertEmoji(emoji)}
                                        className="p-1.5 rounded-lg hover:bg-zinc-800 active:scale-90 transition-all text-xl leading-none text-center"
                                    >
                                        {emoji}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>
                )}

                {/* Typing Indicator */}
                {typingUsers.size > 0 && (
                    <div className="absolute -top-6 left-4 text-xs text-zinc-400 italic">
                        {Array.from(typingUsers).join(", ")} {typingUsers.size === 1 ? "is typing..." : "are typing..."}
                    </div>
                )}

                <form onSubmit={handleSend} className="relative flex items-center">
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className={`absolute left-1 hover:text-white transition-colors ${
                            showEmojiPicker ? "text-indigo-400" : "text-zinc-400"
                        }`}
                        onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                    >
                        <Smile size={18} />
                    </Button>
                    <Input
                        ref={inputRef}
                        value={text}
                        onChange={handleTextChange}
                        placeholder="Type a message..."
                        className="w-full bg-zinc-950 border-zinc-800 pl-10 pr-10 focus-visible:ring-indigo-500 rounded-full h-10"
                    />
                    <Button
                        type="submit"
                        size="icon"
                        className="absolute right-1 h-8 w-8 rounded-full bg-indigo-600 hover:bg-indigo-700 text-white shadow-md disabled:opacity-50"
                        disabled={!text.trim()}
                    >
                        <Send size={14} className="ml-0.5" />
                    </Button>
                </form>
            </div>
        </div>
    );
}
