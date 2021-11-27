import Foundation

@objc(ZipLoaderPlugin)
class ZipLoaderPlugin: CDVPlugin {
    override func pluginInitialize() {
        super.pluginInitialize()
    }

    @objc(downloadZip:)
    func downloadZip(command: CDVInvokedUrlCommand) {
        guard let url = command.argument(at: 0) as? String else {
            print("UnzipPlugin: No URL")
            self.sendResult(CDVPluginResult(status: CDVCommandStatus_ERROR), command: command)
            return
        }

        do {
            let _ = try Updater(url: url,
                                completion: { path, error in
                             if let p = path {
                                print("UnzipPlugin: finish \(p)")
                                self.sendUrl(url: p, command: command)
                             } else {
                                self.sendResult(CDVPluginResult(status: CDVCommandStatus_ERROR), command: command)
                             }
            },
                             progress: { p in
                                self.emitProgress(progress: p, command: command)
            })

        } catch {
            print("UnzipPlugin: Error:\(error)")

            self.sendResult(CDVPluginResult(status: CDVCommandStatus_ERROR), command: command)
        }
    }

    @objc(remove:)
    func remove(command: CDVInvokedUrlCommand) {
        guard let urls = command.argument(at: 0) as? [String] else {
            print("UnzipPlugin: No urls")
            self.sendResult(CDVPluginResult(status: CDVCommandStatus_ERROR), command: command)
            return
        }

        Updater.clean(files: urls) {
            self.sendResult(CDVPluginResult(status: CDVCommandStatus_OK), command: command)
        }
    }

    private func sendResult(_ message: CDVPluginResult?, command: CDVInvokedUrlCommand) {
        self.commandDelegate.send(message, callbackId: command.callbackId)
    }

    private func sendUrl(url: String, command: CDVInvokedUrlCommand) {
        sendResult(dict: ["url": url], finish: true, command: command)
    }

    private func emitProgress(progress: Double, command: CDVInvokedUrlCommand) {
        sendResult(dict: ["progress": progress], finish: false, command: command)
    }

    private func sendResult(dict: [String: Any], finish: Bool, command: CDVInvokedUrlCommand) {
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: dict, options: .prettyPrinted)

            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: String(decoding: jsonData, as: UTF8.self))
            result?.setKeepCallbackAs(!finish)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        } catch {
            print("UnzipPlugin: Error:\(error)")

            self.sendResult(CDVPluginResult(status: CDVCommandStatus_ERROR), command: command)
        }
    }
}
