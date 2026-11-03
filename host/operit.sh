#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"; if [ -f "$D/config.sh" ]; then . "$D/config.sh"; else . "$D/config.example.sh"; fi
# usage: operit.sh [-t seconds] [-c chat_id] [-r] "message"
#   -t  overall timeout (default 900s; also sent to Operit as timeout_ms)
#   -c  continue an existing chat (default: new chat)
#   -r  raw: print full JSON instead of the cleaned reply
# Streams via SSE so long tasks don't hit HTTP timeouts; prints elapsed time + chat_id to stderr.
# v3 (2026-10-31): Operit ignores create_new_chat when its FloatingChatService isn't running (createNewChat fails
#   silently -> message lands in the old current chat with its whole history). So without -c we first send a tiny
#   priming request with show_floating=true + stop_after=true (creates a fresh chat, floating service exits right
#   after), then send the real task to that new chat_id with show_floating=false (no floating ball over the apps).
T=900; CHAT=""; RAW=0
while getopts "t:c:r" o; do case $o in t) T=$OPTARG;; c) CHAT=$OPTARG;; r) RAW=1;; esac; done; shift $((OPTIND-1))
OPERIT_URL="$OPERIT_URL" OPERIT_TOKEN_FILE="$OPERIT_TOKEN_FILE" python3 - "$1" "$T" "$CHAT" "$RAW" <<'PY'
import json,sys,os,re,time,urllib.request
msg,T,chat,raw=sys.argv[1],int(sys.argv[2]),sys.argv[3],sys.argv[4]=="1"
tok=open(os.path.expanduser(os.environ['OPERIT_TOKEN_FILE'])).read().strip()
URL=os.environ['OPERIT_URL'].rstrip('/')+'/api/external-chat'
def prime():
    b={"message":"新会话，只回复：好","response_mode":"sync","create_new_chat":True,"show_floating":True,
       "initial_mode":"BALL","stop_after":True,"return_tool_status":False,"timeout_ms":60000}
    q=urllib.request.Request(URL,data=json.dumps(b).encode(),
        headers={"Authorization":"Bearer "+tok,"Content-Type":"application/json"})
    return json.load(urllib.request.urlopen(q,timeout=90)).get("chat_id")
if not chat:
    t_p=time.time(); chat=prime() or ""
    sys.stderr.write("[operit] new chat %s (%.1fs)\n"%(chat,time.time()-t_p))
    if not chat: sys.exit("[operit] failed to create new chat")
body={"message":msg,"response_mode":"sync","stream":True,"show_floating":False,
      "return_tool_status":False,"timeout_ms":T*1000}
if chat: body.update(create_new_chat=False,chat_id=chat)
else: body["create_new_chat"]=True
r=urllib.request.Request(URL,data=json.dumps(body).encode(),
    headers={"Authorization":"Bearer "+tok,"Content-Type":"application/json","Accept":"text/event-stream"})
t0=time.time(); text=[]; final=None; chat_id=None
try:
    with urllib.request.urlopen(r,timeout=T+30) as resp:
        ev=None
        for line in resp:
            line=line.decode("utf-8","replace").rstrip("\n")
            if line.startswith("event:"): ev=line[6:].strip()
            elif line.startswith("data:"):
                try: d=json.loads(line[5:].strip())
                except Exception: continue
                chat_id=d.get("chat_id",chat_id)
                if ev=="delta" or d.get("event")=="delta": text.append(d.get("delta",""))
                elif ev in("done","error") or d.get("event") in("done","error"): final=d
except Exception as e:
    final={"success":False,"error":"client: %r"%e}
el=time.time()-t0
out=(final or {}).get("ai_response") or "".join(text)
out=re.sub(r"<think>.*?</think>","",out,flags=re.S).strip()
sys.stderr.write("[operit] %.0fs chat_id=%s success=%s err=%s\n"%(el,chat_id,(final or {}).get("success"),(final or {}).get("error")))
print(json.dumps(final,ensure_ascii=False) if raw else out)
PY
