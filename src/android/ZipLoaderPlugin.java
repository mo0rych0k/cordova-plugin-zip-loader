package binisystem.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipLoaderPlugin extends CordovaPlugin {

    private static final String TAG = "LoaderUtils";
    private final static String timeName =
            new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US)
            .format(new Date());
    private static final String TEMP_PATH = "/Temp/" + timeName;


    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.i(TAG, "Initialize plugin");
    }

    @Override
    public boolean execute(String actionKey, JSONArray args, CallbackContext callbackContext) {
        Log.d(TAG, String.format("Execute %s", actionKey));

        switch (actionKey) {
            case "downloadZip":
                String url = args.optString(0);
                downloadZip(url, callbackContext);
                break;
            default:
                return false;
        }
        return true;
    }

    /**
     * Start download and unzip file. Delete zip file before send success result.
     * Before exception deleted parent path.
     * Path/zip name is current date UTC (yyyy_MM_dd_HH_mm_ss)
     * @param urlString - url link zip file
     * @param callback  - callback result
     */
    public void downloadZip(String urlString, final CallbackContext callback) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    download(urlString, callback);
                } catch (Exception e) {
                    callback.error(e.getMessage());
                }
            }
        });
    }

    /**
     * @param urlString - url link zip file
     * @param callback  - callback result on main thread
     */
    private void download(String urlString, final CallbackContext callback) {
        File pathFolder = new File(cordova.getActivity().getBaseContext().getFilesDir() + TEMP_PATH);
        File file = new File(pathFolder, timeName + ".zip");

        try {
            int count;

            if (!pathFolder.exists()) {
                pathFolder.mkdirs();
            }

            URL url = new URL(urlString);
            url.openConnection().connect();

            // download the file
            InputStream input = new BufferedInputStream(url.openStream());
            FileOutputStream output = new FileOutputStream(file.getAbsolutePath());

            byte data[] = new byte[1024];

            while ((count = input.read(data)) != -1) {
                // writing data to file
                output.write(data, 0, count);
            }

            // flushing output
            output.flush();

            // closing streams
            output.close();
            input.close();

            //decompress file
            unZip(file, callback);
        } catch (Exception e) {
            deletePath(pathFolder);
            callback.error(e.getMessage());
        }
    }

    /**
     * @param fileZip - zip file (delete after unzip, or delete path if have exception)
     * @param callback - callback result
     */
    private void unZip(File fileZip, final CallbackContext callback) {
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(fileZip.getAbsolutePath())))) {

            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // new
                File file = new File(fileZip.getParentFile(), zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();

                if (!Objects.requireNonNull(dir).isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " +
                            dir.getAbsolutePath());
                }

                if (zipEntry.isDirectory()) {
                    continue;
                }
                try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                    while ((count = zipInputStream.read(buffer)) != -1)
                        fileOutputStream.write(buffer, 0, count);
                }
            }

            if (fileZip.delete()) {
                callback.success(Objects.requireNonNull(Objects.requireNonNull(fileZip.getParentFile()).getAbsolutePath()));
            } else {
                deletePath(fileZip);
                callback.error("Zip file deleted problem");
            }
        } catch (Exception e) {
            deletePath(fileZip);
            callback.error(e.getMessage());
        }
    }

    // delete parent path if have problem
    private void deletePath(File file){
        if(Objects.requireNonNull(file.getParentFile()).exists()) {
            file.getParentFile().delete();
        }
    }

}


