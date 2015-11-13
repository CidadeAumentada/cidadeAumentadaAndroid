package br.org.inovacidades.www.mapboxapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.metaio.cloud.plugin.util.MetaioCloudUtils;
import com.metaio.sdk.ARELInterpreterAndroidJava;
import com.metaio.sdk.ARViewActivity;
import com.metaio.sdk.MetaioDebug;
import com.metaio.sdk.jni.AnnotatedGeometriesGroupCallback;
import com.metaio.sdk.jni.EGEOMETRY_FOCUS_STATE;
import com.metaio.sdk.jni.IAnnotatedGeometriesGroup;
import com.metaio.sdk.jni.IGeometry;
import com.metaio.sdk.jni.IMetaioSDKCallback;
import com.metaio.sdk.jni.IRadar;
import com.metaio.sdk.jni.ImageStruct;
import com.metaio.sdk.jni.LLACoordinate;
import com.metaio.sdk.jni.Rotation;
import com.metaio.tools.io.AssetsManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;

public class ARActivity extends ARViewActivity {

    private IAnnotatedGeometriesGroup mAnnotatedGeometriesGroup;
    private MyAnnotatedGeometriesGroupCallback mAnnotatedGeometriesGroupCallback;

    private ArrayList<IGeometry> mGeometries;

    private IRadar mRadar;

    private Timer timer = new Timer();
    private Boolean allowRefresh = false;

    private static final String MARKERS_ARRAY_KEY = "Markers Array";
    private MyMarker[] markersArray;

    private ProgressDialog pDialog;

    ImageView ivRecursoImagem;

    @Override
    protected int getGUILayout() {
        return R.layout.activity_ar;
    }

    @Override
    protected IMetaioSDKCallback getMetaioSDKCallbackHandler() {
        return null;
    }

    @Override
    protected void loadContents() {
        mAnnotatedGeometriesGroup = metaioSDK.createAnnotatedGeometriesGroup();
        mAnnotatedGeometriesGroupCallback = new MyAnnotatedGeometriesGroupCallback();
        mAnnotatedGeometriesGroup.registerCallback(mAnnotatedGeometriesGroupCallback);

        metaioSDK.setLLAObjectRenderingLimits(2, 100);

        metaioSDK.setRendererClippingPlaneLimits(10, 220000);

        mGeometries = new ArrayList<>();

        if (markersArray != null) {
            float smallestDistance = Float.MAX_VALUE;

            for (MyMarker aMarker : markersArray) {
                LLACoordinate memoryPos = new LLACoordinate(aMarker.getLatitude(), aMarker.getLongitude(), 0, 0);
                float distance = (float) MetaioCloudUtils.getDistanceBetweenTwoCoordinates(memoryPos, mSensors.getLocation());

                if (distance <= 100) {
                    aMarker.setVisible(true);

                    IGeometry geometry = createPOIGeometry(memoryPos);
                    mAnnotatedGeometriesGroup.addGeometry(geometry, aMarker.getDescription());
                    mGeometries.add(geometry);
                }

                if (distance < smallestDistance) {
                    smallestDistance = distance;
                }
            }

            if (smallestDistance > 100) {
                Log.d("Response: ", "> Nenhuma memoria nas proximidades!");
                finish();
            }
        }

        mRadar = metaioSDK.createRadar();
        mRadar.setBackgroundTexture(AssetsManager.getAssetPathAsFile(getApplicationContext(), "assets1/radar.png"));
        mRadar.setObjectsDefaultTexture(AssetsManager.getAssetPathAsFile(getApplicationContext(), "assets1/yellow.png"));
        mRadar.setRelativeToScreen(IGeometry.ANCHOR_TL);

        for (IGeometry geometry : mGeometries) {
            mRadar.add(geometry);
        }

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                allowRefresh = true;
            }
        }, 5000, 5000);
    }

    private IGeometry createPOIGeometry(LLACoordinate lla) {
        final File path =
                AssetsManager.getAssetPathAsFile(getApplicationContext(),
                        "assets1/ponto.png");
        if (path != null) {
            IGeometry geometry = metaioSDK.createGeometryFromImage(path);
            geometry.setTranslationLLA(lla);
            geometry.setLLALimitsEnabled(true);
            geometry.setScale(60f);
            return geometry;
        } else {
            MetaioDebug.log(Log.ERROR, "Missing files for POI geometry");
            return null;
        }
    }

    @Override
    protected void onGeometryTouched(final IGeometry geometry) {
        MetaioDebug.log("Geometry selected: " + geometry);

        mSurfaceView.queueEvent(new Runnable() {
            @Override
            public void run() {
                mRadar.setObjectsDefaultTexture(AssetsManager.getAssetPathAsFile(getApplicationContext(), "assets1/yellow.png"));
                mRadar.setObjectTexture(geometry, AssetsManager.getAssetPathAsFile(getApplicationContext(), "assets1/red.png"));
                mAnnotatedGeometriesGroup.setSelectedGeometry(geometry);
            }
        });

        new ImageGetter().execute(geometry);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Bundle b = intent.getBundleExtra("BUNDLE");

        //noinspection unchecked
        ArrayList<MyMarker> myMarkers = (ArrayList<MyMarker>) b.getSerializable(MARKERS_ARRAY_KEY);

        if (myMarkers != null) {
            markersArray = new MyMarker[myMarkers.size()];
            myMarkers.toArray(markersArray);
        }

        final boolean result = metaioSDK.setTrackingConfiguration("GPS", false);
        MetaioDebug.log("Tracking data loaded: " + result);
    }

//    public void createImageGeometry(final File path, final IGeometry geometry) {
//
//        mSurfaceView.queueEvent(new Runnable() {
//            @Override
//            public void run() {
//                IGeometry mImagePlane = metaioSDK.createGeometryFromImage(path.getPath());
//
//                if (mImagePlane != null) {
//                    mImagePlane.setTranslationLLA(geometry.getTranslationLLA());
//                    mImagePlane.setLLALimitsEnabled(true);
//                    mImagePlane.setScale(150.0f);
//                    mImagePlane.setRotation(geometry.getRotation());
//                    mImagePlane.setVisible(true);
//                    geometry.setScale(0);
//                } else {
//                    MetaioDebug.log(Log.ERROR, "Error Loading Geometry: " + path);
//                }
//            }
//        });
//    }

    @Override
    protected void onDestroy() {
        if (mAnnotatedGeometriesGroup != null) {
            mAnnotatedGeometriesGroup.registerCallback(null);
        }

        if (mAnnotatedGeometriesGroupCallback != null) {
            mAnnotatedGeometriesGroupCallback.delete();
            mAnnotatedGeometriesGroupCallback = null;
        }

        super.onDestroy();
    }

    @Override
    public void onDrawFrame() {
        try {
            if (allowRefresh && markersArray != null) {
                float smallestDistance = Float.MAX_VALUE;

                for (IGeometry geometry : mGeometries) {
                    LLACoordinate pos = geometry.getTranslationLLA();
                    float distance = (float) MetaioCloudUtils.getDistanceBetweenTwoCoordinates(pos, mSensors.getLocation());

                    if (distance > 100) {
                        for (MyMarker marker : markersArray) {
                            if (mAnnotatedGeometriesGroup.getAnnotationForGeometry(geometry).toString().equals(marker.getDescription())) {
                                marker.setVisible(false);
                            }
                        }

                        mRadar.remove(geometry);
                        mAnnotatedGeometriesGroup.removeGeometry(geometry);
                        mGeometries.remove(geometry);
                    }

                    if (distance < smallestDistance) {
                        smallestDistance = distance;
                    }
                }

                for (MyMarker marker : markersArray) {
                    LLACoordinate pos = new LLACoordinate(marker.getLatitude(), marker.getLongitude(), 0, 0);
                    float distance = (float) MetaioCloudUtils.getDistanceBetweenTwoCoordinates(pos, mSensors.getLocation());

                    if (!marker.isVisible() && distance <= 100) {
                        marker.setVisible(true);

                        IGeometry geometry = createPOIGeometry(pos);
                        mAnnotatedGeometriesGroup.addGeometry(geometry, marker.getDescription());
                        mGeometries.add(geometry);
                        mRadar.add(geometry);
                    }

                    if (distance < smallestDistance) {
                        smallestDistance = distance;
                    }
                }

                if (smallestDistance > 100) {
                    Log.d("Response: ", "> Nenhuma memoria nas proximidades!");
                    finish();
                }

                allowRefresh = false;
            }

            final float heading = (float) Math.toRadians(mSensors.getHeading());

            Rotation rotation = new Rotation((float)(Math.PI/2.0), 0f, -heading);
            for (IGeometry geometry : mGeometries) {
                geometry.setRotation(rotation);
            }
            rotation.delete();
        } catch (Exception e) {
            Log.d("Response: ", "> Deu Erro!");
        }

        super.onDrawFrame();
    }

    final class MyAnnotatedGeometriesGroupCallback extends AnnotatedGeometriesGroupCallback {
        Bitmap mAnnotationBackground, mEmptyStarImage, mFullStarImage;
        int mAnnotationBackgroundIndex;
        ImageStruct texture;
        String[] textureHash = new String[1];
        TextPaint mPaint;
        Lock geometryLock;

        Bitmap inOutCacheBitmaps[] = new Bitmap[] {mAnnotationBackground, mEmptyStarImage, mFullStarImage};
        int inOutCachedAnnotationBackgroundIndex[] = new int[] {mAnnotationBackgroundIndex};

        public MyAnnotatedGeometriesGroupCallback() {
            mPaint = new TextPaint();
            mPaint.setFilterBitmap(true);
            mPaint.setAntiAlias(true);
        }

        @Override
        public IGeometry loadUpdatedAnnotation(IGeometry geometry, Object userData, IGeometry existingAnnotation) {
            if (userData == null) {
                return null;
            }

            if (existingAnnotation != null) {
                return existingAnnotation;
            }

            String title = (String)userData;
            LLACoordinate location = geometry.getTranslationLLA();
            float distance = (float) MetaioCloudUtils.getDistanceBetweenTwoCoordinates(location, mSensors.getLocation());
            Bitmap thumbnail = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);

            try {
                texture = ARELInterpreterAndroidJava.getAnnotationImageForPOI(title, title, distance, "5", thumbnail,
                        null,
                        metaioSDK.getRenderSize(), ARActivity.this,
                        mPaint, inOutCacheBitmaps, inOutCachedAnnotationBackgroundIndex, textureHash);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (thumbnail != null) {
                    thumbnail.recycle();
                }
            }

            mAnnotationBackground = inOutCacheBitmaps[0];
            mEmptyStarImage = inOutCacheBitmaps[1];
            mFullStarImage = inOutCacheBitmaps[2];
            mAnnotationBackgroundIndex = inOutCachedAnnotationBackgroundIndex[0];

            IGeometry resultGeometry = null;

            if (texture != null) {
                if (geometryLock != null) {
                    geometryLock.lock();
                }

                try {
                    resultGeometry = metaioSDK.createGeometryFromImage(textureHash[0], texture, true, false);
                } finally {
                    if (geometryLock != null) {
                        geometryLock.unlock();
                    }
                }
            }
            return resultGeometry;
        }

        @Override
        public void onFocusStateChanged(IGeometry geometry, Object userData, EGEOMETRY_FOCUS_STATE oldState,
                                        EGEOMETRY_FOCUS_STATE newState) {
            MetaioDebug.log("onFocusStateChanged for " + userData + ", " + oldState + "->" + newState);
        }
    }

    private class ImageGetter extends AsyncTask<IGeometry, Void, Void> {

        private Drawable drImagemRecurso;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            pDialog = new ProgressDialog(ARActivity.this);
            pDialog.setMessage("Carregando Imagem...");
            pDialog.setCancelable(false);
            pDialog.show();

            RelativeLayout layoutRecursos = (RelativeLayout) findViewById(R.id.visuRecursos);
            layoutRecursos.setBackgroundColor(Color.parseColor("#A0000000"));
        }

        @Override
        protected Void doInBackground(IGeometry... arg0) {
            try {
                URL pathUrl = new URL("http://cidadeaumentada.esy.es/siteTeste/uploads/imagens/1435096177517.jpg");
                String tDir = System.getProperty("java.io.tmpdir");
                String path = tDir + "tmp" + ".jpg";

                File imagePath = new File(path);
                imagePath.deleteOnExit();

                FileUtils.copyURLToFile(pathUrl, imagePath);

                drImagemRecurso = Drawable.createFromPath(path);
            } catch (IOException e) {
                Log.d("Response: ", "> Falhou em carregar imagem");
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            if (pDialog.isShowing()) {
                pDialog.dismiss();
            }

            ivRecursoImagem = (ImageView) findViewById(R.id.imagemRecurso);
            ivRecursoImagem.setImageDrawable(drImagemRecurso);
            ivRecursoImagem.setVisibility(View.VISIBLE);
        }
    }
}
