/* Surface/asset lifecycle adapted from Filament ModelViewer, Copyright (C) 2020
 * The Android Open Source Project. Licensed under the Apache License, Version 2.0
 * https://www.apache.org/licenses/LICENSE-2.0 ; provided AS IS, without warranties.
 */
package com.apex.navlab

import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.*

object DemoRoute {
    fun x(s: Double): Double { val t=((s-200)/160).coerceIn(0.0,1.0); return 80*t*t*(3-2*t) }
    fun heading(s: Double) = atan2(x(s+0.1)-x(s-0.1),0.2)
}

class RoadRenderer(private val surface: SurfaceView) : AutoCloseable {
    companion object { init { Utils.init() } }
    private val engine=Engine.create()
    private val renderer=engine.createRenderer()
    private val scene=engine.createScene()
    private val camera=engine.createCamera(EntityManager.get().create())
    private val view=engine.createView()
    private val ui=UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val display=DisplayHelper(surface.context)
    private val materials=UbershaderProvider(engine)
    private val loader=AssetLoader(engine,materials,EntityManager.get())
    private val resources=ResourceLoader(engine,true)
    private var swap: SwapChain?=null
    private val light=EntityManager.get().create()
    private val sky=Skybox.Builder().color(.045f,.065f,.12f,1f).build(engine)
    private val ambient=IndirectLight.Builder().irradiance(1,floatArrayOf(.65f,.69f,.8f)).intensity(22000f).build(engine)
    private val bytes=surface.context.assets.open("scene.glb").use { it.readBytes() }
    private val buffer=ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
    private val asset=checkNotNull(loader.createAsset(buffer)) { "Could not load original demo geometry" }
    private val truck=asset.entities.first { asset.getName(it)=="Truck" }
    private var closed=false
    var renderedFrames=0L; private set
    var topDown=false
    var night=false; private set
    var wide=false
    init {
        camera.setExposure(16f,1f/125f,100f)
        view.scene=scene;view.camera=camera
        view.antiAliasing=View.AntiAliasing.FXAA
        view.renderQuality=view.renderQuality.apply { hdrColorBuffer=View.QualityLevel.MEDIUM }
        scene.skybox=sky;scene.indirectLight=ambient
        LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f,.78f,.58f)
            .intensity(100000f).direction(-.6f,-.5f,-.3f).castShadows(true).build(engine,light)
        scene.addEntity(light)
        resources.asyncBeginLoad(asset);asset.releaseSourceData()
        ui.renderCallback=object: UiHelper.RendererCallback {
            override fun onNativeWindowChanged(s:Surface) {
                swap?.let { engine.destroySwapChain(it) };swap=engine.createSwapChain(s)
                display.attach(renderer,surface.display)
            }
            override fun onDetachedFromSurface() {
                display.detach();swap?.let { engine.destroySwapChain(it);engine.flushAndWait() };swap=null
            }
            override fun onResized(w:Int,h:Int) {
                if(w==0 || h==0)return
                view.viewport=Viewport(0,0,w,h)
                camera.setLensProjection(24.0,w.toDouble()/h,.1,1600.0)
                val fence=engine.createFence();fence.wait(Fence.Mode.FLUSH,Fence.WAIT_FOR_EVER);engine.destroyFence(fence)
            }
        }
        ui.attachTo(surface)
    }
    fun toggleNight() {
        night=!night
        val lm=engine.lightManager;val i=lm.getInstance(light)
        lm.setIntensity(i,if(night)15000f else 100000f)
        ambient.intensity=if(night)14000f else 22000f
        sky.setColor(if(night).004f else .045f,if(night).008f else .065f,if(night).025f else .12f,1f)
    }
    fun render(time:Long,s:Double):Boolean {
        if(closed || !ui.isReadyToRender || swap==null)return false
        resources.asyncUpdateLoad()
        val ready=IntArray(128)
        while(true) { val n=asset.popRenderables(ready);if(n==0)break;scene.addEntities(ready.copyOf(n)) }
        val x=DemoRoute.x(s);val a=DemoRoute.heading(s);val c=cos(a).toFloat();val sn=sin(a).toFloat()
        // Rotation about Y is negative heading: truck front is local -Z.
        engine.transformManager.setTransform(engine.transformManager.getInstance(truck),floatArrayOf(
            c,0f,sn,0f, 0f,1f,0f,0f, -sn,0f,c,0f, x.toFloat(),0f,-s.toFloat(),1f))
        if(topDown) camera.lookAt(x,85.0,-s+5,x,0.0,-s-10,0.0,0.0,-1.0)
        else {
            val distance=if(wide)17.0 else 12.0
            camera.lookAt(x-sin(a)*distance,if(wide)7.0 else 5.4,-s+cos(a)*distance,
                DemoRoute.x(s+8),1.2,-s-8,0.0,1.0,0.0)
        }
        if(renderer.beginFrame(swap!!,time)){renderer.render(view);renderer.endFrame();renderedFrames++;return true}
        return false
    }
    override fun close() {
        if(closed)return;closed=true
        ui.detach();resources.asyncCancelLoad();resources.evictResourceData()
        scene.removeEntities(asset.entities);loader.destroyAsset(asset);loader.destroy()
        materials.destroyMaterials();materials.destroy();resources.destroy()
        scene.skybox=null;scene.indirectLight=null;engine.destroySkybox(sky);engine.destroyIndirectLight(ambient)
        engine.destroyEntity(light);engine.destroyRenderer(renderer);engine.destroyView(view);engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity);EntityManager.get().destroy(camera.entity);EntityManager.get().destroy(light)
        engine.destroy()
    }
}
