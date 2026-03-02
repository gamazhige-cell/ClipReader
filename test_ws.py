import websockets
import asyncio
import uuid
import sys

async def test_edge_tts():
    ws_url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    token = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    conn_id = uuid.uuid4().hex
    url = f"{ws_url}?TrustedClientToken={token}&ConnectionId={conn_id}"

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
        "Origin": "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold",
    }

    try:
        async with websockets.connect(url, extra_headers=headers) as ws:
            print("Connected successfully!")
            await ws.close()
            return True
    except Exception as e:
        print(f"Failed standard headers: {e}")

    # Try without Origin
    try:
        headers_no_origin = { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0" }
        async with websockets.connect(url, extra_headers=headers_no_origin) as ws:
            print("Connected successfully without Origin!")
            await ws.close()
            return True
    except Exception as e:
        print(f"Failed without origin: {e}")

    return False

if __name__ == "__main__":
    asyncio.run(test_edge_tts())
