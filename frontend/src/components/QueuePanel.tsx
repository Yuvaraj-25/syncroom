/* eslint-disable @next/next/no-img-element */
"use client";

import React, { useState } from "react";
import { Socket } from "socket.io-client";
import { useRoomStore } from "@/store/useRoomStore";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { 
    ListVideo, Plus, Trash2, Play, X, Film, Search, Key, Sparkles, Loader2
} from "lucide-react";
import { useSettingsStore } from "@/store/useSettingsStore";

interface SearchResultSnippet {
    title: string;
    channelTitle: string;
    thumbnails: {
        medium?: { url: string };
        default?: { url: string };
    };
}

interface SearchResultItem {
    id: {
        videoId: string;
    };
    snippet: SearchResultSnippet;
}

interface QueuePanelProps {
    socket: Socket;
    roomId: string;
    onClose?: () => void;
    onActiveTabChange?: (tab: "link" | "search") => void;
}

export default function QueuePanel({ socket, roomId, onClose, onActiveTabChange }: QueuePanelProps) {
    const { videoQueue, videoHistory, users } = useRoomStore();
    const { youtubeApiKey, setYoutubeApiKey } = useSettingsStore();

    const [inputUrl, setInputUrl] = useState("");
    const [isLoading, setIsLoading] = useState(false);

    // Search Tab states
    const [activeTab, setActiveTab] = useState<"link" | "search">("link");
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState<SearchResultItem[]>([]);
    const [apiKeyInput, setApiKeyInput] = useState("");
    const [isSearching, setIsSearching] = useState(false);
    const [serverHasNoKey, setServerHasNoKey] = useState(false);

    const localUser = users.find(u => u.id === socket.id);
    const isOwner = localUser?.isOwner ?? false;

    const extractYouTubeId = (urlStr: string) => {
        const regex = /(?:youtube\.com\/(?:[^\/]+\/.+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/)([^"&?\/\s]{11})/i;
        const match = urlStr.match(regex);
        return match ? match[1] : null;
    };

    const handleAddToQueue = async (e?: React.FormEvent) => {
        if (e) e.preventDefault();
        
        // Split by newlines, commas, or spaces to support batch pasting of unlimited links
        const urls = inputUrl.split(/[\n,\s]+/).map(u => u.trim()).filter(u => u !== "");
        if (urls.length === 0) return;

        setIsLoading(true);
        let addedCount = 0;
        
        for (const url of urls) {
            const videoId = extractYouTubeId(url);
            if (videoId) {
                let title = "YouTube Video";
                let channelName = "YouTube Creator";
                try {
                    const res = await fetch(`https://noembed.com/embed?url=${encodeURIComponent(url)}`);
                    if (res.ok) {
                        const data = await res.json();
                        if (data) {
                            if (data.title) title = data.title;
                            if (data.author_name) channelName = data.author_name;
                        }
                    }
                } catch (err) {
                    console.warn("Could not fetch details:", err);
                }

                const thumbnail = `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;

                socket.emit("add-to-queue", {
                    roomId,
                    videoInfo: {
                        url,
                        title,
                        thumbnail,
                        addedBy: localUser?.name || "Guest",
                        channelName
                    }
                });
                addedCount++;
            }
        }

        setIsLoading(false);
        if (addedCount > 0) {
            setInputUrl("");
        } else {
            alert("Please paste one or more valid YouTube URLs.");
        }
    };

    const handlePlayQueueItem = (index: number) => {
        if (!isOwner) return;
        const item = videoQueue[index];
        if (!item) return;

        const url = typeof item === "string" ? item : item.url;
        const videoId = extractYouTubeId(url);
        if (videoId) {
            socket.emit("video-url-change", { roomId, newUrl: url });
            socket.emit("remove-from-queue", { roomId, index });
        }
    };

    const handlePlayHistoryItem = (url: string) => {
        if (!isOwner) return;
        socket.emit("video-url-change", { roomId, newUrl: url });
    };

    const handleRemoveHistoryItem = (url: string) => {
        if (!isOwner) return;
        socket.emit("remove-video-history", { roomId, url });
    };

    const handleClearVideoHistory = () => {
        if (!isOwner || !window.confirm("Clear all recently played videos for this room?")) return;
        socket.emit("clear-video-history", { roomId });
    };

    const handleRemoveFromQueue = (index: number) => {
        if (!isOwner) return;
        socket.emit("remove-from-queue", { roomId, index });
    };

    // YouTube Search Handlers
    const handleSaveApiKey = (e: React.FormEvent) => {
        e.preventDefault();
        if (!apiKeyInput.trim()) return;
        setYoutubeApiKey(apiKeyInput.trim());
        setApiKeyInput("");
    };

    const handleSearch = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;

        setIsSearching(true);
        const apiUrl = process.env.NEXT_PUBLIC_SOCKET_URL?.replace(/\/$/, '') || "http://localhost:4000";

        try {
            // 1. Try secure backend proxy search first
            const res = await fetch(`${apiUrl}/api/youtube/search?q=${encodeURIComponent(searchQuery)}`);

            if (res.ok) {
                const data = await res.json();
                if (data && data.items) {
                    setSearchResults(data.items);
                    setServerHasNoKey(false);
                    setIsSearching(false);
                    return;
                }
            }

            // If backend returned 404 (indicates server has no API key configured)
            if (res.status === 404) {
                setServerHasNoKey(true);

                // 2. Try direct client-side fallback using local key if available
                if (youtubeApiKey) {
                    const localRes = await fetch(
                        `https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=5&q=${encodeURIComponent(
                            searchQuery
                        )}&type=video&key=${youtubeApiKey}`
                    );
                    if (localRes.ok) {
                        const data = await localRes.json();
                        if (data && data.items) {
                            setSearchResults(data.items);
                            setIsSearching(false);
                            return;
                        }
                    } else {
                        const errData = await localRes.json();
                        alert(`Search failed: ${errData?.error?.message || "Check your API key"}`);
                    }
                }
            } else {
                const errData = await res.json();
                alert(`Search failed: ${errData?.error?.message || "An error occurred"}`);
            }
        } catch (err) {
            console.warn("Backend search failed or offline, trying client-side fallback:", err);

            // 3. Network fallback to client-side direct search if local key is available
            if (youtubeApiKey) {
                try {
                    const localRes = await fetch(
                        `https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=5&q=${encodeURIComponent(
                            searchQuery
                        )}&type=video&key=${youtubeApiKey}`
                    );
                    if (localRes.ok) {
                        const data = await localRes.json();
                        if (data && data.items) {
                            setSearchResults(data.items);
                            return;
                        }
                    }
                } catch (localErr) {
                    console.error("Local search error:", localErr);
                }
            }
            alert("An error occurred during search. Please check your network connection.");
        } finally {
            setIsSearching(false);
        }
    };

    const handleAddSearchResultToQueue = (item: SearchResultItem) => {
        const videoId = item.id.videoId;
        const title = item.snippet.title;
        const thumbnail = item.snippet.thumbnails?.medium?.url || item.snippet.thumbnails?.default?.url || `https://img.youtube.com/vi/${videoId}/mqdefault.jpg`;
        const channelName = item.snippet.channelTitle || "YouTube Creator";
        const url = `https://www.youtube.com/watch?v=${videoId}`;
        
        socket.emit("add-to-queue", {
            roomId,
            videoInfo: {
                url,
                title,
                thumbnail,
                addedBy: localUser?.name || "Guest",
                channelName
            }
        });
    };

    const handlePlaySearchResult = (item: SearchResultItem) => {
        if (!isOwner) return;
        const videoId = item.id.videoId;
        const url = `https://www.youtube.com/watch?v=${videoId}`;
        socket.emit("video-url-change", { roomId, newUrl: url });
    };

    return (
        <div className="h-full flex flex-col bg-zinc-950/80 backdrop-blur-md text-white border-l border-zinc-800/40">
            {/* Header */}
            <div className="shrink-0 p-4 border-b border-zinc-800/60 bg-zinc-900/40 flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-lg bg-indigo-500/10 flex items-center justify-center">
                        <ListVideo size={16} className="text-indigo-400" />
                    </div>
                    <div>
                        <h3 className="text-sm font-semibold text-zinc-100 leading-none">Up Next</h3>
                        <span className="text-[10px] text-zinc-500 font-medium">Room Media Queue</span>
                    </div>
                </div>
                {onClose && (
                    <button onClick={onClose} className="text-zinc-500 hover:text-zinc-300 transition-colors cursor-pointer">
                        <X size={16} />
                    </button>
                )}
            </div>

            {/* Premium Tab Toggles */}
            <div className="shrink-0 px-4 pt-3 pb-1 flex border-b border-zinc-800/60 bg-zinc-900/5 gap-2">
                <button
                    onClick={() => {
                        setActiveTab("link");
                        onActiveTabChange?.("link");
                    }}
                    className={`pb-2 px-2 text-xs font-bold transition-all relative border-b-2 ${
                        activeTab === "link"
                            ? "text-indigo-400 border-indigo-500"
                            : "text-zinc-500 border-transparent hover:text-zinc-300"
                    }`}
                >
                    Paste Link
                </button>
                <button
                    onClick={() => {
                        setActiveTab("search");
                        onActiveTabChange?.("search");
                    }}
                    className={`pb-2 px-2 text-xs font-bold transition-all relative border-b-2 flex items-center gap-1.5 ${
                        activeTab === "search"
                            ? "text-indigo-400 border-indigo-500"
                            : "text-zinc-500 border-transparent hover:text-zinc-300"
                    }`}
                >
                    <Search size={12} />
                    Search YouTube
                </button>
            </div>

            {/* Dynamic Controls based on Tab */}
            {activeTab === "link" ? (
                /* Input Form Box for pasting links directly inside the sidebar */
                <div className="shrink-0 p-4 border-b border-zinc-800/60 bg-zinc-900/10">
                    <form onSubmit={handleAddToQueue} className="space-y-2">
                        <label className="text-[10px] uppercase font-bold text-zinc-500 tracking-wider">Paste YouTube Link(s)</label>
                        <div className="flex gap-2">
                            <Input
                                value={inputUrl}
                                onChange={(e) => setInputUrl(e.target.value)}
                                placeholder="Paste url(s) (spaces or enters)..."
                                className="bg-zinc-950 border-zinc-800 text-white placeholder:text-zinc-650 text-xs h-9 flex-1"
                                disabled={isLoading}
                            />
                            <Button
                                type="submit"
                                size="sm"
                                className="bg-indigo-600 hover:bg-indigo-500 text-white shrink-0 h-9 px-3 text-xs gap-1 cursor-pointer"
                                disabled={isLoading}
                            >
                                <Plus size={14} /> Add
                            </Button>
                        </div>
                        <span className="text-[9px] text-zinc-600 block leading-tight">
                            Press Enter or click Add to queue. You can paste multiple links separated by spaces or newlines.
                        </span>
                    </form>
                </div>
            ) : (
                /* Search Tab Controls */
                <div className="shrink-0 p-4 border-b border-zinc-800/60 bg-zinc-900/10">
                    {serverHasNoKey && !youtubeApiKey ? (
                        /* API Key Entry Box */
                        <form onSubmit={handleSaveApiKey} className="space-y-3">
                            <div className="flex items-center gap-1.5 text-zinc-300">
                                <Key size={13} className="text-yellow-500 animate-pulse" />
                                <span className="text-xs font-semibold">YouTube API Key Required</span>
                            </div>
                            <p className="text-[10px] text-zinc-400 leading-normal">
                                Paste your YouTube API Key below to search and queue videos directly without leaving SyncRoom! Stored safely in your browser.
                            </p>
                            <div className="flex gap-2">
                                <Input
                                    value={apiKeyInput}
                                    onChange={(e) => setApiKeyInput(e.target.value)}
                                    placeholder="Paste YouTube API Key..."
                                    className="bg-zinc-950 border-zinc-800 text-white placeholder:text-zinc-650 text-xs h-9 flex-1"
                                    type="password"
                                />
                                <Button
                                    type="submit"
                                    size="sm"
                                    className="bg-emerald-600 hover:bg-emerald-500 text-white shrink-0 h-9 px-3 text-xs cursor-pointer"
                                >
                                    Save
                                </Button>
                            </div>
                        </form>
                    ) : (
                        /* Search Form only (Results moved below to ScrollArea) */
                        <div className="space-y-3">
                            <form onSubmit={handleSearch} className="flex gap-2">
                                <Input
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    placeholder="Search YouTube videos..."
                                    className="bg-zinc-950 border-zinc-800 text-white placeholder:text-zinc-650 text-xs h-9 flex-1"
                                    disabled={isSearching}
                                />
                                <Button
                                    type="submit"
                                    size="sm"
                                    className="bg-indigo-600 hover:bg-indigo-500 text-white shrink-0 h-9 px-3 text-xs cursor-pointer animate-none"
                                    disabled={isSearching}
                                >
                                    {isSearching ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
                                </Button>
                            </form>

                            {youtubeApiKey && (
                                <div className="flex items-center justify-between text-[9px] uppercase font-bold text-zinc-500 tracking-wider">
                                    <span className="text-zinc-400">Local key active</span>
                                    <button 
                                        onClick={() => {
                                            setYoutubeApiKey("");
                                            setSearchResults([]);
                                        }}
                                        className="text-red-400 hover:text-red-300 font-medium lowercase cursor-pointer text-[9px]"
                                    >
                                        Change key
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* Scrollable Area - Renders search results or queued videos */}
            <ScrollArea className="flex-1 p-4">
                {activeTab === "search" ? (
                    isSearching ? (
                        /* Loading state */
                        <div className="flex flex-col items-center justify-center py-20 text-center text-zinc-500">
                            <Loader2 size={36} className="text-indigo-500 animate-spin mb-3" />
                            <p className="text-xs font-semibold text-zinc-350">Searching YouTube...</p>
                            <p className="text-[10px] text-zinc-500 mt-1">Finding the best matches for you</p>
                        </div>
                    ) : searchResults.length === 0 ? (
                        /* Search welcome/hint state */
                        <div className="flex flex-col items-center justify-center py-20 text-center text-zinc-500">
                            <Sparkles size={36} className="text-indigo-500/20 mb-3" />
                            <p className="text-xs font-semibold text-zinc-350">Discover YouTube Videos</p>
                            <p className="text-[10px] text-zinc-500 mt-1 max-w-[220px]">
                                Type a keyword above to find videos and add them to the queue!
                            </p>
                        </div>
                    ) : (
                        /* Expanded & Gorgeous Search Results list */
                        <div className="space-y-3.5 pb-6">
                            <div className="flex items-center justify-between text-[10px] uppercase font-bold text-zinc-500 tracking-wider">
                                <span className="flex items-center gap-1 text-zinc-400">
                                    <Sparkles size={11} className="text-yellow-500 animate-pulse" /> {searchResults.length} Results Found
                                </span>
                            </div>
                            <div className="space-y-3">
                                {searchResults.map((item, index) => {
                                    const title = item.snippet.title;
                                    const channel = item.snippet.channelTitle || "YouTube Creator";
                                    const thumbnail = item.snippet.thumbnails?.medium?.url || item.snippet.thumbnails?.default?.url;

                                    return (
                                        <div 
                                            key={index} 
                                            className="flex gap-3 bg-zinc-900/40 hover:bg-zinc-900/90 border border-zinc-800/60 hover:border-zinc-700/50 p-2.5 rounded-xl transition-all duration-200 group relative"
                                        >
                                            {/* Beautiful Big Thumbnail Preview */}
                                            <div className="relative w-28 h-16 rounded-lg overflow-hidden bg-black shrink-0 border border-zinc-850 shadow-md">
                                                {thumbnail ? (
                                                    <img src={thumbnail} alt="" className="w-full h-full object-cover" />
                                                ) : (
                                                    <div className="w-full h-full bg-zinc-900 flex items-center justify-center text-[10px] text-zinc-500">Video</div>
                                                )}
                                            </div>

                                            {/* Video Metadata & Description */}
                                            <div className="flex-1 min-w-0 flex flex-col justify-between">
                                                <div>
                                                    <h4 
                                                        className="text-[11px] font-semibold leading-snug text-zinc-200 line-clamp-2"
                                                        title={title}
                                                    >
                                                        {title}
                                                    </h4>
                                                    <p className="text-[9px] text-zinc-400 truncate mt-1 flex items-center gap-1">
                                                        <Film size={9} className="text-zinc-500 shrink-0" />
                                                        <span className="truncate">{channel}</span>
                                                    </p>
                                                </div>
                                            </div>

                                            {/* Beautiful Action Controls */}
                                            <div className="flex flex-col gap-1.5 shrink-0 justify-center">
                                                {isOwner && (
                                                    <Button
                                                        size="sm"
                                                        onClick={() => handlePlaySearchResult(item)}
                                                        className="h-7 px-2.5 gap-1 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white cursor-pointer text-[10px] font-bold shadow-md shadow-indigo-600/10 transition-all hover:scale-105 active:scale-95"
                                                        title="Play this video immediately"
                                                    >
                                                        <Play size={10} fill="currentColor" /> Play
                                                    </Button>
                                                )}
                                                <Button
                                                    size="sm"
                                                    onClick={() => handleAddSearchResultToQueue(item)}
                                                    className="h-7 px-2.5 gap-1 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-200 cursor-pointer text-[10px] font-bold border border-zinc-700/40 transition-all hover:scale-105 active:scale-95"
                                                    title="Add to queue"
                                                >
                                                    <Plus size={10} /> Add
                                                </Button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )
                ) : (
                    /* Link / Queue Tab list */
                    <div className="space-y-5 pb-4">
                        <section>
                            <div className="mb-3 flex items-center justify-between">
                                <h4 className="text-[10px] uppercase font-bold tracking-wider text-indigo-300">Up Next</h4>
                                <span className="text-[10px] text-zinc-500">{videoQueue.length} videos</span>
                            </div>
                            {videoQueue.length === 0 ? (
                                <div className="flex flex-col items-center py-8 text-center text-zinc-500">
                                    <ListVideo size={30} className="opacity-10 mb-2" />
                                    <p className="text-xs font-medium text-zinc-400">The queue is currently empty.</p>
                                    <p className="text-[10px] text-zinc-600 mt-1 max-w-[200px]">Paste a YouTube link above to add a video.</p>
                                </div>
                            ) : (
                                <div className="space-y-3">
                                    {videoQueue.map((item, idx) => {
                                const isObj = typeof item !== "string";
                                const title = isObj ? item.title : "YouTube Video";
                                const url = isObj ? item.url : item;
                                const thumbnail = isObj ? item.thumbnail : `https://img.youtube.com/vi/${extractYouTubeId(url)}/mqdefault.jpg`;
                                const channel = isObj ? (item.channelName || "YouTube Creator") : "YouTube Creator";
                                const addedBy = isObj ? item.addedBy : null;

                                return (
                                    <div 
                                        key={idx} 
                                        className="flex items-center gap-3 bg-zinc-900/40 hover:bg-zinc-900/90 border border-zinc-800/80 hover:border-zinc-700/50 p-2.5 rounded-xl group transition-all duration-200"
                                    >
                                        {/* Thumbnail Preview */}
                                        <div className="relative w-20 h-12 rounded-lg overflow-hidden bg-black shrink-0 border border-zinc-850 shadow-md">
                                            {thumbnail ? (
                                                <img src={thumbnail} alt="" className="w-full h-full object-cover" />
                                            ) : (
                                                <div className="w-full h-full bg-zinc-900 flex items-center justify-center text-[8px] text-zinc-500">Video</div>
                                            )}
                                            <div className="absolute top-0 left-0 bg-black/80 px-1.5 py-0.5 rounded-br text-[9px] font-extrabold text-indigo-400 border-r border-b border-zinc-850">
                                                {idx + 1}
                                            </div>
                                        </div>

                                        {/* Video metadata */}
                                        <div className="flex-1 min-w-0">
                                            <h4 
                                                onClick={() => isOwner && handlePlayQueueItem(idx)}
                                                className={`text-[11px] font-semibold leading-snug truncate ${
                                                    isOwner 
                                                        ? "text-zinc-200 hover:text-indigo-400 cursor-pointer" 
                                                        : "text-zinc-300"
                                                }`}
                                                title={isOwner ? "Click to play this video immediately" : undefined}
                                            >
                                                {title}
                                            </h4>
                                            
                                            {/* Channel & AddedBy Info */}
                                            <p className="text-[9px] text-zinc-400 truncate mt-0.5 font-medium flex items-center gap-1">
                                                <Film size={9} className="text-zinc-500 shrink-0" />
                                                <span className="truncate">{channel}</span>
                                            </p>
                                            {addedBy && (
                                                <span className="text-[8px] text-zinc-600 block truncate mt-0.5">
                                                    Added by {addedBy}
                                                </span>
                                            )}
                                        </div>

                                        {/* Action Controls */}
                                        <div className="flex flex-col gap-1 items-center justify-center shrink-0">
                                            {isOwner && (
                                                <>
                                                    <button
                                                        onClick={() => handlePlayQueueItem(idx)}
                                                        className="opacity-0 group-hover:opacity-100 p-1 text-zinc-400 hover:text-emerald-400 hover:bg-emerald-500/10 rounded-lg transition-all cursor-pointer"
                                                        title="Play immediately"
                                                    >
                                                        <Play size={11} fill="currentColor" />
                                                    </button>
                                                    <button
                                                        onClick={() => handleRemoveFromQueue(idx)}
                                                        className="opacity-0 group-hover:opacity-100 p-1 text-zinc-500 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-all cursor-pointer"
                                                        title="Remove from queue"
                                                    >
                                                        <Trash2 size={11} />
                                                    </button>
                                                </>
                                            )}
                                        </div>
                                    </div>
                                );
                                    })}
                                </div>
                            )}
                        </section>

                        <section className="border-t border-zinc-800/60 pt-4">
                            <div className="mb-3 flex items-center justify-between">
                                <h4 className="text-[10px] uppercase font-bold tracking-wider text-zinc-400">Recently played</h4>
                                <div className="flex items-center gap-2">
                                    <span className="text-[10px] text-zinc-500">{videoHistory.length} videos</span>
                                    {isOwner && videoHistory.length > 0 && (
                                        <button
                                            type="button"
                                            onClick={handleClearVideoHistory}
                                            className="text-[10px] text-zinc-500 hover:text-red-400"
                                            title="Clear all recently played videos"
                                        >
                                            Clear history
                                        </button>
                                    )}
                                </div>
                            </div>
                            {videoHistory.length === 0 ? (
                                <p className="py-4 text-center text-[10px] text-zinc-600">Played videos will appear here so you can watch them again.</p>
                            ) : (
                                <div className="space-y-2">
                                    {videoHistory.map((item, index) => {
                                        const videoId = extractYouTubeId(item.url);
                                        const thumbnail = item.thumbnail || (videoId ? `https://img.youtube.com/vi/${videoId}/mqdefault.jpg` : "");
                                        return (
                                            <div key={`${item.url}-${index}`} className="flex items-center gap-2 rounded-xl border border-zinc-800/70 bg-zinc-900/40 p-2">
                                                {isOwner && (
                                                    <button
                                                        type="button"
                                                        onClick={() => handlePlayHistoryItem(item.url)}
                                                        className="flex min-w-0 flex-1 items-center gap-3 rounded-lg p-0.5 text-left hover:bg-zinc-800/60"
                                                        title="Play this video again"
                                                    >
                                                        <div className="relative h-10 w-16 shrink-0 overflow-hidden rounded-md bg-black">
                                                            {thumbnail && <img src={thumbnail} alt="" className="h-full w-full object-cover" />}
                                                            <span className="absolute inset-0 flex items-center justify-center bg-black/0 text-white opacity-0 transition-opacity hover:bg-black/35 hover:opacity-100">
                                                                <Play size={14} fill="currentColor" />
                                                            </span>
                                                        </div>
                                                        <span className="min-w-0 flex-1 truncate text-[11px] font-medium text-zinc-300">
                                                            {item.title || "YouTube Video"}
                                                        </span>
                                                    </button>
                                                )}
                                                {!isOwner && (
                                                    <>
                                                        <div className="h-10 w-16 shrink-0 overflow-hidden rounded-md bg-black">
                                                            {thumbnail && <img src={thumbnail} alt="" className="h-full w-full object-cover" />}
                                                        </div>
                                                        <span className="min-w-0 flex-1 truncate text-[11px] font-medium text-zinc-300">
                                                            {item.title || "YouTube Video"}
                                                        </span>
                                                    </>
                                                )}
                                                {isOwner && (
                                                    <button
                                                        type="button"
                                                        onClick={() => handleRemoveHistoryItem(item.url)}
                                                        className="shrink-0 rounded-lg p-1.5 text-zinc-500 hover:bg-red-500/10 hover:text-red-400"
                                                        title="Remove from recently played"
                                                        aria-label={`Remove ${item.title || "video"} from recently played`}
                                                    >
                                                        <Trash2 size={13} />
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </section>
                    </div>
                )}
            </ScrollArea>
        </div>
    );
}
