package cordova.plugin.brotherPrinter;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.brother.ptouch.sdk.connection.BluetoothConnectionSetting;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;

import com.brother.ptouch.sdk.LabelInfo;
import com.brother.ptouch.sdk.NetPrinter;
import com.brother.ptouch.sdk.Printer;
import com.brother.ptouch.sdk.PrinterInfo;
import com.brother.ptouch.sdk.PrinterStatus;
import java.util.Set;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;

public class BrotherPrinter extends CordovaPlugin {


    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 1;

    private NetPrinter[] mBluetoothPrinter; // array of storing Printer

    //token to make it easy to grep logcat
    private static final String TAG = "print";

    private CallbackContext callbackctx;

    public void pluginInitialize() {

        Log.d(TAG, "OMG");

        super.pluginInitialize();
        if (!isPermitWriteStorage()) {
            cordova.requestPermission(this,PERMISSION_WRITE_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }


    @Override
    public boolean execute (String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if ("findNetworkPrinters".equals(action)) {
            findNetworkPrinters(callbackContext);
            return true;
        }

        if ("printViaSDK".equals(action)) {
            printViaSDK(args, callbackContext);
            return true;
        }

        if ("printViaWifiInfra".equals(action)) {
            printViaWifiInfra(args, callbackContext);
            return true;
        }

        if ("printViaWifiInfraText".equals(action)) {
            printViaWifiInfraText(args, callbackContext);
            return true;
        }

        if ("findBluetoothPairedPrinters".equals(action)) {
            findBluetoothPairedPrinters(callbackContext);
            return true;
        }

        return false;
    }

    private NetPrinter[] enumerateNetPrinters(String model) {
        Printer myPrinter = new Printer();
        PrinterInfo myPrinterInfo = new PrinterInfo();
        NetPrinter[] netPrinters = myPrinter.getNetPrinters(model);
        return netPrinters;
    }


    private boolean isPermitWriteStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (cordova.getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private void findNetworkPrinters(final CallbackContext callbackctx) {

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try{

                    NetPrinter[] netPrinters720 = enumerateNetPrinters("QL-720NW");
                    NetPrinter[] netPrinters820 = enumerateNetPrinters("QL-820NWB");

                    List<NetPrinter> both = new ArrayList<NetPrinter>(netPrinters720.length + netPrinters820.length);
                    Collections.addAll(both, netPrinters720);
                    Collections.addAll(both, netPrinters820);

                    NetPrinter[] netPrinters = both.toArray(new NetPrinter[both.size()]);

                    int netPrinterCount = netPrinters.length;
                    JSONArray printersArray = new JSONArray();
                    JSONObject printerJson;
                    if (netPrinterCount > 0) {
                        Log.d(TAG, "---- network printers found! ----");
                        for (int i = 0; i < netPrinterCount; i++) {
                            printerJson = new JSONObject();
                            printerJson.put("modelName", netPrinters[i].modelName);
                            printerJson.put("ipAddress", netPrinters[i].ipAddress);
                            printerJson.put("macAddress", netPrinters[i].macAddress);
                            printerJson.put("serNo", netPrinters[i].serNo);
                            printerJson.put("nodeName", netPrinters[i].nodeName);
                            printersArray.put(printerJson);
                            Log.d(TAG,
                                    " idx:    " + Integer.toString(i)
                                            + "\n model:  " + netPrinters[i].modelName
                                            + "\n ip:     " + netPrinters[i].ipAddress
                                            + "\n mac:    " + netPrinters[i].macAddress
                                            + "\n serial: " + netPrinters[i].serNo
                                            + "\n name:   " + netPrinters[i].nodeName
                            );
                        }
                        Log.d(TAG, "---- /network printers found! ----");
                    } else if (netPrinterCount == 0 ) {
                        Log.d(TAG, "!!!! No network printers found !!!!");
                    }
                    JSONObject args = new JSONObject();
                    PluginResult result;
                    Boolean available = netPrinterCount > 0;
                    args.put("found", available);
                    if(available) {
                        args.put("printers" , printersArray);
                    }
                    result = new PluginResult(PluginResult.Status.OK, args);
                    callbackctx.sendPluginResult(result);
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }

    public static Bitmap bmpFromBase64(String base64, final CallbackContext callbackctx){
        try{
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private void printViaSDK(final JSONArray args, final CallbackContext callbackctx) {
        final Bitmap bitmap = bmpFromBase64(args.optString(0, null), callbackctx);
        final int numberOfCopies = args.optInt(1, 1);
        final String macAddress = args.optString(2, null);
        if( macAddress == null ){
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "No Printer paramater passed!");
            callbackctx.sendPluginResult(result);
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try{

                    Printer myPrinter = new Printer();
                    PrinterInfo myPrinterInfo = new PrinterInfo();
                    myPrinterInfo = myPrinter.getPrinterInfo();
                    PluginResult result;
                    myPrinterInfo.printerModel  = PrinterInfo.Model.QL_820NWB;
                    myPrinterInfo.port          = PrinterInfo.Port.BLUETOOTH;;
                    myPrinterInfo.printMode     = PrinterInfo.PrintMode.ORIGINAL;
                    myPrinterInfo.orientation   = PrinterInfo.Orientation.PORTRAIT;
                    myPrinterInfo.paperSize     = PrinterInfo.PaperSize.CUSTOM;

                    myPrinterInfo.labelNameIndex =  LabelInfo.QL700.valueOf("W62RB").ordinal();
                    myPrinterInfo.isAutoCut=true;
                    myPrinterInfo.isCutAtEnd=true;
                    myPrinterInfo.isHalfCut=true;
                    myPrinterInfo.isSpecialTape= false;
                    myPrinterInfo.numberOfCopies = numberOfCopies;

                    myPrinterInfo.macAddress= macAddress;

                    boolean isSet;
                    isSet = myPrinter.setPrinterInfo(myPrinterInfo);

                    if(bitmap == null){
                        result = new PluginResult(PluginResult.Status.ERROR, " Bitmap creation failed");
                        callbackctx.sendPluginResult(result);
                    }

                    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    BluetoothConnectionSetting.setBluetoothAdapter(bluetoothAdapter);

                    PrinterStatus status = myPrinter.printImage(bitmap);

                    String status_code = ""+status.errorCode;

                    result = new PluginResult(PluginResult.Status.OK, status_code);

                    callbackctx.sendPluginResult(result);

                }catch(Exception e){
                    e.printStackTrace();
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Failed to print with bluetooth");
                    callbackctx.sendPluginResult(result);

                }
            }
        });
    }


    /**
     * get paired printers
     */
    private void findBluetoothPairedPrinters(final CallbackContext callbackctx) {

        // get the BluetoothAdapter
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter
                .getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                /** startActivity(enableBtIntent);**/
            }
        }else{
            PluginResult result;
            result = new PluginResult(PluginResult.Status.ERROR, "No BluetoothAdapter found");
            callbackctx.sendPluginResult(result);
            return;
        }

        try {
            JSONObject args = new JSONObject();
            JSONArray printersArray = new JSONArray();
            JSONObject printerJson;
            /*
             * if the paired devices exist, set the paired devices else set the
             * string of "No Bluetooth Printer."
             */
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter != null ? bluetoothAdapter.getBondedDevices() : null;
            if ((pairedDevices != null ? pairedDevices.size() : 0) > 0) {
                args.put("found", true);
                mBluetoothPrinter = new NetPrinter[pairedDevices.size()];
                for (BluetoothDevice device : pairedDevices) {
                    printerJson = new JSONObject();
                    printerJson.put("address", device.getAddress());
                    printerJson.put("name", device.getName());
                    printerJson.put("type", device.getType());
                    printersArray.put(printerJson);
                }
                args.put("printers", printersArray);
                Log.d(TAG, "---- /bluetooth printers found! ----");

            } else {
                args.put("found", false);
                Log.d(TAG, "---- /NO bluetooth printers found! ----");
            }
            // plugin result;
            PluginResult result = new PluginResult(PluginResult.Status.OK, args);
            callbackctx.sendPluginResult(result);
        } catch (Exception e) {
            e.printStackTrace();
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Can't find bluetooth paired devices");
            callbackctx.sendPluginResult(result);
        }
    }

    private void printViaWifiInfra(final JSONArray args, final CallbackContext callbackctx) {

        final Bitmap bitmap = bmpFromBase64(args.optString(0, null), callbackctx);
        final int numberOfCopies = args.optInt(1, 1);
        final String ipAddress = args.optString(2, null);
        final String macAddress = args.optString(3, null);
        if( ipAddress == null || macAddress == null ){
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "No Printer paramater passed!");
            callbackctx.sendPluginResult(result);
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try{

                    Printer myPrinter = new Printer();
                    PrinterInfo myPrinterInfo = new PrinterInfo();
                    myPrinterInfo = myPrinter.getPrinterInfo();
                    PluginResult result;

                    myPrinterInfo.printerModel  = PrinterInfo.Model.QL_820NWB;
                    myPrinterInfo.port          = PrinterInfo.Port.NET;
                    myPrinterInfo.printMode     = PrinterInfo.PrintMode.ORIGINAL;
                    myPrinterInfo.orientation   = PrinterInfo.Orientation.PORTRAIT;
                    myPrinterInfo.paperSize     = PrinterInfo.PaperSize.CUSTOM;
                    myPrinterInfo.labelNameIndex =  LabelInfo.QL700.valueOf("W62RB").ordinal();;
                    myPrinterInfo.isAutoCut=true;
                    myPrinterInfo.isCutAtEnd=true;
                    myPrinterInfo.isHalfCut=true;
                    myPrinterInfo.isSpecialTape= false;

                    myPrinterInfo.numberOfCopies = numberOfCopies;
                    myPrinterInfo.ipAddress     = ipAddress;
                    myPrinterInfo.macAddress    = macAddress;

                    boolean isSet;
                    isSet = myPrinter.setPrinterInfo(myPrinterInfo);

                    if(bitmap == null){
                        result = new PluginResult(PluginResult.Status.ERROR, " Bitmap creation failed");
                        callbackctx.sendPluginResult(result);
                    }

                    PrinterStatus status = myPrinter.printImage(bitmap);
                    result = new PluginResult(PluginResult.Status.OK, ""+status.errorCode);
                    callbackctx.sendPluginResult(result);

                }catch(Exception e){
                    PluginResult result;
                    e.printStackTrace();
                    result = new PluginResult(PluginResult.Status.ERROR,  "FAILED");
                    callbackctx.sendPluginResult(result);

                }
            }
        });
    }

    private void printViaWifiInfraText(final JSONArray args, final CallbackContext callbackctx) {

        final Bitmap bitmap = makePictureFromText(args.optString(0, null));
        final int numberOfCopies = args.optInt(1, 1);
        final String ipAddress = args.optString(2, null);
        final String macAddress = args.optString(3, null);
        final String modelName = args.optString(4, null);
        final String labelNameIndex = args.optString(5, null);
        if( ipAddress == null || macAddress == null ){
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, "No Printer paramater passed!");
            callbackctx.sendPluginResult(result);
        }


        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try{

                    Printer myPrinter = new Printer();
                    PrinterInfo myPrinterInfo = new PrinterInfo();
                    myPrinterInfo = myPrinter.getPrinterInfo();
                    PluginResult result;
                    if(modelName.contains("QL-820NWB")){
                        myPrinterInfo.printerModel  = PrinterInfo.Model.QL_820NWB;
                    }
                    if(modelName.contains("QL-720NW")){
                        myPrinterInfo.printerModel  = PrinterInfo.Model.QL_720NW;
                    }
                    myPrinterInfo.port          = PrinterInfo.Port.NET;
                    myPrinterInfo.printMode     = PrinterInfo.PrintMode.FIT_TO_PAGE;
                    myPrinterInfo.paperSize     = PrinterInfo.PaperSize.CUSTOM;
                    myPrinterInfo.labelNameIndex =  LabelInfo.QL700.valueOf(labelNameIndex).ordinal();
                    myPrinterInfo.isAutoCut=true;
                    myPrinterInfo.isCutAtEnd=true;
                    myPrinterInfo.isHalfCut=false;
                    myPrinterInfo.isSpecialTape= false;

                    myPrinterInfo.numberOfCopies = numberOfCopies;
                    myPrinterInfo.ipAddress     = ipAddress;
                    myPrinterInfo.macAddress    = macAddress;

                    boolean isSet = myPrinter.setPrinterInfo(myPrinterInfo);

                    if(bitmap == null){
                        result = new PluginResult(PluginResult.Status.ERROR, " Bitmap creation failed");
                        callbackctx.sendPluginResult(result);
                    }

                    PrinterStatus status = myPrinter.printImage(bitmap);
                    result = new PluginResult(PluginResult.Status.OK, ""+status.errorCode);
                    callbackctx.sendPluginResult(result);

                } catch(Exception e){
                    PluginResult result;
                    e.printStackTrace();
                    result = new PluginResult(PluginResult.Status.ERROR,  "FAILED");
                    callbackctx.sendPluginResult(result);

                }
            }
        });
    }

    public Bitmap makePictureFromText(String text) {
        Bitmap textBitmap = makeBitmapFromText(text, 18f);
        return textBitmap;
    }

    private Bitmap makeBitmapFromText(String text, Float size) {
        Rect		bounds	 	= new Rect();
        Boolean 	antiAlias 	= (size < 20) ?  true : false;
        TextPaint textPaint 	= new TextPaint();

        textPaint.setColor(Color.BLACK);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(size);
        textPaint.setAntiAlias(antiAlias);

        final Paint fontPaint = new Paint();
        fontPaint.setColor( Color.BLACK );
        fontPaint.setAntiAlias( true );
        fontPaint.setTextSize( size );

        TextRect textRect = new TextRect( fontPaint );

        final int height = textRect.prepare(text, 250, 10000);

        Bitmap bmp = Bitmap.createBitmap(250 , height, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);

        Canvas nameCanvas = new Canvas(bmp);
        textRect.draw(nameCanvas, 0 ,0);
        return bmp;
    }


}
