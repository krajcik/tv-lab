import Cocoa
import Metal
import QuartzCore
import simd

struct ParticleState { var motion: SIMD4<Float>; var capture: SIMD4<Float> = .zero }
struct ParticleProperties { var base: SIMD4<Float>; var offset: SIMD4<Float> }
struct FlowUniforms {
    var clock: SIMD4<Float> = .zero
    var formation: SIMD4<Float> = .zero
    var geometry: SIMD4<Float> = .zero
    var extra: SIMD4<Float> = .zero
    var projection: SIMD4<Float> = .zero
}
struct VortexUniforms { var center: SIMD4<Float>; var shape: SIMD4<Float>; var seed: SIMD4<Float> }
struct Vortex {
    var x: Float = 0, y: Float = 0, vx: Float = 0, vy: Float = 0
    var radius: Float = 0, spin: Float = 0, life: Float = 0, age: Float = 0
    var shock: Float = 0, cooldown: Float = 0, seed: Float = 0
}
final class FlowField {
    var extent = SIMD2<Float>((16/9)/(2*0.97)-0.006,1/(2*0.97)-0.006)
    var vortices = [Vortex](repeating:Vortex(),count:8)
    var generation=0
    init() { for i in 0..<8 { spawn(i,0) } }
    private func spawn(_ i: Int,_ time: Float) {
        var v=Vortex(); v.seed=Float(i*131+generation*37)+floor(time*5); generation += 1
        let angle=flowRandom(v.seed+19)*Float.pi*2
        v.x=(flowRandom(v.seed+1)-0.5)*extent.x*1.65; v.y=(flowRandom(v.seed+3)-0.5)*extent.y*1.65
        v.vx=cos(angle)*(0.09+flowRandom(v.seed+11)*0.10); v.vy=sin(angle)*(0.09+flowRandom(v.seed+13)*0.10)
        v.radius=0.15+flowRandom(v.seed+5)*0.17; v.spin=(i%2==0 ? -1:1)*(0.055+flowRandom(v.seed+7)*0.045)
        v.life=8+flowRandom(v.seed+17)*10; vortices[i]=v
    }
    func advance(_ time: Float,_ dt: Float) -> [VortexUniforms] {
        for i in 0..<8 {
            vortices[i].age += dt; vortices[i].shock *= exp(-dt*2.6); vortices[i].cooldown=max(0,vortices[i].cooldown-dt)
            if vortices[i].age >= vortices[i].life { spawn(i,time) }
            var v=vortices[i]
            let turn=time*(0.37+flowRandom(v.seed)*0.25)+v.seed
            v.vx += cos(turn)*dt*0.075; v.vy += sin(turn*1.17)*dt*0.075
            let speed=hypot(v.vx,v.vy); if speed > 0.20 { v.vx *= 0.20/speed; v.vy *= 0.20/speed }
            v.x += v.vx*dt; v.y += v.vy*dt
            let bx=extent.x*0.91, by=extent.y*0.91
            if abs(v.x)>bx { v.x=copysign(bx,v.x); v.vx = -copysign(abs(v.vx)*0.95,v.x); v.shock=max(v.shock,0.45) }
            if abs(v.y)>by { v.y=copysign(by,v.y); v.vy = -copysign(abs(v.vy)*0.95,v.y); v.shock=max(v.shock,0.45) }
            vortices[i]=v
        }
        for i in 0..<8 { for j in i+1..<8 {
            var a=vortices[i], b=vortices[j]
            let dx=b.x-a.x,dy=b.y-a.y,distance=hypot(dx,dy),overlap=(a.radius+b.radius)*0.62-distance
            if overlap<=0 || distance<0.00001 { continue }
            let nx=dx/distance,ny=dy/distance,separation=overlap*min(1,dt*2.4)*0.5
            a.x -= nx*separation; a.y -= ny*separation; b.x += nx*separation; b.y += ny*separation
            if a.cooldown<=0 && b.cooldown<=0 {
                let impulse: Float=0.11+overlap*0.4
                a.vx -= nx*impulse; a.vy -= ny*impulse; b.vx += nx*impulse; b.vy += ny*impulse
                a.shock=0.85; b.shock=0.85; a.cooldown=2; b.cooldown=2
                if a.spin*b.spin<0 { a.age=max(a.age,a.life-1.6); b.age=max(b.age,b.life-2) }
                else { a.spin *= 0.85; b.spin *= 1.08 }
            }
            vortices[i]=a; vortices[j]=b
        } }
        return vortices.map { v in VortexUniforms(center:SIMD4(v.x,v.y,v.vx,v.vy),shape:SIMD4(v.radius,v.spin,smooth(0,1.7,v.age)*(1-smooth(v.life-2,v.life,v.age)),v.shock),seed:SIMD4(v.seed,0,0,0)) }
    }
}
final class ParticleGPU {
    let device: MTLDevice, queue: MTLCommandQueue
    let fieldPipeline: MTLComputePipelineState, meanPipeline: MTLComputePipelineState, simulation: MTLComputePipelineState
    let points: MTLRenderPipelineState, trails: MTLRenderPipelineState
    let field: MTLBuffer, mean: MTLBuffer
    var states: [MTLBuffer], properties: MTLBuffer
    var read=0
    let count: Int
    init(bundle: Bundle,count: Int = 200_000) throws {
        guard let device=MTLCreateSystemDefaultDevice(),let queue=device.makeCommandQueue() else { throw FlowError.invalid("Metal unavailable") }
        self.device=device; self.queue=queue; self.count=count
        guard let url=bundle.url(forResource:"Particles",withExtension:"metal") else { throw FlowError.invalid("Metal shader missing") }
        let options=MTLCompileOptions(); options.fastMathEnabled=false
        let library=try device.makeLibrary(source:String(contentsOf:url,encoding:.utf8),options:options)
        func compute(_ name: String) throws -> MTLComputePipelineState {
            guard let function=library.makeFunction(name:name) else { throw FlowError.invalid(name) }; return try device.makeComputePipelineState(function:function)
        }
        fieldPipeline=try compute("buildField"); meanPipeline=try compute("meanField"); simulation=try compute("simulate")
        func pipeline(_ vertex: String,_ fragment: String) throws -> MTLRenderPipelineState {
            let d=MTLRenderPipelineDescriptor(); d.vertexFunction=library.makeFunction(name:vertex); d.fragmentFunction=library.makeFunction(name:fragment)
            let a=d.colorAttachments[0]!; a.pixelFormat = .bgra8Unorm; a.isBlendingEnabled=true
            a.sourceRGBBlendFactor = .sourceAlpha; a.destinationRGBBlendFactor = .oneMinusSourceAlpha
            a.sourceAlphaBlendFactor = .one; a.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            return try device.makeRenderPipelineState(descriptor:d)
        }
        points=try pipeline("pointVertex","pointFragment"); trails=try pipeline("trailVertex","trailFragment")
        func buffer(_ length: Int) throws -> MTLBuffer { guard let b=device.makeBuffer(length:length,options:.storageModeShared) else { throw FlowError.invalid("Metal allocation") }; return b }
        field=try buffer(65*49*MemoryLayout<SIMD2<Float>>.stride); mean=try buffer(8)
        states=[try buffer(count*MemoryLayout<ParticleState>.stride),try buffer(count*MemoryLayout<ParticleState>.stride)]
        properties=try buffer(count*MemoryLayout<ParticleProperties>.stride)
        let state=states[0].contents().bindMemory(to:ParticleState.self,capacity:count)
        let p=properties.contents().bindMemory(to:ParticleProperties.self,capacity:count)
        let extent=FlowField().extent
        for i in 0..<count {
            state[i]=ParticleState(motion:SIMD4((flowRandom(Float(i*3+101))-0.5)*extent.x*2,(flowRandom(Float(i*3+503))-0.5)*extent.y*2,0,0))
            let id=Float(i*11)
            p[i]=ParticleProperties(base:SIMD4(flowRandom(id+7),(0.17+flowRandom(id+57)*0.17)*1.21,flowRandom(id+73),flowRandom(id+113)>0.945 ? 1:0),offset:SIMD4((flowRandom(id+19)-0.5)*0.0024,(flowRandom(id+31)-0.5)*0.0024,0,0))
        }
    }
    func targetBuffer(_ cloud: Cloud) throws -> MTLBuffer {
        guard let b=cloud.points.withUnsafeBytes({ device.makeBuffer(bytes:$0.baseAddress!,length:$0.count,options:.storageModeShared) }) else { throw FlowError.invalid("Target allocation") }; return b
    }
    func encode(_ command: MTLCommandBuffer,uniforms: FlowUniforms,vortices: [VortexUniforms],targets: MTLBuffer) throws {
        var u=uniforms
        guard let e=command.makeComputeCommandEncoder() else { throw FlowError.invalid("Compute encoder") }
        func dispatch(_ p: MTLComputePipelineState,_ n: Int) {
            e.setComputePipelineState(p); e.dispatchThreads(MTLSize(width:n,height:1,depth:1),threadsPerThreadgroup:MTLSize(width:min(128,p.maxTotalThreadsPerThreadgroup),height:1,depth:1))
        }
        e.setBuffer(field,offset:0,index:0)
        vortices.withUnsafeBytes { e.setBytes($0.baseAddress!,length:$0.count,index:1) }
        e.setBytes(&u,length:MemoryLayout<FlowUniforms>.stride,index:2); dispatch(fieldPipeline,65*49)
        // Separate dispatches on a tracked buffer preserve producer/consumer ordering.
        e.memoryBarrier(scope:.buffers)
        e.setBuffer(field,offset:0,index:0); e.setBuffer(mean,offset:0,index:1); dispatch(meanPipeline,1)
        e.memoryBarrier(scope:.buffers)
        e.setBuffer(states[read],offset:0,index:0); e.setBuffer(states[1-read],offset:0,index:1)
        e.setBuffer(properties,offset:0,index:2); e.setBuffer(targets,offset:0,index:3)
        e.setBuffer(field,offset:0,index:4); e.setBuffer(mean,offset:0,index:5)
        e.setBytes(&u,length:MemoryLayout<FlowUniforms>.stride,index:6); dispatch(simulation,count)
        e.endEncoding(); read=1-read
    }
    func render(_ command: MTLCommandBuffer,texture: MTLTexture,uniforms: FlowUniforms,targets: MTLBuffer) throws {
        let d=MTLRenderPassDescriptor(); d.colorAttachments[0].texture=texture
        d.colorAttachments[0].loadAction = .clear; d.colorAttachments[0].storeAction = .store; d.colorAttachments[0].clearColor=MTLClearColorMake(0,0,0,1)
        guard let e=command.makeRenderCommandEncoder(descriptor:d) else { throw FlowError.invalid("Render encoder") }
        var u=uniforms
        e.setRenderPipelineState(trails); e.setVertexBuffer(states[read],offset:0,index:0); e.setVertexBuffer(states[1-read],offset:0,index:1)
        e.setVertexBytes(&u,length:MemoryLayout<FlowUniforms>.stride,index:2)
        e.drawPrimitives(type:.line,vertexStart:0,vertexCount:2,instanceCount:min(10000,count))
        e.setRenderPipelineState(points); e.setVertexBuffer(properties,offset:0,index:1); e.setVertexBuffer(targets,offset:0,index:2)
        e.setVertexBytes(&u,length:MemoryLayout<FlowUniforms>.stride,index:3); e.drawPrimitives(type:.point,vertexStart:0,vertexCount:count)
        e.endEncoding()
    }
}
final class PresentationStats {
    private let lock=NSLock()
    private var first: Double=0, last: Double=0, count=0, intervals: [Double]=[]
    func record(_ time: Double) {
        guard time>0 else { return }; lock.lock(); defer { lock.unlock() }
        if first==0 { first=time }
        if last>0 && intervals.count<18000 { intervals.append(time-last) }; last=time; count += 1
    }
    func report() -> [String:Any] {
        lock.lock(); defer { lock.unlock() }; let sorted=intervals.sorted()
        return ["presentedFrames":count,"elapsed":last-first,"presentedFPS":last>first ? Double(count-1)/(last-first):0,"medianMs":sorted.isEmpty ? 0:sorted[sorted.count/2]*1000,"p95Ms":sorted.isEmpty ? 0:sorted[min(sorted.count-1,Int(Double(sorted.count)*0.95))]*1000]
    }
}
final class FlowRenderer {
    let gpu: ParticleGPU, catalog: Catalog, cache: CloudCache
    let layer: CAMetalLayer
    var field=FlowField()
    let presentation=PresentationStats()
    private let slots=DispatchSemaphore(value:2)
    private var targets: MTLBuffer?, targetCount=1
    private(set) var scene=0
    private var phase: Float = -9, time: Float = 0
    private var last: CFTimeInterval = 0
    private var active=false
    private(set) var frames=0
    private(set) var firstFrame: CFTimeInterval = 0
    private(set) var frameIntervals: [Double] = []
    var onScene: ((String)->Void)?
    init(layer: CAMetalLayer,bundle: Bundle) throws {
        self.layer=layer; gpu=try ParticleGPU(bundle:bundle); catalog=try Catalog(bundle:bundle); cache=CloudCache(catalog)
        layer.device=gpu.device; layer.pixelFormat = .bgra8Unorm; layer.framebufferOnly=false
        layer.isOpaque=true; layer.backgroundColor=CGColor(gray:0,alpha:1); layer.maximumDrawableCount=2
        layer.displaySyncEnabled=true
    }
    func start() { active=true; last=0; cache.request(scene) }
    func stop() { active=false; last=0 }
    deinit { cache.close() }
    func draw() {
        guard active,layer.drawableSize.width>0,layer.drawableSize.height>0,slots.wait(timeout:.now()) == .success else { return }
        var submitted=false
        defer { if !submitted { slots.signal() } }
        autoreleasepool {
            do {
                if targets == nil {
                    guard let cloud=cache.clouds[scene] else {
                        if cache.failures.contains(scene) { scene=(scene+1)%catalog.scenes.count; cache.request(scene) }
                        return
                    }
                    targets=try gpu.targetBuffer(cloud); targetCount=cloud.points.count
                    phase=15-SceneTiming(words:catalog.scenes[scene].words).build; phase = -phase
                    onScene?(cloud.label)
                }
                guard let drawable=layer.nextDrawable(),let command=gpu.queue.makeCommandBuffer(),let initialTargets=targets else { return }
                let now=CACurrentMediaTime(), dt: Float=last==0 ? 1/60 : min(0.045,Float(now-last)); last=now
                time += dt
                let timing=SceneTiming(words:catalog.scenes[scene].words)
                if phase+dt >= timing.duration {
                    let next=(scene+1)%catalog.scenes.count
                    if let cloud=cache.clouds[next] {
                        targets=try gpu.targetBuffer(cloud); targetCount=cloud.points.count; scene=next; phase=0; cache.request(scene); onScene?(cloud.label)
                    } else if cache.failures.contains(next) {
                        scene=next; targets=nil; cache.request(scene)
                    }
                    // Otherwise hold the scene clock; free flow still advances.
                } else { phase += dt }
                let t=SceneTiming(words:catalog.scenes[scene].words)
                let motion: Float=0.18+0.82*smooth(0,20,time), ramp=max(0,min(1,time/20))
                let flowTime: Float=time<20 ? 0.18*time+16.4*(ramp*ramp*ramp-0.5*ramp*ramp*ramp*ramp) : time-8.2
                let aspect=Float(layer.drawableSize.width/layer.drawableSize.height)
                field.extent=SIMD2(aspect/(2*0.97)-0.006,1/(2*0.97)-0.006)
                let scale=max(1,Float(layer.contentsScale)), logical=Float(layer.drawableSize.height)/scale
                var u=FlowUniforms(); u.clock=SIMD4(dt,time,phase,motion); u.formation=SIMD4(t.build,t.release,t.end,t.words ? 1:0)
                u.geometry=SIMD4(field.extent.x,field.extent.y,logical,scale); u.extra=SIMD4(flowTime,t.duration,Float(targetCount),Float(gpu.count)); u.projection=SIMD4(2*0.97/aspect,-2*0.97,0,0)
                let current=targets ?? initialTargets
                try gpu.encode(command,uniforms:u,vortices:field.advance(flowTime,dt*motion),targets:current)
                try gpu.render(command,texture:drawable.texture,uniforms:u,targets:current)
                let slots=self.slots
                command.addCompletedHandler { command in
                    if command.status == .error { NSLog("Flow GPU error: %@",String(describing:command.error)) }; slots.signal()
                }
                let stats=presentation
                drawable.addPresentedHandler { stats.record($0.presentedTime) }
                command.present(drawable); command.commit(); submitted=true
                if firstFrame==0 { firstFrame=now }
                if frames>0 && frameIntervals.count<18000 { frameIntervals.append(Double(dt)) }; frames += 1
            } catch { NSLog("Flow render failed: %@",String(describing:error)); active=false }
        }
    }
}
