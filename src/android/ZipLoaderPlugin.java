package com.ziploader.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipLoaderPlugin extends CordovaPlugin {

    private static final String TAG = "LoaderUtils";
    private static final String PROGRESS = "progress";
    private static final String URL = "url";

    private CallbackContext callbackContext;

    @Override
    public boolean execute(String actionKey, JSONArray args, CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
        try {
            switch (actionKey) {
                case "downloadZip":
                    String url = args.optString(0);
                    downloadZip(url);
                    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                    pluginResult.setKeepCallback(true); // Keep callback
                    callbackContext.sendPluginResult(pluginResult);
                    break;
                case "remove":
                    final JSONArray array = args.getJSONArray(0);
                    if (array != null) {
                        deletePaths(convertToStringArray(array));
                    } else {
                        callbackContext.error("Empty array paths");
                    }
                    break;
                default:
                    return false;
            }
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            callbackContext.error(e.getMessage());
            return false;
        }
    }

    /**
     * Start download and unzip file. Delete zip file before send success result.
     * Before exception deleted parent path.
     * Path/zip name is current date UTC (yyyy_MM_dd_HH_mm_ss)
     *
     * @param urlString - url link zip file
     */
    public void downloadZip(String urlString) {
        Log.d(TAG, String.format("downloadZip %s", urlString));

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    download(urlString);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    /**
     * @param urlString - url link zip file
     */
    private void download(String urlString) {
        try {
            URL url = new URL(urlString);
            String zipName = new File(urlString).getName();
            File pathFolder = new File(
                    cordova.getActivity().getBaseContext().getCacheDir() + "/" +
                            getFileNameWithoutExtension(zipName));
            try {

                if (!pathFolder.exists()) {
                    pathFolder.mkdirs();
                }

                File file = new File(pathFolder, zipName);
                int count;

                URLConnection connection = url.openConnection();
                connection.connect();
                int fileLength = connection.getContentLength();
                // download the file
                InputStream input = new BufferedInputStream(url.openStream());
                FileOutputStream output = new FileOutputStream(file.getAbsolutePath());

                byte data[] = new byte[1024];
                int downloaded = 0;
                float percentage = 0;
                while ((count = input.read(data)) != -1) {
                    // writing data to file
                    output.write(data, 0, count);
                    downloaded += count;
                    // post progress
                    if (percentage != ((float) downloaded / (float) fileLength)) {
                        percentage = ((float) downloaded / (float) fileLength);
                        emit(PROGRESS, percentage, true);
                    }
                }

                // flushing output
                output.flush();

                // closing streams
                output.close();
                input.close();

                //decompress file
                unZip(file);

            } catch (Exception e) {
                deletePath(pathFolder);
                callbackContext.error(e.getMessage());
            }
        } catch (MalformedURLException e) {
            callbackContext.error(e.getMessage());
        }
    }

    /**
     * @param fileZip - zip file (delete after unzip, or delete path if have exception)
     */
    private void unZip(File fileZip) {
        Log.d(TAG, String.format("un Zip %s", fileZip.getAbsolutePath()));
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(fileZip.getAbsolutePath())))) {

            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];

            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File file = new File(fileZip.getParentFile(), zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();

                // Fixing Zip Path Traversal Vulnerability
                try {
                    ensureZipPathSafety(file, fileZip.getParentFile());
                } catch (Exception e) {
                    deletePath(fileZip);
                    callbackContext.error(e.getMessage());
                    return;
                }

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
                moveFiles(fileZip.getParentFile());
            } else {
                deletePath(fileZip);
                callbackContext.error("Zip file deleted problem");
            }
        } catch (Exception e) {
            deletePath(fileZip);
            callbackContext.error(e.getMessage());
        }
    }

    private void moveFiles(File unZipCachePath){
        File unZipPath = new File(cordova.getActivity().getBaseContext().getFilesDir(), unZipCachePath.getName());
        unZipCachePath.renameTo(unZipPath);
        Log.d(TAG, String.format("success %s", Objects.requireNonNull(unZipPath).getAbsolutePath()));
        emit(URL, Objects.requireNonNull(unZipPath).getAbsolutePath(), false);
    }

    // delete parent path if have problem
    private void deletePath(File file) {
        if (Objects.requireNonNull(file.getParentFile()).exists()) {
            file.getParentFile().delete();
        }
    }

    /**
     * this method get a JSONArray of strings and convert it to String array
     *
     * @param json JSONArray object that contains string elements
     * @return the provided JSONArray converted to String array
     * @throws JSONException
     */
    private String[] jsonArrayToStringArray(JSONArray json) throws JSONException {
        if (json.length() == 0) {
            return null;
        }
        String[] arr = new String[json.length()];
        for (int i = 0; i < json.length(); i++) {
            arr[i] = json.getString(i);
        }
        return arr;
    }

    /**
     * This method get the JSONArray object from JS and convert it to String array.
     * The first element can be string representation of a string array or JSONArray of strings.
     *
     * @param json JSONArray object from JS.
     * @return String array or null
     */
    private String[] convertToStringArray(JSONArray json) {
        if (json == null || json.length() == 0) {
            return null;
        }

        String[] arr = null;
        try {
            Object obj = json.get(0);
            if (obj instanceof String) {
                arr = stringToArray((String) obj);
            } else if (obj instanceof JSONArray) {
                arr = jsonArrayToStringArray((JSONArray) obj);
            }
        } catch (JSONException | ClassCastException e) {
            e.printStackTrace();
            return null;
        }
        return arr;
    }

    /**
     * takes string representation of a string array and converts it to an array. use this method because old version of cordova cannot pass an array to native.
     * newer versions can, but can break flow to older users
     *
     * @param str
     * @return String array
     */
    private String[] stringToArray(String str) {
        String[] realArr = null;
        str = str.substring(1, str.length() - 1);
        str = str.replaceAll(" ", "");
        realArr = str.split("[ ,]");
        return realArr;
    }

    private void deletePaths(String[] paths) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (paths == null)
                        return;
                    boolean result = false;
                    Log.d(TAG, " try delete paths : " + Arrays.toString(paths));
                    for (String path : paths) {
                        File file = new File(path);
                        Log.d(TAG, file.getAbsolutePath() + " is exists : " + file.exists());
                        if (file.exists()) {
                            result = file.delete();
                            Log.d(TAG, file.getAbsolutePath() + " is deleted :" + result);
                        } else {
                            result = true;
                        }
                    }
                    if (result) {
                        callbackContext.success();
                    } else {
                        callbackContext.error("Delete paths has problem!");
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }

            }
        });
    }

    public void emit(String eventName, Object param, boolean setKeepCallback) {
        PluginResult result = new PluginResult(PluginResult.Status.OK,
                new JSONObject(new HashMap<String, Object>() {{
                    put(eventName, param);
                }}).toString());
        result.setKeepCallback(setKeepCallback);
        callbackContext.sendPluginResult(result);
    }

    private static final Pattern ext = Pattern.compile("(?<=.)\\.[^.]+$");

    public static String getFileNameWithoutExtension(String name) {
        return ext.matcher(name).replaceAll("");
    }

    private void ensureZipPathSafety(final File outputFile, final String destDirectory) throws Exception {
        String destDirCanonicalPath = (new File(destDirectory)).getCanonicalPath();
        String outputFilecanonicalPath = outputFile.getCanonicalPath();
        if (!outputFileCanonicalPath.startsWith(destDirCanonicalPath)) {
            throw new Exception(String.format("Found Zip Path Traversal Vulnerability with %s", canonicalPath));
        }
    }
}