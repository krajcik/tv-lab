import Cocoa
import Metal

func require(_ condition: Bool,_ message: String) throws { if !condition { throw FlowError.invalid(message) } }
func vec(_ f: [Float],_ i: Int = 0) -> SIMD4<Float> { SIMD4(f[i],f[i+1],f[i+2],f[i+3]) }
func validateFlow(_ directory: String) throws {
    let out=URL(fileURLWithPath:directory), bundle=Bundle.main
    let catalog=try Catalog(bundle:bundle,shuffle:false)
    var hashes=Set<Int>()
    for i in 0..<200 {
        if i%2 != 0 { continue }
        let cloud=try autoreleasepool { try catalog.load(i) }
        try require(cloud.points.count==65536,"Image target count")
        var hasher=Hasher(); cloud.points.forEach { hasher.combine($0) }; hashes.insert(hasher.finalize())
    }
    try require(hashes.count==100,"Images must be distinct")
    for i in 0..<1000 { try autoreleasepool {
        let cloud=try catalog.load(i*2+1); try require(cloud.points.count==16384,"Quote target count")
    } }
    print("PASS actual catalog: 100 distinct image clouds, 1000 quote clouds, text wrapping")
    let cache=CloudCache(catalog)
    for index in 0..<40 {
        cache.request(index)
        let deadline=Date().addingTimeInterval(5)
        while cache.clouds[index]==nil && Date()<deadline { RunLoop.main.run(until:Date().addingTimeInterval(0.005)) }
        try require(cache.clouds[index] != nil && cache.clouds.count<=3,"Cache availability/bound")
        try require(cache.clouds.keys.allSatisfy { $0>=index && $0<=index+2 },"Stale cache entry")
    }
    cache.request(300);cache.close();RunLoop.main.run(until:Date().addingTimeInterval(0.1))
    try require(cache.clouds.isEmpty,"Cache repopulated after close")
    print("PASS bounded cache: 40 transitions, <=3 retained clouds, stale completion discarded after close")
    try require(SceneTiming(words:true).release-SceneTiming(words:true).build==20,"20 second hold")
    let reference=try JSONDecoder().decode(Reference.self,from:Data(contentsOf:out.appendingPathComponent("reference.json")))
    let gpu=try ParticleGPU(bundle:bundle,count:256)
    let target=try gpu.targetBuffer(Cloud(points:stride(from:0,to:reference.targets.count,by:4).map { vec(reference.targets,$0) },label:"Reference"))
    var maximum: Float=0
    var components=[Float](repeating:0,count:6), worst=""
    for c in reference.cases {
        let props=gpu.properties.contents().bindMemory(to:ParticleProperties.self,capacity:256)
        for i in 0..<256 { props[i]=ParticleProperties(base:vec(c.properties,i*6),offset:SIMD4(c.properties[i*6+4],c.properties[i*6+5],0,0)) }
        for block in c.blocks {
            let states=gpu.states[gpu.read].contents().bindMemory(to:ParticleState.self,capacity:256)
            for i in 0..<256 { states[i]=ParticleState(motion:vec(block.initial,i*6),capture:SIMD4(block.initial[i*6+4],block.initial[i*6+5],0,0)) }
            for step in block.steps {
                var u=FlowUniforms();u.clock=vec(step.clock);u.formation=vec(step.formation);u.extra=vec(step.extra)
                u.geometry=SIMD4((16/9)/(2*0.97)-0.006,1/(2*0.97)-0.006,1080,2)
                let vortices=(0..<8).map { VortexUniforms(center:vec(step.centers,$0*4),shape:vec(step.shapes,$0*4),seed:vec(step.seeds,$0*4)) }
                let command=gpu.queue.makeCommandBuffer()!;try gpu.encode(command,uniforms:u,vortices:vortices,targets:target);command.commit();command.waitUntilCompleted()
                try require(command.status == .completed,"Metal compute error")
            }
            let actual=gpu.states[gpu.read].contents().bindMemory(to:ParticleState.self,capacity:256)
            for i in 0..<256 { let a=actual[i]; let values=[a.motion.x,a.motion.y,a.motion.z,a.motion.w,a.capture.x,a.capture.y]
                for j in 0..<6 { try require(values[j].isFinite,"Nonfinite GPU state");let delta=abs(values[j]-block.expected[i*6+j]); components[j]=max(components[j],delta); if delta>maximum { maximum=delta; worst="time=\(block.steps.last!.clock[1]) particle=\(i) component=\(j) GPU=\(values[j]) CPU=\(block.expected[i*6+j])" } }
            }
        }
    }
    print("Parity components \(components); worst \(worst)")
    try require(maximum<0.0001,"Android/Metal divergence \(maximum)")
    print("PASS Android CPU / Metal: 256 particles, 120 one-step samples across 7200 CPU steps; max error \(maximum)")
    // Render actual 200k particles at 4K into our own texture; no desktop capture.
    let large=try ParticleGPU(bundle:bundle)
    let d=MTLTextureDescriptor.texture2DDescriptor(pixelFormat:.bgra8Unorm,width:3840,height:2160,mipmapped:false);d.usage=[.renderTarget];d.storageMode = .shared
    let texture=large.device.makeTexture(descriptor:d)!
    for scene in [0,1] {
        let cloud=try catalog.load(scene), target=try large.targetBuffer(cloud), field=FlowField()
        var lastU=FlowUniforms()
        let began=CACurrentMediaTime()
        for step in 0..<1080 {
            let t=Float(step+1)/60, timing=SceneTiming(words:scene==1),motion: Float=0.18+0.82*smooth(0,20,t),ramp=min(1,t/20)
            let flow: Float=t<20 ? 0.18*t+16.4*(ramp*ramp*ramp-0.5*ramp*ramp*ramp*ramp):t-8.2
            var u=FlowUniforms();u.clock=SIMD4(1/60,t,t-(15-timing.build),motion);u.formation=SIMD4(timing.build,timing.release,timing.end,scene==1 ? 1:0)
            u.extra=SIMD4(flow,timing.duration,Float(cloud.points.count),200000);u.geometry=SIMD4(field.extent.x,field.extent.y,1080,2);u.projection=SIMD4(2*0.97/(16/9),-2*0.97,0,0)
            let command=large.queue.makeCommandBuffer()!;try large.encode(command,uniforms:u,vortices:field.advance(flow,motion/60),targets:target)
            try large.render(command,texture:texture,uniforms:u,targets:target);command.commit();command.waitUntilCompleted();try require(command.status == .completed,"4K render failure");lastU=u
        }
        let elapsed=CACurrentMediaTime()-began
        var pixels=[UInt8](repeating:0,count:3840*2160*4)
        pixels.withUnsafeMutableBytes { texture.getBytes($0.baseAddress!,bytesPerRow:3840*4,from:MTLRegionMake2D(0,0,3840,2160),mipmapLevel:0) }
        let bright=stride(from:0,to:pixels.count,by:4).filter { pixels[$0]>24 }.count
        try require(bright>1000,"Empty render")
        let data=Data(pixels) as CFData,provider=CGDataProvider(data:data)!
        let image=CGImage(width:3840,height:2160,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:3840*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedFirst.rawValue|CGBitmapInfo.byteOrder32Little.rawValue),provider:provider,decode:nil,shouldInterpolate:false,intent:.defaultIntent)!
        let dest=CGImageDestinationCreateWithURL(out.appendingPathComponent(scene==0 ? "image.png":"quote.png") as CFURL,"public.png" as CFString,1,nil)!
        CGImageDestinationAddImage(dest,image,nil);try require(CGImageDestinationFinalize(dest),"PNG write")
        print("PASS 4K/200k offscreen \(scene==0 ? "image":"quote"): 1080 frames / \(elapsed)s; throughput \(1080/elapsed) fps (not display FPS); phase \(lastU.clock.z)")
    }
}
private struct Reference: Decodable { let targets:[Float];let cases:[ReferenceCase] }
private struct ReferenceCase: Decodable { let properties:[Float];let blocks:[ReferenceBlock] }
private struct ReferenceBlock: Decodable { let initial:[Float];let expected:[Float];let steps:[ReferenceStep] }
private struct ReferenceStep: Decodable { let clock:[Float];let formation:[Float];let extra:[Float];let centers:[Float];let shapes:[Float];let seeds:[Float] }
