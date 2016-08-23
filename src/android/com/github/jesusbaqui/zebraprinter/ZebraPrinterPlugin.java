package cordova.plugins.zebraprinter;

import java.util.LinkedList;
import java.util.List;

import android.app.Activity;

import java.io.IOException;
import android.os.Bundle;
import android.os.Looper;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.discovery.BluetoothDiscoverer;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterUsb;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterBluetooth;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.printer.discovery.UsbDiscoverer;
import com.zebra.sdk.comm.BluetoothConnection;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.TcpConnection;
import com.zebra.sdk.printer.PrinterLanguage;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

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

        if (action.equals("print")) {
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
    public Boolean isBluetoothSelected(){
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
            sleep(1500);
        } catch (ConnectionException e) {

        } finally {
            disconnect();
        }
    }

    private byte[] getConfigLabel(String msg) {
        PrinterLanguage printerLanguage = printer.getPrinterControlLanguage();

        byte[] configLabel = null;
        if (printerLanguage == PrinterLanguage.ZPL) {
            configLabel = msg.getBytes();
        } else if (printerLanguage == PrinterLanguage.CPCL) {
            String cpclConfigLabel = "! 0 200 200 406 1\r\n" + "ON-FEED IGNORE\r\n" + "BOX 20 20 380 380 8\r\n" + "T 0 6 137 177 TEST\r\n" + "PRINT\r\n";
            configLabel = cpclConfigLabel.getBytes();
        }
        return configLabel;
    }
    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
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
