import Cocoa
import ScreenSaver
import QuartzCore

@objc(FlowScreenSaver)
public final class FlowScreenSaver: ScreenSaverView {
    private var renderer: FlowRenderer?
    private var running=false
    private var errorLabel: NSTextField?
    var flowRenderer: FlowRenderer? { renderer }
    public override init?(frame: NSRect,isPreview: Bool) {
        super.init(frame:frame,isPreview:isPreview)
        animationTimeInterval=1/60
        wantsLayer=true
        let metal=CAMetalLayer(); layer=metal
        do { renderer=try FlowRenderer(layer:metal,bundle:Bundle(for:FlowScreenSaver.self)) }
        catch {
            NSLog("Flow startup failed: %@",String(describing:error))
            let label=NSTextField(wrappingLabelWithString:"Не удалось запустить Metal: \(error)")
            label.textColor = .white; label.frame=bounds.insetBy(dx:20,dy:20); label.autoresizingMask=[.width,.height]; addSubview(label); errorLabel=label
        }
    }
    required init?(coder: NSCoder) { super.init(coder:coder) }
    public override func viewDidMoveToWindow() { super.viewDidMoveToWindow(); resizeDrawable() }
    public override func setFrameSize(_ size: NSSize) { super.setFrameSize(size); resizeDrawable() }
    public override func viewDidChangeBackingProperties() { super.viewDidChangeBackingProperties(); resizeDrawable() }
    private func resizeDrawable() {
        guard let metal=layer as? CAMetalLayer else { return }
        let scale=window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 2
        CATransaction.begin(); CATransaction.setDisableActions(true)
        metal.contentsScale=scale; metal.drawableSize=CGSize(width:max(1,bounds.width*scale),height:max(1,bounds.height*scale)); CATransaction.commit()
    }
    public override func startAnimation() { super.startAnimation(); running=true; renderer?.start() }
    public override func stopAnimation() { running=false; renderer?.stop(); super.stopAnimation() }
    public override func animateOneFrame() { guard running else { return }; renderer?.draw() }
}
