import Cocoa
import ScreenSaver
import QuartzCore
let app=NSApplication.shared
let bundle=Bundle(path:CommandLine.arguments[1])!
try bundle.loadAndReturnError()
guard let type=bundle.principalClass as? ScreenSaverView.Type,let view=type.init(frame:NSRect(x:0,y:0,width:960,height:540),isPreview:true),let layer=view.layer as? CAMetalLayer,layer.device != nil else { fatalError("Invalid ScreenSaverView principal class / Metal startup") }
view.startAnimation();view.animateOneFrame();view.stopAnimation();view.animateOneFrame()
view.startAnimation();view.stopAnimation()
print("PASS .saver bundle: dynamic loading, principal class, Metal startup, repeated start/stop")
