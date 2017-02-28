package cordova.plugin.zebraprinter;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.device.ZebraIllegalArgumentException;
import com.zebra.sdk.graphics.ZebraImageFactory;
import com.zebra.sdk.graphics.ZebraImageI;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterUsb;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.printer.discovery.UsbDiscoverer;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ZebraPrinterPlugin extends CordovaPlugin {
    // Global
    private static final String LOG_TAG = "ZebraPrinterPlugin";

    // Wireless
    private Connection printerConnection;
    private ZebraPrinter printer;

    // Usb
    private static final String ACTION_USB_PERMISSION = "mx.gob.aguadehermosillo.USB_PERMISSION";
    private PendingIntent mPermissionIntent;
    private boolean hasPermissionToCommunicate = false;
    private UsbManager mUsbManager;
    private DiscoveredPrinterUsb discoveredPrinterUsb;

    public ZebraPrinterPlugin() {
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

      public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();

        if (ACTION_USB_PERMISSION.equals(action)) {
          synchronized (this) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
              if (device != null) {
                hasPermissionToCommunicate = true;
              }
            }
          }
        }
      }

    };

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("printWithImg")) {
          try {
            String mac = args.getString(0);
            String msg = args.getString(1);
            String images = args.getString(2);
            printWithImage(callbackContext, mac, msg, images);
          } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
            e.printStackTrace();
          }
          return true;
        }
        else if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                sendData(callbackContext, mac, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("find")) {
            try {
                findPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("usbPrint")) {
            try {
                String zpl = args.getString(0);
                sendUsbData(callbackContext, zpl);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        else if (action.equals("usbFind")) {
            try {
                findUsbPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    // Wireless Actions
    void findPrinter(final CallbackContext callbackContext) {
      new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                  BluetoothDiscoverer.findPrinters(cordova.getActivity().getApplicationContext(), new DiscoveryHandler() {

                      public void foundPrinter(DiscoveredPrinter printer) {
                          String macAddress = printer.address;
                          //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                          callbackContext.success(printer.address);
                      }

                      public void discoveryFinished() {
                          //Discovery is done
                      }

                      public void discoveryError(String message) {
                          //Error during discovery
                          callbackContext.error(message);
                      }
                  });
              } catch (Exception e) {
                  callbackContext.error(e.getMessage());
              }
            }
        }).start();
    }
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
              printer = connect(mac);
              if (printer != null) {
                  sendLabel(msg);
                  callbackContext.success(msg);
              } else {
                  disconnect();
                  callbackContext.error("Error");
              }
          }
        }).start();
    }
    void printWithImage(final CallbackContext callbackContext, final String mac, final String msg, final String images) throws IOException {
    new Thread(new Runnable() {
      @Override
      public void run() {
        printer = connect(mac);
        if (printer != null) {
          printLabelWithImage(msg, images);
          callbackContext.success(msg);
        } else {
          disconnect();
          callbackContext.error("Error");
        }
      }
    }).start();
  }

    // Usb Actions
    void findUsbPrinter(final CallbackContext callbackContext) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mUsbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
                    mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
                    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                    getApplicationContext().registerReceiver(mUsbReceiver, filter);

                    UsbDiscoveryHandler handler = new UsbDiscoveryHandler();
                    UsbDiscoverer.findPrinters(getApplicationContext(), handler);

                    if (handler.printers != null && handler.printers.size() > 0)
                    {
                      discoveredPrinterUsb = handler.printers.get(0);
                      mUsbManager.requestPermission(discoveredPrinterUsb.device, mPermissionIntent);
                      callbackContext.success("true");
                    }
                    else
                    {
                      callbackContext.error("No se encontro impresora");
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        }).start();
    }
    void sendUsbData(final CallbackContext callbackContext, final String zpl) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (hasPermissionToCommunicate)
                {
                    try {
                        Connection conn = discoveredPrinterUsb.getConnection();
                        conn.open();
                        conn.write(zpl.getBytes());
                        conn.close();
                        callbackContext.success("true");
                    } catch (ConnectionException e) {
                        callbackContext.error("ERROR");
                    }
                }
                else
                {
                    callbackContext.error("PERMISSION");
                }
            }
        }).start();
    }


    //Private methods
    private Activity getActivity() { return this.cordova.getActivity(); }
    private Context getApplicationContext() { return this.cordova.getActivity().getApplicationContext(); }

    public ZebraPrinter connect(String mac) {
        printerConnection = null;
        if (isBluetoothSelected()) {
            printerConnection = new BluetoothConnection(mac);
        }

        try {
            printerConnection.open();
        } catch (ConnectionException e) {
            sleep(1000);
            disconnect();
        }

        ZebraPrinter printer = null;

        if (printerConnection.isConnected()) {
            try {
                printer = ZebraPrinterFactory.getInstance(printerConnection);
                PrinterLanguage pl = printer.getPrinterControlLanguage();
            } catch (ConnectionException e) {
                printer = null;
                sleep(500);
                disconnect();
            } catch (ZebraPrinterLanguageUnknownException e) {
                printer = null;
                sleep(500);
                disconnect();
            }
        }

        return printer;
    }
    private Boolean isBluetoothSelected(){
      return true;
    }
    public void disconnect() {
        try {
            if (printerConnection != null) {
                printerConnection.close();
            }
        } catch (ConnectionException e) {
        } finally {
        }
    }
    private void sendLabel(String msg) {
        try {
            byte[] configLabel = getConfigLabel(msg);
            printerConnection.write(configLabel);
            sleep(500);
        } catch (ConnectionException e) {

        } finally {
            disconnect();
        }
    }
    private void printLabelWithImage(String msg, String images) {
      try {
        //SEND IMAGES FIRST
        List<String> imagesList = Arrays.asList(new Gson().fromJson(images, String[].class));
        int counter = 0;
        for (String base64 : imagesList) {
          Bitmap bmp = decodeBase64(base64);
          int height = bmp.getHeight();
          int width = bmp.getWidth();
          bmp.setHasAlpha(true);

          Bitmap monochromeBmp = getMonochromeBitmap(bmp, width, height);
          ZebraImageI image = ZebraImageFactory.getImage(monochromeBmp);
          printer.storeImage("IMAGE" + counter, image, width, height);
          counter++;
          bmp.recycle();
          monochromeBmp.recycle();
        }

        //SEND ZPL WITH PREVIOUS IMAGES REFERENCES
        byte[] configLabel = getConfigLabel(msg);
        printerConnection.write(configLabel);
        sleep(1000);
/*      } catch (ConnectionException e) {

      } catch(IOException e) {

      } catch(ZebraIllegalArgumentException e) {

      }*/
      } catch(Exception e) {
        Toast.makeText(cordova.getActivity(), "Error de impresión", Toast.LENGTH_SHORT);
      }
      finally {
        disconnect();
      }
  }

    private byte[] getConfigLabel(String msg) {
        return msg.getBytes();
    }
    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private static Bitmap decodeBase64(String input) {
      byte[] decodedBytes = Base64.decode(input, 0);
      BitmapFactory.Options op = new BitmapFactory.Options();
      op.inPreferredConfig = Bitmap.Config.ARGB_8888;
      return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, op);
    }
    private Bitmap getMonochromeBitmap(Bitmap src, int width, int height){
      Bitmap monochromeBmp = src.copy(Bitmap.Config.ARGB_8888, true);
      src.recycle();
      int pixel;
      int k = 0;
      int B=0,G=0,R=0,A=0;
      try{
        for(int x = 0; x < height; x++) {
          for(int y = 0; y < width; y++, k++) {
            pixel = monochromeBmp.getPixel(y, x);

            A = Color.alpha(pixel);
            R = Color.red(pixel);
            G = Color.green(pixel);
            B = Color.blue(pixel);
            R = G = B = (int)(0.299 * R + 0.587 * G + 0.114 * B);

            if (A < 128) {
              monochromeBmp.setPixel(y,x, Color.WHITE);
            } else {
              if (R < 128) {
                monochromeBmp.setPixel(y,x, Color.BLACK);
              } else {
                monochromeBmp.setPixel(y,x, Color.WHITE);
              }
            }
          }
        }

        return monochromeBmp;
      }
      catch (Exception e) {
        // TODO: handle exception
        return null;
      }
    }

    //USB Discovery Handler
    class UsbDiscoveryHandler implements DiscoveryHandler {
        public List<DiscoveredPrinterUsb> printers;
        public UsbDiscoveryHandler() {
            printers = new LinkedList<DiscoveredPrinterUsb>();
        }
        public void foundPrinter(final DiscoveredPrinter printer) {
            printers.add((DiscoveredPrinterUsb) printer);
        }
        public void discoveryFinished() {
        }
        public void discoveryError(String message) {
        }
    }
}