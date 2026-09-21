import asyncio, json, math, os
from array import array
from collections import deque
from pathlib import Path
from dotenv import load_dotenv
load_dotenv()
from telethon import TelegramClient
from telethon.sessions import StringSession
from pytgcalls import PyTgCalls
from pytgcalls.types import ExternalMedia, MediaStream
from pytgcalls.types.raw import AudioParameters
import websockets

API_ID=int(os.getenv('API_ID','0')); API_HASH=os.getenv('API_HASH',''); SESSION_STRING=os.getenv('SESSION_STRING',''); SESSION_PATH=os.getenv('SESSION_PATH','./data/toxic')
HOST=os.getenv('HOST','0.0.0.0'); PORT=int(os.getenv('PORT','8765')); TOKEN=os.getenv('CONTROL_TOKEN','')
if not API_ID or not API_HASH: raise SystemExit('Set API_ID and API_HASH')
SAMPLE_RATE=48000; CHANNELS=2
fx={'volume':100.0,'gain':0.0,'loudness':100.0,'bass':100.0,'treble':100.0,'presence':100.0,'clarity':100.0,'widen':100.0,'delay':0.0,'compressor':True,'agc':True,'gate':False,'echo':False,'reverb':False}
state={'rms':0.08,'low':[0.,0.],'high':[0.,0.],'pres':[0.,0.],'echo':deque(maxlen=int(SAMPLE_RATE*.7*2)),'delay':deque(maxlen=int(SAMPLE_RATE*.4*2))}
user=None; call=None; target=None; joined=False; muted=False

def clamp(x,a,b): return max(a,min(b,x))
def dsp(pcm):
    if not pcm:return pcm
    a=array('h'); a.frombytes(pcm[:len(pcm)-len(pcm)%2]);
    if not a:return pcm
    rms=math.sqrt(sum((x/32768.)**2 for x in a)/len(a)); state['rms']=state['rms']*.94+rms*.06
    volume=max(0,fx['volume'])/100; loud=max(0,fx['loudness'])/100; gain=10**(((clamp(fx['gain'],0,400)/400)*30)/20)
    agc=clamp(.11/max(state['rms'],.012),.55,3.2) if fx['agc'] else 1
    linear=clamp(gain*volume*loud*agc,.05,8)
    la=1-math.exp(-2*math.pi*180/SAMPLE_RATE); ha=1-math.exp(-2*math.pi*3400/SAMPLE_RATE); pa=1-math.exp(-2*math.pi*1700/SAMPLE_RATE)
    d=int(SAMPLE_RATE*clamp(fx['delay'],0,400)/1000)*2; ed=int(SAMPLE_RATE*.32)*2; rd=int(SAMPLE_RATE*.08)*2
    out=[0.]*len(a)
    bs=max(0,(max(0,fx['bass'])-100)/100); ts=max(0,(max(0,fx['treble'])-100)/100); ps=max(0,(max(0,fx['presence'])-100)/100); cs=max(0,(max(0,fx['clarity'])-100)/100)
    for i,raw in enumerate(a):
        ch=i%2; x=raw/32768.; low=state['low'][ch]+la*(x-state['low'][ch]); state['low'][ch]=low; hp=state['high'][ch]+ha*(x-state['high'][ch]); state['high'][ch]=hp; high=x-hp; pp=state['pres'][ch]+pa*(x-state['pres'][ch]); state['pres'][ch]=pp
        y=(x+low*bs+high*ts+(pp-low)*ps*.55+high*cs*.35)*linear
        if fx['gate'] and abs(y)<.012 and state['rms']<.035:y*=.18
        if fx['compressor'] and abs(y)>.32:y=math.copysign(.32+(abs(y)-.32)/3.5,y)
        if fx['echo'] and len(state['echo'])>=ed:y+=state['echo'][-ed]*.22
        state['echo'].append(y)
        if d and len(state['delay'])>=d:y+=state['delay'][-d]*.12
        state['delay'].append(y)
        if fx['reverb'] and len(state['echo'])>=rd:y+=state['echo'][-rd]*.10
        out[i]=y
    w=max(0,fx['widen'])/100
    if w!=1:
        for i in range(0,len(out)-1,2):
            m=(out[i]+out[i+1])*.5; s=(out[i]-out[i+1])*.5*w; out[i]=m+s; out[i+1]=m-s
    z=array('h');
    for y in out:z.append(int(max(-1,min(1,math.tanh(y*1.12)*.985))*32767))
    return z.tobytes()

async def join(t):
    global target,joined
    if joined and target==int(t):return
    if joined:
        try: await call.leave_call(target)
        except:pass
    target=int(t); params=AudioParameters(bitrate=SAMPLE_RATE,channels=CHANNELS)
    await call.play(target,MediaStream(ExternalMedia.AUDIO,params)); joined=True

async def handler(ws):
    global muted
    if TOKEN and ws.request.headers.get('X-TOXIC-TOKEN')!=TOKEN: await ws.close(code=4401,reason='unauthorized'); return
    await ws.send('TOXIC CONNECTED')
    try:
        async for msg in ws:
            if isinstance(msg,str):
                c=json.loads(msg); op=c.get('op')
                if op=='join': await join(c['target']); await ws.send('TARGET JOINED')
                elif op=='leave':
                    if joined:
                        try: await call.leave_call(target)
                        except:pass
                    await ws.send('VC LEFT')
                elif op=='start': await ws.send('RELAY LIVE')
                elif op=='stop': await ws.send('RELAY STOPPED')
                elif op=='mute': muted=True; await ws.send('MUTED')
                elif op=='unmute': muted=False; await ws.send('UNMUTED')
                elif op=='fx':
                    for k,v in c.items():
                        if k in fx: fx[k]=bool(v) if k in ('compressor','agc','gate','echo','reverb') else float(v)
            else:
                if joined and not muted:
                    await call.send_frame(target, 'microphone', dsp(msg))
    finally: pass

async def main():
    global user,call
    user=TelegramClient(StringSession(SESSION_STRING),API_ID,API_HASH) if SESSION_STRING else TelegramClient(SESSION_PATH,API_ID,API_HASH)
    await user.start()
    call=PyTgCalls(user); await call.start()
    print(f'TOXIC backend listening on {HOST}:{PORT}')
    async with websockets.serve(handler,HOST,PORT,max_size=2**20,ping_interval=20): await asyncio.Future()
if __name__=='__main__': asyncio.run(main())
