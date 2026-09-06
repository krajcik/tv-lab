import Cocoa
import CoreText

func smooth(_ a: Float, _ b: Float, _ x: Float) -> Float {
    let t = max(0, min(1, (x-a)/(b-a))); return t*t*(3-2*t)
}
func flowRandom(_ n: Float) -> Float {
    let x = sin(Double(n)*127.1+311.7)*43758.5453123; return Float(x-floor(x))
}
struct ImageEntry: Decodable { let id: String; let resource: String; let crop: [Double]; let title: String }
struct Quote: Decodable { let text: String; let author: String }
struct Scene { let image: Int?; let quote: Int?; var words: Bool { quote != nil } }
struct Cloud { let points: [SIMD4<Float>]; let label: String }
struct SceneTiming {
    let words: Bool
    var build: Float { words ? 8 : 6 }
    var release: Float { build + (words ? 20 : 3.9) }
    var end: Float { release + (words ? 5 : 3) }
    var duration: Float { end + 12.9 }
}
struct SampleRandom {
    var state: UInt64 = 71
    mutating func next() -> Float {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Float(state >> 40) / 16777216
    }
}
enum FlowError: Error { case invalid(String) }
final class Catalog {
    let bundle: Bundle
    let images: [ImageEntry]
    let quotes: [Quote]
    let scenes: [Scene]
    init(bundle: Bundle, shuffle: Bool = true) throws {
        self.bundle = bundle
        func read<T: Decodable>(_ name: String) throws -> T {
            guard let url = bundle.url(forResource: name, withExtension: "json") else { throw FlowError.invalid("Missing \(name).json") }
            return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
        }
        images = try read("images"); quotes = try read("quotes")
        guard images.count == 100, quotes.count == 1000, Set(images.map(\.id)).count == 100 else { throw FlowError.invalid("Expected complete 100/1000 catalog") }
        for image in images {
            guard image.crop.count == 4, image.crop.allSatisfy({ $0.isFinite && $0 >= 0 && $0 <= 1 }), image.crop[0] < image.crop[2], image.crop[1] < image.crop[3], bundle.url(forResource: image.resource, withExtension: "png") != nil else { throw FlowError.invalid("Invalid image \(image.id)") }
        }
        var pictures = Array(images.indices), words = Array(quotes.indices)
        if shuffle { pictures.shuffle(); words.shuffle() }
        scenes = (0..<max(pictures.count, words.count)).flatMap { [Scene(image: pictures[$0 % pictures.count], quote: nil), Scene(image: nil, quote: words[$0 % words.count])] }
    }
    func load(_ index: Int) throws -> Cloud {
        let scene = scenes[index]
        if let i = scene.image { return try imageCloud(images[i]) }
        let q = quotes[scene.quote!]
        return try textCloud(q.text, author: q.author)
    }
    private func imageCloud(_ entry: ImageEntry) throws -> Cloud {
        let url = bundle.url(forResource: entry.resource, withExtension: "png")!
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil), let atlas = CGImageSourceCreateImageAtIndex(source, 0, nil) else { throw FlowError.invalid(entry.id) }
        let c = entry.crop
        let left = (Double(atlas.width)*c[0]).rounded(), top = (Double(atlas.height)*c[1]).rounded()
        let right = (Double(atlas.width)*c[2]).rounded(), bottom = (Double(atlas.height)*c[3]).rounded()
        guard let crop = atlas.cropping(to: CGRect(x: left, y: top, width: right-left, height: bottom-top)) else { throw FlowError.invalid(entry.id) }
        let scale = min(1, 700 / Double(max(crop.width,crop.height)))
        let w = max(3,Int(Double(crop.width)*scale)), h = max(3,Int(Double(crop.height)*scale))
        var pixels = [UInt8](repeating: 0,count:w*h*4)
        try pixels.withUnsafeMutableBytes { raw in
            guard let context = Self.context(raw.baseAddress!,w,h) else { throw FlowError.invalid("Bitmap context") }
            context.interpolationQuality = .high; context.draw(crop,in:CGRect(x:0,y:0,width:w,height:h))
        }
        return try sample(pixels,w:w,h:h,image:true,label:entry.title)
    }
    static func context(_ data: UnsafeMutableRawPointer,_ w: Int,_ h: Int) -> CGContext? {
        CGContext(data:data,width:w,height:h,bitsPerComponent:8,bytesPerRow:w*4,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)
    }
    static func font(_ size: CGFloat, italic: Bool = false) -> CTFont {
        CTFontCreateWithName((italic ? "Georgia-Italic" : "Georgia") as CFString,size,nil)
    }
    static func line(_ text: String,_ font: CTFont) -> CTLine {
        CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:[NSAttributedString.Key(kCTFontAttributeName as String):font,NSAttributedString.Key(kCTForegroundColorAttributeName as String):CGColor(gray:1,alpha:1)]))
    }
    static func wrap(_ text: String,_ font: CTFont,_ width: Double) -> [String] {
        var result: [String] = [], current = ""
        for word in text.split(whereSeparator: { $0.isWhitespace }).map(String.init) {
            let candidate = current.isEmpty ? word : current+" "+word
            if CTLineGetTypographicBounds(line(candidate,font),nil,nil,nil) > width && !current.isEmpty { result.append(current); current = word }
            else { current = candidate }
        }
        if !current.isEmpty { result.append(current) }; return result
    }
    func textCloud(_ text: String,author: String) throws -> Cloud {
        let w=1000, h=600
        var size: CGFloat = 48, f = Self.font(48), lines = Self.wrap(text,Self.font(48),936)
        while lines.count > 3 && size > 22 { size -= 2; f = Self.font(size); lines = Self.wrap(text,f,936) }
        guard lines.allSatisfy({ CTLineGetTypographicBounds(Self.line($0,f),nil,nil,nil) <= 936 }) else { throw FlowError.invalid("Quote overflow") }
        let spacing = (CTFontGetAscent(f)+CTFontGetDescent(f)+CTFontGetLeading(f))*1.1
        let total = spacing*CGFloat(lines.count)+50
        var pixels = [UInt8](repeating:0,count:w*h*4)
        try pixels.withUnsafeMutableBytes { raw in
            guard let context = Self.context(raw.baseAddress!,w,h) else { throw FlowError.invalid("Text context") }
            // The bitmap rows are sampled top to bottom, matching image targets.
            var baseline = (CGFloat(h)+total)/2-CTFontGetAscent(f)
            for text in lines {
                let line = Self.line(text,f), width = CTLineGetTypographicBounds(line,nil,nil,nil)
                context.textPosition = CGPoint(x:(Double(w)-width)/2,y:baseline); CTLineDraw(line,context); baseline -= spacing
            }
            let line = Self.line("— "+author,Self.font(24,italic:true))
            context.textPosition = CGPoint(x:(Double(w)-CTLineGetTypographicBounds(line,nil,nil,nil))/2,y:baseline-18); CTLineDraw(line,context)
        }
        return try sample(pixels,w:w,h:h,image:false,label:text+" — "+author)
    }
    private func sample(_ pixels: [UInt8],w: Int,h: Int,image: Bool,label: String) throws -> Cloud {
        var rng = SampleRandom(), candidates: [Int] = []
        // RGB is already premultiplied by alpha.
        func luma(_ at: Int) -> Float { let k=at*4; return (0.2126*Float(pixels[k])+0.7152*Float(pixels[k+1])+0.0722*Float(pixels[k+2]))/255 }
        for y in 1..<h-1 { for x in 1..<w-1 {
            let at=y*w+x, l=luma(at)
            let weight = image ? min(1,l*l+(abs(l-luma(at+1))+abs(l-luma(at+w)))*0.3) : l
            if weight > 0.06 && rng.next() < weight { candidates.append(at) }
        } }
        guard !candidates.isEmpty else { throw FlowError.invalid("Empty cloud: \(label)") }
        var points: [SIMD4<Float>] = []; points.reserveCapacity(image ? 65536 : 16384)
        let scale: Float = 0.96/Float(max(w,h))
        for _ in 0..<(image ? 65536 : 16384) {
            let at=candidates[min(candidates.count-1,Int(rng.next()*Float(candidates.count)))], row=at/w
            let x=(Float(at%w)-Float(w)/2+rng.next()-0.5)*scale
            let y=(Float(row)-Float(h)/2+rng.next()-0.5)*scale
            points.append(SIMD4(x,y,image ? luma(at) : 1,1-smooth(0.36,0.50,y)))
        }
        return Cloud(points:points,label:label)
    }
}

/// Exactly three retained recipes, one serial in-flight load, stale results discarded.
final class CloudCache {
    let catalog: Catalog
    private let queue = DispatchQueue(label:"ru.krajcik.flow.clouds",qos:.utility)
    private var wanted: [Int] = [], loading = false, closed = false
    private(set) var clouds: [Int:Cloud] = [:]
    private(set) var failures: Set<Int> = []
    init(_ catalog: Catalog) { self.catalog=catalog }
    func request(_ index: Int) {
        guard !closed else { return }
        wanted=(0..<3).map { (index+$0)%catalog.scenes.count }
        clouds=clouds.filter { wanted.contains($0.key) }; pump()
    }
    func close() { closed=true; clouds.removeAll(); wanted.removeAll() }
    private func pump() {
        guard !closed,!loading, let index=wanted.first(where:{ clouds[$0] == nil && !failures.contains($0) }) else { return }
        loading=true
        let catalog=self.catalog
        queue.async { [weak self] in
            let result: Result<Cloud,Error> = autoreleasepool { Result { try catalog.load(index) } }
            DispatchQueue.main.async { [weak self] in
                guard let self=self,!self.closed else { return }; self.loading=false
                if self.wanted.contains(index) {
                    switch result { case .success(let cloud): self.clouds[index]=cloud; case .failure(let error): self.failures.insert(index); NSLog("Flow scene error: %@",String(describing:error)) }
                }
                self.pump()
            }
        }
    }
}
