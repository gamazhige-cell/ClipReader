import asyncio
import edge_tts

async def main():
    communicate = edge_tts.Communicate("Hello World", "zh-CN-XiaoxiaoNeural")
    await communicate.save("test.mp3")

asyncio.run(main())
