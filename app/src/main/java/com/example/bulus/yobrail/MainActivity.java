package com.example.bulus.yobrail;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Vibrator;
import android.content.Context;
import android.widget.Toast;

import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.example.bulus.yobrail.helper.ImageHelper;
import com.microsoft.projectoxford.vision.contract.LanguageCodes;
import com.microsoft.projectoxford.vision.contract.Line;
import com.microsoft.projectoxford.vision.contract.OCR;
import com.microsoft.projectoxford.vision.contract.Region;
import com.microsoft.projectoxford.vision.contract.Word;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_TAKE_PHOTO = 0;

    //menu buttons (LU-left-up)
    private Button button_practise;
    private TextView device_status; //connected or disconnected
    private File mFilePhotoTaken;    // File of the photo taken with camera
    private Uri mUriPhotoTaken;     // The URI of photo taken from gallery
    private VisionServiceClient client; //Client connected to cloud
    private Uri mImageUri;   // The URI of the image selected to detect.
    private Bitmap mBitmap; // The image selected to detect.
    private EditText mEditText; // The edit to show status and result.
    public final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbSerialDevice serialPort;
    private UsbDeviceConnection connection;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (granted) {
                    device_status.setText("Połączono"); //Zmiana informacji o podłączneiu urządzenia
                    Toast.makeText(context, "USB Gotowe!", Toast.LENGTH_SHORT).show();    //"Toast" o poprawnym podłączeniu urządzenia
                    connection = usbManager.openDevice(device);
                    serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                    if (serialPort != null) {
                        if (serialPort.open()) { //Set Serial Connection Parameters.
                            serialPort.setBaudRate(9600);
                            serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                            serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                            serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                            serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);

                        } else {
                            Log.d("SERIAL", "PORT NOT OPEN");
                        }
                    } else {
                        Log.d("SERIAL", "PORT IS NULL");
                    }
                } else {
                    Toast.makeText(context, "USB Niegotowe!", Toast.LENGTH_SHORT).show();   //"Toast" o odrzuceniu urządzenia
                    Log.d("SERIAL", "PERM NOT GRANTED");
                }
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                FindUsbDevices();
            } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                device_status.setText("Nie połączono"); //Zmiana informacji o podłączneiu urządzenia
                Toast.makeText(context, "USB Odłączone!", Toast.LENGTH_SHORT).show();   //"Toast" o odłączeniu urządzenia
                serialPort.close();

            }
        };
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mEditText = (EditText)findViewById(R.id.mEditText);
        button_practise = (Button)findViewById(R.id.button_practise);   //button practice implementation
        device_status = (TextView)findViewById(R.id.device_value);     //Implementacja TextView statusu urządzenia

        //Klient do połączenia z chmurą
        if (client==null){
            client = new VisionServiceRestClient(getString(R.string.subscription_key), getString(R.string.subscription_apiroot));
        }

        //listeners submenu
        button_practise.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent newIntent = new Intent(getApplicationContext(), SubMenu2.class);
                startActivity(newIntent);
            }
        });

        usbManager = (UsbManager) getSystemService(this.USB_SERVICE);

        //Filtry powiadomień
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
        FindUsbDevices();
    }

    public void FindUsbDevices() {

        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x1A86)//Arduino Vendor ID
                {
                    PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                    usbManager.requestPermission(device, pi);
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }
                if (!keep)
                    break;
            }
        }
    }

    public void takePhoto(View view) {
        Intent newIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        newIntent.putExtra("android.intent.extra.quickCapture",true);
        if (newIntent.resolveActivity(getPackageManager()) != null) {
            // Save the photo taken to a temporary file.
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            try {
                mFilePhotoTaken = File.createTempFile(
                        "IMG_",  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );

                // Create the File where the photo should go
                // Continue only if the File was successfully created
                if (mFilePhotoTaken != null) {
                    mUriPhotoTaken = FileProvider.getUriForFile(this,
                            "com.github.ProjektGrupowy2018.fileprovider",
                            mFilePhotoTaken);
                    newIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUriPhotoTaken);

                    // Finally start camera activity
                    startActivityForResult(newIntent, REQUEST_TAKE_PHOTO);
                }
            }
            catch (IOException e) {
                mEditText.setText(e.getMessage());
            }
        }
    }

    // Called when image selection is done.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        Log.d("AnalyzeActivity", "onActivityResult");
        switch (requestCode)
        {
            case REQUEST_TAKE_PHOTO:
                if (resultCode == RESULT_OK)
                {
                    mEditText.setText("");

                    mImageUri = Uri.fromFile(mFilePhotoTaken);

                    mBitmap = ImageHelper.loadSizeLimitedBitmapFromUri(
                            mImageUri, getContentResolver());
                    if (mBitmap != null)
                    {
                        Log.d("AnalyzeActivity", "Image: " + mImageUri + " resized to " + mBitmap.getWidth()
                                + "x" + mBitmap.getHeight());
                        doRecognize();
                    }

                }
                break;
            default:
                break;
        }
    }

    public void doRecognize() {
        mEditText.setText("Analyzing...");

        try {
            new doRequest().execute();
        } catch (Exception e)
        {
            mEditText.setText("Error encountered. Exception is: " + e.toString());
        }
    }

    private String process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        OCR ocr;
        ocr = this.client.recognizeText(inputStream, LanguageCodes.AutoDetect, true);

        String result = gson.toJson(ocr);
        Log.d("result", result);

        return result;
    }

    private class doRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public doRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return process();
            } catch (Exception e) {
                this.e = e;    // Store error
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            if (e != null) {
                mEditText.setText("Error: " + e.getMessage());
                this.e = null;
            } else {
                Gson gson = new Gson();
                OCR r = gson.fromJson(data, OCR.class);

                String result = "";
                for (Region reg : r.regions) {
                    for (Line line : reg.lines) {
                        for (Word word : line.words) {
                            result += word.text + " ";
                        }
                        //result += "\n";
                    }
                    //result += "\n\n";
                }

                mEditText.setText(result);

                // Get instance of Vibrator from current Context
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                // Vibrate for 400 milliseconds
                v.vibrate(400);

                serialPort.write(result.getBytes());
            }
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }




}
