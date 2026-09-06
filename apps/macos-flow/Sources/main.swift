import Cocoa
import ScreenSaver

final class PreviewDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    var window: NSWindow!
    var saver: FlowScreenSaver!
    func applicationDidFinishLaunching(_ notification: Notification) {
        let frame=NSRect(x:0,y:0,width:1280,height:800)
        window=NSWindow(contentRect:frame,styleMask:[.titled,.closable,.miniaturizable,.resizable],backing:.buffered,defer:false)
        window.title="Поток · TV Lab — 100 изображений / 1000 цитат"
        saver=FlowScreenSaver(frame:frame,isPreview:false)!; saver.autoresizingMask=[.width,.height]
        window.contentView=saver; window.delegate=self; window.center(); window.makeKeyAndOrderFront(nil)
        saver.startAnimation()
        NSApp.activate(ignoringOtherApps:true)
        if CommandLine.arguments.contains("--fullscreen") { window.toggleFullScreen(nil) }
        if let i=CommandLine.arguments.firstIndex(of:"--seconds"),CommandLine.arguments.count>i+1,let seconds=Double(CommandLine.arguments[i+1]) {
            DispatchQueue.main.asyncAfter(deadline:.now()+seconds) { NSApp.terminate(nil) }
        }
    }
    func windowWillClose(_ notification: Notification) { saver.stopAnimation(); NSApp.terminate(nil) }
    func applicationWillTerminate(_ notification: Notification) {
        saver?.stopAnimation()
        if let r=saver?.flowRenderer,r.frames>1 {
            let elapsed=CACurrentMediaTime()-r.firstFrame
            var report=r.presentation.report()
            report["width"]=r.layer.drawableSize.width;report["height"]=r.layer.drawableSize.height;report["particles"]=r.gpu.count
            if let data=try? JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys]),let text=String(data:data,encoding:.utf8) { print(text) }
            print("Flow preview: \(r.frames) submitted frames / \(elapsed)s; \(Double(r.frames)/elapsed) submitted fps")
        }
    }
}
if let index=CommandLine.arguments.firstIndex(of:"--validate"),CommandLine.arguments.count>index+1 {
    do { try validateFlow(CommandLine.arguments[index+1]); exit(0) }
    catch { fputs("FAIL: \(error)\n",stderr); exit(1) }
}
let app=NSApplication.shared
app.setActivationPolicy(.regular)
let delegate=PreviewDelegate(); app.delegate=delegate
app.run()
