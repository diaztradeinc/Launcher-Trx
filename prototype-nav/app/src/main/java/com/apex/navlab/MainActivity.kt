package com.apex.navlab

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : Activity(), Choreographer.FrameCallback {
    private lateinit var root: FrameLayout
    private lateinit var scene: RoadRenderer
    private lateinit var maneuver: TextView
    private lateinit var sign: TextView
    private lateinit var eta: TextView
    private lateinit var diagnostics: TextView
    private lateinit var pauseButton: TextView
    private lateinit var mapButton: TextView
    private lateinit var voiceButton: TextView
    private val positioned=mutableListOf<Pair<View,FloatArray>>()
    private var speech:TextToSpeech?=null
    private var speechReady=false
    private var voice=false
    var progress=0.0; private set
    var paused=true; private set
    var ended=false; private set
    val frames get()=if(::scene.isInitialized)scene.renderedFrames else 0
    val mapMode get()=scene.topDown
    private var speed=1.0
    private var previous=0L
    private var lastRender=0L
    private var lastHud=0L
    private var statsStart=0L
    private var statsFrames=0L
    private var fps=0
    private var announced=false
    private val red=Color.rgb(226,22,44)
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        root=FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v,insets ->
            v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        val surface=SurfaceView(this)
        place(surface,78f,50f,524f,676f)
        try { scene=RoadRenderer(surface) } catch(e:Exception) {
            AlertDialog.Builder(this).setTitle("3D initialization failed").setMessage("This device could not start the renderer: ${e.message}")
                .setPositiveButton("Close") { _,_->finish() }.setCancelable(false).show();return
        }
        panel(0f,0f,602f,50f,Color.rgb(9,11,14))
        label("TRX  APEX",16f,0f,210f,50f,25f,Color.WHITE,true)
        label("3D LAB  •  v0.1",380f,0f,210f,50f,13f,Color.LTGRAY)
        panel(0f,50f,78f,676f,Color.rgb(10,12,15))
        val dock=listOf("⌂\nHome","➤\nNav","♫\nMedia","◴\nPerformance","▦\nApps","⚙\nSettings")
        dock.forEachIndexed { i,text ->
            val b=button(text,0f,68f+i*97f,78f,85f,if(i==1)Color.rgb(53,12,20) else Color.rgb(10,12,15),"dock$i") {
                when(i) {0->finish();1->recenter();5->showSettings();else->AlertDialog.Builder(this).setTitle(text.substringAfter('\n')).setMessage("This separate app tests navigation only. Your launcher still contains this page.").setPositiveButton("OK",null).show()}
            };b.textSize=12f
        }
        panel(0f,165f,3f,85f,red)
        button("SIMULATION • NOT FOR DRIVING",91f,59f,497f,27f,Color.rgb(30,34,40),"simulation") { about() }
        maneuver=label("↱  200 m\nDemo exit 27 • Canyon loop",95f,94f,490f,85f,24f,Color.WHITE,true).apply { background=rounded(Color.argb(240,13,17,22));gravity=Gravity.CENTER_VERTICAL;setPadding(18,0,12,0) }
        mapButton=button("3D  /  MAP",427f,190f,155f,43f,Color.argb(240,15,20,25),"map") {
            scene.topDown=!scene.topDown;mapButton.text=if(scene.topDown)"MAP  /  3D" else "3D  /  MAP"
        }
        sign=label("DEMO EXIT 27  ↗\nCanyon loop",211f,256f,234f,60f,17f,Color.WHITE,true).apply { background=rounded(Color.rgb(19,77,53));gravity=Gravity.CENTER }
        button("◎",530f,374f,53f,51f,Color.argb(235,12,16,21),"recenter") { recenter() }
        button("☼",530f,434f,53f,51f,Color.argb(235,12,16,21),"light") { scene.toggleNight() }
        voiceButton=button("♪ OFF",530f,494f,53f,51f,Color.argb(235,12,16,21),"voice") {
            if(!speechReady)toast("No speech engine ready on this device")
            else { voice=!voice;voiceButton.text=if(voice)"♪ ON" else "♪ OFF";if(voice)speak("Simulation. Take demo exit twenty seven toward Canyon loop.") else speech?.stop() }
        }
        button("✕",530f,554f,53f,51f,red,"end") { ended=true;paused=true;pauseButton.text="RESTART";speech?.stop();updateHud() }
        eta=label("DEMO ROUTE  •  480 m remaining",96f,615f,486f,43f,17f,Color.WHITE,true).apply { background=rounded(Color.argb(245,12,15,20));gravity=Gravity.CENTER }
        pauseButton=button("START DEMO",96f,666f,154f,40f,red,"pause") {
            if(ended) { progress=0.0;ended=false;announced=false }
            paused=!paused;pauseButton.text=if(paused)"RESUME" else "PAUSE";updateHud()
        }
        lateinit var speedButton:TextView
        speedButton=button("1× SPEED",260f,666f,138f,40f,Color.rgb(24,29,36),"speed") {
            speed=if(speed==1.0)2.0 else 1.0;speedButton.text="${speed.toInt()}× SPEED"
        }
        diagnostics=label("Loading 3D…",405f,663f,182f,44f,11f,Color.LTGRAY)
        root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_->layoutViews() }
        speech=TextToSpeech(this) { result->speechReady=result==TextToSpeech.SUCCESS;if(speechReady)speech?.language=Locale.US }
        updateHud()
    }
    private fun rounded(color:Int)=GradientDrawable().apply { setColor(color);cornerRadius=14f;setStroke(1,Color.rgb(66,72,80)) }
    private fun place(v:View,x:Float,y:Float,w:Float,h:Float) { positioned.add(v to floatArrayOf(x,y,w,h));root.addView(v,FrameLayout.LayoutParams(1,1)) }
    private fun panel(x:Float,y:Float,w:Float,h:Float,color:Int) { place(View(this).apply{setBackgroundColor(color)},x,y,w,h) }
    private fun label(text:String,x:Float,y:Float,w:Float,h:Float,size:Float,color:Int,bold:Boolean=false):TextView {
        val v=TextView(this).apply{this.text=text;setTextColor(color);gravity=Gravity.CENTER;setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);setTag(android.R.id.custom,size);if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)}
        place(v,x,y,w,h);return v
    }
    private fun button(text:String,x:Float,y:Float,w:Float,h:Float,color:Int,tag:String,action:()->Unit):TextView =
        label(text,x,y,w,h,if(text.length<3)28f else 13f,Color.WHITE,true).apply {
            background=rounded(color);this.tag=tag;isClickable=true;isFocusable=true;contentDescription=when(tag){"map"->"Switch map and chase view";"recenter"->"Recenter chase camera";"light"->"Toggle day and night";"voice"->"Toggle simulated voice guidance";"end"->"End simulation";else->text.replace('\n',' ')};setOnClickListener{action()}
        }
    private fun layoutViews() {
        val sx=(root.width-root.paddingLeft-root.paddingRight)/602f
        val sy=(root.height-root.paddingTop-root.paddingBottom)/726f
        positioned.forEach { (v,p)->
            val lp=v.layoutParams as FrameLayout.LayoutParams
            val w=(p[2]*sx).roundToInt();val h=(p[3]*sy).roundToInt();val x=(p[0]*sx).roundToInt();val y=(p[1]*sy).roundToInt()
            if(lp.width!=w || lp.height!=h || lp.leftMargin!=x || lp.topMargin!=y){lp.width=w;lp.height=h;lp.leftMargin=x;lp.topMargin=y;v.layoutParams=lp}
            if(v is TextView)v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,(v.getTag(android.R.id.custom) as Float)*min(sx,sy))
        }
    }
    private fun recenter(){scene.topDown=false;scene.wide=false;mapButton.text="3D  /  MAP"}
    private fun showSettings(){AlertDialog.Builder(this).setTitle("3D Lab settings").setItems(arrayOf("Change camera distance","Day / night lighting","About this prototype")){_,i->when(i){0->scene.wide=!scene.wide;1->scene.toggleNight();2->about()}}.show()}
    private fun about(){AlertDialog.Builder(this).setTitle("TRX APEX 3D Lab • 0.1").setMessage("Original low-poly truck proxy and synthetic road. No GPS, live traffic, real map, or Google navigation data.\n\nThis build tests the chase camera, HUD, sound and renderer. It is not guidance for driving. Your launcher remains a separate app.\n\n30 FPS is a target; the counter measures submitted frames on this device.").setPositiveButton("OK",null).show()}
    private fun speak(text:String){if(voice && speechReady)speech?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"demo")}
    private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_SHORT).show()
    private fun updateHud() {
        val remaining=(480-progress).coerceAtLeast(0.0).roundToInt()
        maneuver.text=when { ended->"✓  Simulation ended\nTap RESTART to drive again";progress<200->"↱  ${(200-progress).roundToInt()} m\nDemo exit 27 • Canyon loop";progress<365->"↱  Follow the blue route\nCanyon loop";else->"↑  Continue ahead\nDemo destination" }
        sign.text=if(progress<365)"DEMO EXIT 27  ↗\nCanyon loop" else "DEMO DESTINATION\n${remaining} m ahead"
        eta.text="${if(paused)"PAUSED" else "DEMO"}  •  $remaining m  •  ${(remaining/(10*speed)).roundToInt()} sec remaining"
        diagnostics.text="$fps submitted FPS\n${if(paused)0 else (22*speed).toInt()} demo mph"
    }
    override fun doFrame(time:Long) {
        if(!::scene.isInitialized)return
        Choreographer.getInstance().postFrameCallback(this)
        // Cap at 30 submissions/sec while integrating motion by elapsed time.
        if(lastRender!=0L && time-lastRender<32_000_000L)return
        val dt=if(previous==0L)0.0 else ((time-previous)/1e9).coerceAtMost(.15)
        previous=time;lastRender=time
        if(!paused){progress=(progress+dt*10*speed).coerceAtMost(480.0);if(progress>=480){ended=true;paused=true;pauseButton.text="RESTART";speak("Simulation complete.")}}
        if(progress>150 && !announced){announced=true;speak("In fifty meters, take demo exit twenty seven.")}
        scene.render(time,progress)
        if(statsStart==0L){statsStart=time;statsFrames=frames}
        if(time-statsStart>=1_000_000_000L){fps=((frames-statsFrames)*1e9/(time-statsStart)).roundToInt();statsStart=time;statsFrames=frames}
        if(time-lastHud>200_000_000L){lastHud=time;updateHud()}
    }
    override fun onResume(){super.onResume();previous=0;lastRender=0;if(::scene.isInitialized)Choreographer.getInstance().postFrameCallback(this)}
    override fun onPause(){Choreographer.getInstance().removeFrameCallback(this);speech?.stop();super.onPause()}
    override fun onDestroy(){speech?.shutdown();if(::scene.isInitialized)scene.close();super.onDestroy()}
}
