package com.praksa.endrina.barcodescener;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.MultiDetector;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.gms.vision.face.FaceDetector;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Activity for the multi-tracker app.  This app detects faces and barcodes with the rear facing
 * camera, and draws overlay graphics to indicate the position, size, and ID of each face and
 * barcode.
 */
public final class MultiTrackerActivity extends AppCompatActivity {
    private static final String TAG = "MultiTracker";

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    /**
     * Initializes the UI and creates the detector pipeline.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }



    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {


        Context context = getApplicationContext();

        // A face detector is created to track faces.  An associated multi-processor instance
        // is set to receive the face detection results, track the faces, and maintain graphics for
        // each face on screen.  The factory is used by the multi-processor to create a separate
        // tracker instance for each face.
        FaceDetector faceDetector = new FaceDetector.Builder(context).build();
        FaceTrackerFactory faceFactory = new FaceTrackerFactory(mGraphicOverlay);
        faceDetector.setProcessor(
                new MultiProcessor.Builder<>(faceFactory).build());

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory();
        barcodeDetector.setProcessor(
                new MultiProcessor.Builder<>(barcodeFactory).build());


        // A multi-detector groups the two detectors together as one detector.  All images received
        // by this detector from the camera will be sent to each of the underlying detectors, which
        // will each do face and barcode detection, respectively.  The detection results from each
        // are then sent to associated tracker instances which maintain per-item graphics on the
        // screen.
        MultiDetector multiDetector = new MultiDetector.Builder()
                .add(faceDetector)
                .add(barcodeDetector)
                .build();

        if (!multiDetector.isOperational()) {
            // Note: The first time that an app using the barcode or face API is installed on a
            // device, GMS will download a native libraries to the device in order to do detection.
            // Usually this completes before the app is run for the first time.  But if that
            // download has not yet completed, then the above call will not detect any barcodes
            // and/or faces.
            //
            // isOperational() can be used to check if the required native libraries are currently
            // available.  The detectors will automatically become operational once the library
            // downloads complete on device.
            Log.w(TAG, "Detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        // Creates and starts the camera.  Note that this uses a higher resolution in comparison
        // to other detection examples to enable the barcode detector to detect small barcodes
        // at long distances.
        mCameraSource = new CameraSource.Builder(getApplicationContext(), multiDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f)
                .build();
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }



    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }


    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Multitracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }
    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
            class BarcodeTrackerFactory implements MultiProcessor.Factory<Barcode> {
                private GraphicOverlay mGraphicOverlay;
                public String barkod;

                BarcodeTrackerFactory(GraphicOverlay graphicOverlay) {
                    mGraphicOverlay = graphicOverlay;
                }

                @Override
                public Tracker<Barcode> create(Barcode barcode) {
                    BarcodeGraphic graphic = new BarcodeGraphic(mGraphicOverlay);
                    barkod = barcode.rawValue;
                    return new GraphicTracker<>(mGraphicOverlay, graphic);
                }


            }



/**
 * Graphic instance for rendering barcode position, size, and ID within an associated graphic
 * overlay view.
 */
            class BarcodeGraphic extends TrackedGraphic<Barcode> {

                private final int COLOR_CHOICES[] = {
                        Color.BLUE,
                        Color.CYAN,
                        Color.GREEN
                };
                private int mCurrentColorIndex = 0;

                private Paint mRectPaint;
                private Paint mTextPaint;
                private volatile Barcode mBarcode;

                BarcodeGraphic(GraphicOverlay overlay) {
                    super(overlay);

                    mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
                    final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

                    mRectPaint = new Paint();
                    mRectPaint.setColor(selectedColor);
                    mRectPaint.setStyle(Paint.Style.STROKE);
                    mRectPaint.setStrokeWidth(4.0f);

                    mTextPaint = new Paint();
                    mTextPaint.setColor(selectedColor);
                    mTextPaint.setTextSize(36.0f);
                }

                /**
                 * Updates the barcode instance from the detection of the most recent frame.  Invalidates the
                 * relevant portions of the overlay to trigger a redraw.
                 */
                void updateItem(Barcode barcode) {
                    mBarcode = barcode;

                    postInvalidate();
                }

                /**
                 * Draws the barcode annotations for position, size, and raw value on the supplied canvas.
                 */
                @Override
                public void draw(Canvas canvas) {
                    Barcode barcode = mBarcode;
                    if (barcode == null) {
                        return;
                    }


                    // Draws the bounding box around the barcode.
                    RectF rect = new RectF(barcode.getBoundingBox());
                    rect.left = translateX(rect.left);
                    rect.top = translateY(rect.top);
                    rect.right = translateX(rect.right);
                    rect.bottom = translateY(rect.bottom);
                    canvas.drawRect(rect, mRectPaint);


                    // Draws a label at the bottom of the barcode indicate the barcode value that was detected.
                    canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);



                }


            }


        }

    }




    /**
     * Graphic instance for rendering barcode position, size, and ID within an associated graphic
     * overlay view.
     */
    class BarcodeGraphic extends TrackedGraphic<Barcode> {

        private  final int COLOR_CHOICES[] = {
                Color.BLUE,
                Color.CYAN,
                Color.GREEN
        };
        private  int mCurrentColorIndex = 0;

        private Paint mRectPaint;
        private Paint mTextPaint;
        private volatile Barcode mBarcode;

        BarcodeGraphic(GraphicOverlay overlay) {
            super(overlay);

            mCurrentColorIndex = (mCurrentColorIndex + 1) % COLOR_CHOICES.length;
            final int selectedColor = COLOR_CHOICES[mCurrentColorIndex];

            mRectPaint = new Paint();
            mRectPaint.setColor(selectedColor);
            mRectPaint.setStyle(Paint.Style.STROKE);
            mRectPaint.setStrokeWidth(4.0f);

            mTextPaint = new Paint();
            mTextPaint.setColor(selectedColor);
            mTextPaint.setTextSize(36.0f);
        }

        /**
         * Updates the barcode instance from the detection of the most recent frame.  Invalidates the
         * relevant portions of the overlay to trigger a redraw.
         */
        void updateItem(Barcode barcode) {
            mBarcode = barcode;

            postInvalidate();
        }

        /**
         * Draws the barcode annotations for position, size, and raw value on the supplied canvas.
         */
        @Override
        public void draw(Canvas canvas) {
            Barcode barcode = mBarcode;
            if (barcode == null) {
                return;
            }


            // Draws the bounding box around the barcode.
            RectF rect = new RectF(barcode.getBoundingBox());
            rect.left = translateX(rect.left);
            rect.top = translateY(rect.top);
            rect.right = translateX(rect.right);
            rect.bottom = translateY(rect.bottom);
            canvas.drawRect(rect, mRectPaint);


            // Draws a label at the bottom of the barcode indicate the barcode value that was detected.
            canvas.drawText(barcode.rawValue, rect.left, rect.bottom, mTextPaint);



        }


    }

    class BarcodeTrackerFactory implements MultiProcessor.Factory<Barcode> {

        @Override
        public Tracker<Barcode> create(final Barcode barcode) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                   Toast.makeText(getApplicationContext(), barcode.rawValue, Toast.LENGTH_SHORT).show();
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    new ServerPost().execute(barcode.rawValue, android.os.Build.MODEL, date);

                }
            });
            return new MyBarcodeTracker();
        }
    }

    class MyBarcodeTracker extends Tracker<Barcode> {
        @Override
        public void onUpdate(Detector.Detections<Barcode> detectionResults, Barcode barcode) {
            String storage = "storage";
            SharedPreferences sharedPreferences = getSharedPreferences(storage, Context.MODE_PRIVATE);
            //Retrieve the values
            Set<String> set = sharedPreferences.getStringSet("lista", null);
            if (set == null) {
                set = new HashSet<String>();
            }
            set.add(barcode.rawValue);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putStringSet("lista", set);
            editor.commit();


        }
    }
    private class ServerPost extends AsyncTask<String, String, String> {
        private ProgressDialog dialog = new ProgressDialog(MultiTrackerActivity.this);


        @Override
        protected String doInBackground(String... params) {

            try {
                URL url = new URL("http://192.168.0.72:8000/barkod?barkod=" + params[0] +
                        "&uredaj=" + params[1]
                        + "&datum=" + params[2]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.connect();



                InputStream in = new BufferedInputStream(conn.getInputStream());
                JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
                try {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        String value = reader.nextString();
                        if (name.equals("status")) {
                            return value;
                        }




                    }
                } finally {
                    reader.close();
                }


            } catch (MalformedURLException error) {
                return "NOT";
            } catch (SocketTimeoutException error) {
                return "NOT";
            } catch (IOException error) {
                return "NOT";
            }
            return "NOT";


        }


        @Override
        protected void onPostExecute(String result) {
            dialog.dismiss();

            Toast.makeText(getApplicationContext(),result, Toast.LENGTH_LONG).show();



        }


        @Override
        protected void onPreExecute() {
            dialog.setMessage("Molimo Pričekajte");
            dialog.show();
        }


        @Override
        protected void onProgressUpdate(String... text) {

        }


    }
}