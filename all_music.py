import math, wave, struct

SR = 44100

TRACKS = {
    "ru_ngu": {
        "bpm": 58,
        "melody": [(60,2),(64,2),(67,2),(64,2),(62,2),(65,2),(69,2),(65,2),
                   (60,2),(64,2),(67,2),(72,2),(69,2),(67,2),(64,2),(60,4)]
    },
    "chill": {
        "bpm": 84,
        "melody": [(64,1),(67,1),(71,2),(69,1),(67,1),(64,2),(62,1),(65,1),(69,2),
                   (67,1),(65,1),(62,2),(60,1),(64,1),(67,2),(72,2),(69,2),(67,1),(65,1),(64,4)]
    },
    "neon_dream": {
        "bpm": 112,
        "melody": [(64,1),(67,1),(69,2),(67,1),(64,1),(62,2),(64,1),(67,1),
                   (72,2),(71,1),(69,1),(67,2),(64,1),(67,1),(69,1),(72,1),
                   (74,2),(72,2),(69,1),(67,1),(64,2),(62,1),(64,3)]
    }
}

def make_wav(filename, bpm, melody):
    beat = 60 / bpm
    samples = []
    for midi, beats in melody:
        dur = beats * beat
        f = 440 * 2 ** ((midi - 69) / 12)
        n = int(dur * SR)
        for i in range(n):
            t = i / SR
            attack = min(0.08, dur / 5)
            release = min(0.5, dur / 3)
            env = t / attack if t < attack else max(0, (dur-t)/release) if t > dur-release else 1
            s = (0.75*math.sin(2*math.pi*f*t)
                 + 0.18*math.sin(2*math.pi*2*f*t)
                 + 0.05*math.sin(2*math.pi*3*f*t))
            samples.append(s * env * 0.28)
    with wave.open(filename, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1,min(1,s))*32767)) for s in samples))
    print("Created", filename)

if __name__ == "__main__":
    make_wav("ru_ngu.wav", **TRACKS["ru_ngu"])
    make_wav("chill.wav", **TRACKS["chill"])
    make_wav("neon_dream.wav", **TRACKS["neon_dream"])
    print("Đã ghép bộ nhạc và tạo 3 file WAV.")
