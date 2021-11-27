import Foundation

class Updater: NSObject {
    private let urlSession: URLSession
    private var sessionTask: URLSessionDownloadTask?
    private let completion: ((_: String?, _: Error?) -> Void)
    private let progress: ((Double) -> Void)
    private var observation: NSKeyValueObservation?

    init (url: String, completion: @escaping ((_: String?, _: Error?) -> Void), progress: @escaping (Double) -> Void) throws {
        guard let u = URL(string: url) else {
            let error = NSError(domain: "", code: 100, userInfo: [NSLocalizedDescriptionKey: "Wrong url"])
            completion(nil, error)

            throw error
        }


        self.completion = completion
        self.progress = progress

        let config = URLSessionConfiguration.default
        config.isDiscretionary = true
        config.sessionSendsLaunchEvents = true

        self.urlSession = URLSession(configuration: URLSessionConfiguration.default)

        super.init()

        let sessionTask = urlSession.downloadTask(with: u) { tempURL, response, error in
            print("Updater: finished fetching")

            guard let url = tempURL else {
                completion(nil, error)
                return
            }

            self.downloadAndExtract(url: url, fileName:u.lastPathComponent,  completion: completion)
        }

        let observation = sessionTask.progress.observe(\.fractionCompleted) { p, _ in
            print("Updater: progress: ", p.fractionCompleted)

            progress(p.fractionCompleted)
        }

        self.observation = observation
        self.sessionTask = sessionTask
        sessionTask.resume()
    }

    static func clean (files: [String], completion: @escaping (()->Void)) {
        DispatchQueue.global(qos: .background).async {
            let fileManager = FileManager()

            for file in files {
                do {
                    print("Updater: remove: ", file)

                    try fileManager.removeItem(atPath: file)
                } catch {

                }
            }

            DispatchQueue.main.async {
                completion()
            }
        }
    }

    private func downloadAndExtract(url: URL, fileName: String, completion: ((_: String?, _: Error?) -> Void)) {
        let fileManager = FileManager()

        let destinationURL = fileManager.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let sourceUrl = fileManager.temporaryDirectory.appendingPathComponent(fileName)

        do {
            try fileManager.createDirectory(at: destinationURL, withIntermediateDirectories: true, attributes: nil)

            let data = try Data(contentsOf: url)
            try data.write(to: sourceUrl)

            try fileManager.unzipItem(at: sourceUrl, to: destinationURL)
        } catch {
            print("Updater: Extraction of ZIP archive failed with error:\(error)")

            completion(nil, error)

            return
        }

        guard let persistentStorageDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first?.appendingPathComponent("updater") else {
            let error = NSError(domain: "", code: 100, userInfo: [NSLocalizedDescriptionKey: "Can not create path"])
            completion(nil, error)

            return
        }

        var persistentStorage = persistentStorageDir.appendingPathComponent(fileName)

        do {
            try fileManager.removeItem(at: persistentStorage)
        } catch {

        }

        do {
            try fileManager.createDirectory(at: persistentStorageDir, withIntermediateDirectories: true, attributes: nil)

            try fileManager.moveItem(at: destinationURL, to: persistentStorage)

            var resourceValues = URLResourceValues()
            resourceValues.isExcludedFromBackup = true

            try persistentStorage.setResourceValues(resourceValues)
        } catch {
            print("Updater: Copy failed with error:\(error)")

            completion(nil, error)
        }

        do {
            try fileManager.removeItem(at: sourceUrl)
        } catch {
            print("Updater: Remove failed with error:\(error)")
        }

        completion(persistentStorage.path, nil)
    }
}
