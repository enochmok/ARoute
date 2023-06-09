package scm.finalYearProject.aroute

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import examples.java.common.helpers.FullScreenHelper
import examples.java.common.samplerender.SampleRender

class ARoute : AppCompatActivity() {
    companion object {
        private const val TAG = "ARoute"
    }

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var view: ARouteView
    lateinit var renderer: ARouteRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // Setup ARCore session lifecycle helper and configuration.
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        // If Session creation or Session.resume() fails, display a message and log detailed
        // information.
        arCoreSessionHelper.exceptionCallback =
                { exception ->
                    val message =
                            when (exception) {
                                is UnavailableUserDeclinedInstallationException ->
                                    "Please install Google Play Services for AR"
                                is UnavailableApkTooOldException -> "Please update ARCore"
                                is UnavailableSdkTooOldException -> "Please update this app"
                                is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                                is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                                else -> "Failed to create AR session: $exception"
                            }
                    Log.e(TAG, "ARCore threw an exception", exception)
                    view.snackbarHelper.showError(this, message)
                }

        // Configure session features.
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)

        // Set up the AROUTE AR renderer.
        renderer = ARouteRenderer(this)
        lifecycle.addObserver(renderer)

        // Set up AROUTE AR UI.
        view = ARouteView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)
        view.setImageViewButton()

        // Sets up an example renderer using our HelloGeoRenderer.
        SampleRender(view.surfaceView, renderer, assets)
    }

    // Configure the session, setting the desired options according to your usecase.
    fun configureSession(session: Session) {
        // TODO: Configure ARCore to use GeospatialMode.ENABLED.
        session.configure(
                session.config.apply {
                    //Enable Geospatial Mode
                    geospatialMode = Config.GeospatialMode.ENABLED
                }
        )
    }



    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(this, "Camera and location permissions are needed to run this application", Toast.LENGTH_LONG)
                    .show()
            if (!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                GeoPermissionsHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }
}