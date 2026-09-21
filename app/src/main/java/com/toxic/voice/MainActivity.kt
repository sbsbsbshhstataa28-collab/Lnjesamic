package com.toxic.voice

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private var ws: WebSocket? = null
    private var recorder: AudioRecord? = null
    private var running = false
    private var muted = false
    private lateinit var status: TextView
    private lateinit var urlBox: EditText
    private lateinit var tokenBox: EditText
    private lateinit var targetBox: EditText
    private val fx = linkedMapOf("volume" to 100,"gain" to 0,"loudness" to 100,"bass" to 100,"treble" to 100,"presence" to 100,"clarity" to 100,"widen" to 100,"delay" to 0)
    private val switches = linkedMapOf<String,Switch>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        buildUi()
    }

    private fun buildUi() {
        val scroll=ScrollView(this)
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,24,28,30); setBackgroundColor(0xFF080808.toInt()) }
        fun label(s:String,size:Float=15f)=TextView(this).apply{ text=s; textSize=size; setTextColor(0xFFFFFFFF.toInt()); setPadding(0,9,0,5) }
        box.addView(label("🔥 TOXIC",34f)); box.addView(label("LIVE MIC → DSP → TELEGRAM VC",14f))
        status=label("● DISCONNECTED",16f); box.addView(status)
        urlBox=EditText(this).apply{ hint="Backend URL  ws://HOST:8765"; setSingleLine(); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF999999.toInt()) }; box.addView(urlBox)
        tokenBox=EditText(this).apply{ hint="Control token"; setSingleLine(); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF999999.toInt()) }; box.addView(tokenBox)
        targetBox=EditText(this).apply{ hint="Target Telegram VC chat ID"; setSingleLine(); setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF999999.toInt()) }; box.addView(targetBox)
        fun btn(text:String, action:()->Unit){ Button(this).apply{ this.text=text; setOnClickListener{action()} }.also{box.addView(it)} }
        btn("🔌 CONNECT") { connect() }
        btn("🎙 JOIN TARGET VC") { send(mapOf("op" to "join","target" to targetBox.text.toString().trim())) }
        btn("▶ START RELAY") { startMic(); send(mapOf("op" to "start")) }
        btn("⏹ STOP RELAY") { stopMic(); send(mapOf("op" to "stop")) }
        btn("🔇 MUTE / UNMUTE") { muted=!muted; send(mapOf("op" to if(muted) "mute" else "unmute")); status.text=if(muted) "● MUTED" else "● LIVE" }
        btn("🚪 LEAVE VC") { stopMic(); send(mapOf("op" to "leave")) }
        val names=listOf("volume","gain","loudness","bass","treble","presence","clarity","widen","delay")
        for(n in names){ box.addView(label(n.uppercase())); val sb=SeekBar(this); sb.max=400; sb.progress=fx[n]!!; val v=label("${sb.progress}/400"); sb.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){fx[n]=p;v.text="$p/400"; sendFx()}
            override fun onStartTrackingTouch(s:SeekBar?){ }
            override fun onStopTrackingTouch(s:SeekBar?){ }
        }); box.addView(sb); box.addView(v) }
        for(n in listOf("compressor","agc","gate","echo","reverb")){ val sw=Switch(this).apply{ text=n.uppercase(); setTextColor(0xFFFFFFFF.toInt()); isChecked=n=="compressor"||n=="agc"; setOnCheckedChangeListener{_,_->sendFx()} }; switches[n]=sw; box.addView(sw) }
        scroll.addView(box); setContentView(scroll)
    }

    private fun connect(){
        val client=OkHttpClient.Builder().readTimeout(0,TimeUnit.MILLISECONDS).build()
        val req=Request.Builder().url(urlBox.text.toString().trim()).addHeader("X-TOXIC-TOKEN",tokenBox.text.toString()).build()
        ws=client.newWebSocket(req,object:WebSocketListener(){
            override fun onOpen(w:WebSocket,r:Response){ status.text="● CONNECTED"; sendFx() }
            override fun onMessage(w:WebSocket,text:String){ status.text="● $text" }
            override fun onMessage(w:WebSocket,bytes:ByteString){ }
            override fun onFailure(w:WebSocket,t:Throwable,r:Response?){ status.text="● ERROR: ${t.message}" }
            override fun onClosed(w:WebSocket,c:Int,r:String){ status.text="● DISCONNECTED" }
        })
    }
    private fun send(m:Map<String,String>){ val o=JSONObject();m.forEach{o.put(it.key,it.value)}; ws?.send(o.toString()) }
    private fun sendFx(){ val o=JSONObject().put("op","fx"); fx.forEach{o.put(it.key,it.value)}; switches.forEach{o.put(it.key,it.value.isChecked)}; ws?.send(o.toString()) }

    private fun startMic(){
        if(running)return
        if(ws==null){status.text="● CONNECT FIRST";return}
        val sr=48000; val min=AudioRecord.getMinBufferSize(sr,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT)
        recorder=AudioRecord(MediaRecorder.AudioSource.MIC,sr,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,sr/2)); recorder!!.startRecording(); running=true
        Thread { val buf=ShortArray(1920); while(running){ val n=recorder?.read(buf,0,buf.size)?:0; if(n>0&&!muted){ val b=ByteArray(n*2); for(i in 0 until n){ val x=buf[i].toInt(); b[i*2]=(x and 255).toByte(); b[i*2+1]=(x shr 8).toByte() }; ws?.send(ByteString.of(*b)) } } }.start()
        status.text="● MIC → TOXIC → VC"
    }
    private fun stopMic(){running=false;try{recorder?.stop()}catch(_:Exception){};recorder?.release();recorder=null}
    override fun onDestroy(){stopMic();ws?.close(1000,"bye");super.onDestroy()}
}
