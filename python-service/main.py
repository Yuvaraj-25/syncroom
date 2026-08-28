from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
# pyrefly: ignore [missing-import]
import httpx
import re

import os

app = FastAPI(title="SyncRoom Python Service")

# Allow CORS — use env var in production, fallback to wildcard
allowed_origins = os.environ.get("CORS_ORIGIN", "*").split(",")

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class VideoRequest(BaseModel):
    url: str

def get_youtube_id(url: str):
    # Basic YouTube URL regex extraction
    match = re.search(r"(?:v=|\/)([0-9A-Za-z_-]{11}).*", url)
    return match.group(1) if match else None

@app.post("/api/metadata")
async def get_metadata(request: VideoRequest):
    video_id = get_youtube_id(request.url)
    if not video_id:
        return {"title": "Unknown Video", "thumbnail": ""}
    
    # We fetch the public oembed endpoint for basic title/thumbnail without an API key
    oembed_url = f"https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={video_id}&format=json"
    
    async with httpx.AsyncClient() as client:
        try:
            response = await client.get(oembed_url)
            if response.status_code == 200:
                data = response.json()
                return {
                    "title": data.get("title", "Unknown Video"),
                    "thumbnail": data.get("thumbnail_url", "")
                }
        except Exception as e:
            print(f"Error fetching metadata: {e}")
            
    return {"title": "Unknown Video", "thumbnail": ""}

@app.get("/health")
def health_check():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
