"use client";

import { useEffect, useState } from "react";
import type { Socket } from "socket.io-client";
import { FileText, Printer } from "lucide-react";

type PinnedNote = {
  id: string | number;
  userName?: string;
  text?: string;
  message?: string;
  timestamp?: string;
};

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return entities[character];
  });
}

export default function StudyRoomPanel({
  socket,
  roomId,
}: {
  socket: Socket;
  roomId: string;
}) {
  const [notes, setNotes] = useState<PinnedNote[]>([]);

  useEffect(() => {
    const handleNotes = ({ notes }: { notes: PinnedNote[] }) => {
      setNotes(notes || []);
    };

    socket.on("pinned-notes-updated", handleNotes);
    socket.emit("request-pinned-notes", { roomId });

    return () => {
      socket.off("pinned-notes-updated", handleNotes);
    };
  }, [socket, roomId]);

  const getExportText = () =>
    notes
      .map((note, index) => {
        const date = note.timestamp
          ? new Date(note.timestamp).toLocaleString()
          : "";
        return [
          `${index + 1}. ${note.userName || "Room member"}`,
          date,
          note.text || note.message || "No text available",
        ]
          .filter(Boolean)
          .join("\n");
      })
      .join("\n\n");

  const getFileName = (extension: string) => {
    const safeRoomId = roomId.replace(/[^a-z0-9-_]/gi, "-");
    return `syncroom-${safeRoomId}-study-notes.${extension}`;
  };

  const downloadText = () => {
    const file = new Blob([getExportText()], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(file);
    const link = document.createElement("a");
    link.href = url;
    link.download = getFileName("txt");
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  const printNotes = () => {
    const printWindow = window.open("", "_blank");
    if (!printWindow) {
      window.alert("Allow pop-ups for SyncRoom to print or save notes as a PDF.");
      return;
    }
    printWindow.onload = () => {
      printWindow.focus();
      printWindow.print();
    };

    const noteCards = notes
      .map((note, index) => {
        const author = escapeHtml(note.userName || "Room member");
        const content = escapeHtml(note.text || note.message || "No text available");
        const date = note.timestamp
          ? `<p class="date">${escapeHtml(new Date(note.timestamp).toLocaleString())}</p>`
          : "";
        return `<article><h2>${index + 1}. ${author}</h2>${date}<p>${content}</p></article>`;
      })
      .join("");

    printWindow.document.write(`<!doctype html>
      <html>
        <head>
          <title>${escapeHtml(`SyncRoom ${roomId} study notes`)}</title>
          <style>
            body { font: 15px/1.6 Arial, sans-serif; color: #18181b; max-width: 760px; margin: 40px auto; padding: 0 24px; }
            h1 { border-bottom: 2px solid #4f46e5; padding-bottom: 12px; }
            article { break-inside: avoid; border-bottom: 1px solid #d4d4d8; padding: 8px 0 16px; }
            article h2 { font-size: 16px; margin-bottom: 4px; }
            article p { white-space: pre-wrap; overflow-wrap: anywhere; margin: 6px 0; }
            article .date { color: #71717a; font-size: 12px; }
            @media print { body { margin: 0 auto; } }
          </style>
        </head>
        <body>
          <h1>SyncRoom study notes</h1>
          <p>Room: ${escapeHtml(roomId)}</p>
          ${noteCards}
        </body>
      </html>`);
    printWindow.document.close();
  };

  return (
    <div className="flex h-full flex-col text-white">
      <div className="border-b border-zinc-800 p-4">
        <h2 className="font-semibold">📚 Study Room</h2>
        <p className="mt-1 text-xs text-zinc-400">
          Your saved study notes
        </p>
        <div className="mt-3 flex gap-2">
          <button
            type="button"
            onClick={downloadText}
            disabled={notes.length === 0}
            className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 px-2.5 py-1.5 text-xs text-zinc-200 hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <FileText size={14} />
            Export TXT
          </button>
          <button
            type="button"
            onClick={printNotes}
            disabled={notes.length === 0}
            className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 px-2.5 py-1.5 text-xs text-zinc-200 hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-50"
            title="Print or save as PDF"
          >
            <Printer size={14} />
            Save as PDF
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3">
        {notes.length === 0 ? (
          <p className="text-sm text-zinc-400">
            No pinned notes yet. Open Chat and click Pin on a message.
          </p>
        ) : (
          notes.map((note) => (
            <div
              key={note.id}
              className="mb-3 rounded-lg border border-zinc-700 bg-zinc-900 p-3"
            >
              <p className="mb-1 text-xs text-indigo-300">
                {note.userName || "Room member"}
              </p>

              <p className="whitespace-pre-wrap break-words text-sm">
                {note.text || note.message || "No text available"}
              </p>

              <button
                onClick={() =>
                  socket.emit("unpin-message", {
                    roomId,
                    noteId: note.id,
                  })
                }
                className="mt-2 text-xs text-zinc-400 hover:text-red-400"
              >
                Unpin
              </button>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
