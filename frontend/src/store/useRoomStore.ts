import { create } from 'zustand';

interface User {
    id: string; // socketId
    name: string;
    image?: string;
    isMuted?: boolean;
    isVideoOn?: boolean;
    stream?: MediaStream;
    isOwner?: boolean;
    isPrimaryOwner?: boolean;
    isHandRaised?: boolean;
}

interface Message {
    id: string;
    userId: string;
    userName: string;
    userImage?: string;
    text: string;
    timestamp: string;
    isAi?: boolean;
    // File sharing fields
    type?: "text" | "file";
    fileName?: string;
    fileSize?: number;
    fileUrl?: string;
    fileType?: string;
}

export interface Poll {
    id: string;
    question: string;
    options: string[];
    votes: { [optionIndex: number]: string[] }; // optionIndex -> array of userIds
    creatorName: string;
    creatorId: string;
    isOpen: boolean;
}

export interface Answer {
    id: string;
    text: string;
    creatorName: string;
    creatorId: string;
    timestamp: string;
}

export interface Question {
    id: string;
    text: string;
    creatorName: string;
    creatorId: string;
    timestamp: string;
    upvotes: string[]; // array of userIds
    answers: Answer[];
}

export interface VideoInfo {
    url: string;
    title: string;
    thumbnail: string;
    addedBy?: string;
    channelName?: string;
}

interface RoomState {
    users: User[];
    messages: Message[];
    setUsers: (users: User[]) => void;
    addUser: (user: User) => void;
    removeUser: (socketId: string) => void;
    updateUser: (socketId: string, updates: Partial<User>) => void;
    addMessage: (message: Message) => void;
    videoQueue: (string | VideoInfo)[];
    setVideoQueue: (queue: (string | VideoInfo)[]) => void;
    polls: Poll[];
    setPolls: (polls: Poll[]) => void;
    questions: Question[];
    setQuestions: (questions: Question[]) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
    users: [],
    messages: [],
    setUsers: (users) => set({ users }),
    addUser: (user) => set((state) => ({ users: [...state.users, user] })),
    removeUser: (socketId) => set((state) => ({ users: state.users.filter(u => u.id !== socketId) })),
    updateUser: (socketId, updates) => set((state) => ({
        users: state.users.map(u => u.id === socketId ? { ...u, ...updates } : u)
    })),
    addMessage: (message) => set((state) => ({ messages: [...state.messages, message] })),
    videoQueue: [],
    setVideoQueue: (queue) => set({ videoQueue: queue }),
    polls: [],
    setPolls: (polls) => set({ polls }),
    questions: [],
    setQuestions: (questions) => set({ questions }),
}));
