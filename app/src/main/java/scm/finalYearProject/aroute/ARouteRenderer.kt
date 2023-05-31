package scm.finalYearProject.aroute

import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.maps.android.PolyUtil
import examples.java.common.helpers.DisplayRotationHelper
import examples.java.common.helpers.TrackingStateHelper
import examples.java.common.samplerender.*
import examples.java.common.samplerender.arcore.BackgroundRenderer
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


class ARouteRenderer(val activity: ARoute) :
        SampleRender.Renderer, DefaultLifecycleObserver {
    //<editor-fold desc="ARCore initialization" defaultstate="collapsed">
    companion object {
        val TAG = "ARouteRenderer"

        private val Z_NEAR = 0.1f
        private val Z_FAR = 1000f
    }

    lateinit var backgroundRenderer: BackgroundRenderer
    lateinit var virtualSceneFramebuffer: Framebuffer
    var hasSetTextureNames = false

    // Virtual object (ARCore pawn)
    lateinit var virtualObjectMesh: Mesh
    lateinit var virtualObjectShader: Shader
    lateinit var virtualObjectTexture: Texture

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    val modelMatrix = FloatArray(16)
    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)
    val modelViewMatrix = FloatArray(16) // view x model

    val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

    val session
        get() = activity.arCoreSessionHelper.session

    val displayRotationHelper = DisplayRotationHelper(activity)
    val trackingStateHelper = TrackingStateHelper(activity)

    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        hasSetTextureNames = false
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    override fun onSurfaceCreated(render: SampleRender) {
        // Prepare the rendering objects.
        // This involves reading shaders and 3D model files, so may throw an IOException.
        try {
            backgroundRenderer = BackgroundRenderer(render)
            virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

            // Virtual object to render (Geospatial Marker)
            virtualObjectTexture =
                    Texture.createFromAsset(
                            render,
                            "models/dest_texture.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB
                    )

            virtualObjectMesh = Mesh.createFromAsset(render, "models/geospatial_marker.obj");
            virtualObjectShader =
                    Shader.createFromAssets(
                            render,
                            "shaders/ar_unlit_object.vert",
                            "shaders/ar_unlit_object.frag",
                            /*defines=*/ null)
                            .setTexture("u_Texture", virtualObjectTexture)

            backgroundRenderer.setUseDepthVisualization(render, false)
            backgroundRenderer.setUseOcclusion(render, false)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
            showError("Failed to read a required asset file: $e")
        }
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer.resize(width, height)
    }
    //</editor-fold>

    val anchorList = ArrayList<Anchor>()
    var destination = LatLng(0.0, 0.0)

    override fun onDrawFrame(render: SampleRender) {
        val session = session ?: return

        //<editor-fold desc="ARCore frame boilerplate" defaultstate="collapsed">
        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
            hasSetTextureNames = true
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session)

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        val frame =
                try {
                    session.update()
                } catch (e: CameraNotAvailableException) {
                    Log.e(TAG, "Camera not available during onDrawFrame", e)
                    showError("Camera not available. Try restarting the app.")
                    return
                }

        val camera = frame.camera

        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame)

        // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
        trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

        // -- Draw background
        if (frame.timestamp != 0L) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render)
        }

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }

        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0)

        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
        //</editor-fold>

        // TODO: Obtain Geospatial information and display it on the map.
        // TODO: Current User position information
        val earth = session.earth

        if (earth?.trackingState == TrackingState.TRACKING) {
            val cameraGeospatialPose = earth.cameraGeospatialPose
            activity.view.mapView?.updateMapPosition(
                    latitude = cameraGeospatialPose.latitude,
                    longitude = cameraGeospatialPose.longitude,
                    heading = cameraGeospatialPose.heading
            )
        }
        if (earth != null) {
            activity.view.updateStatusText(earth, earth.cameraGeospatialPose)
        }

        // Draw the placed anchor, if it exists.
        earthAnchor?.let {
            render.renderCompassAtAnchor(it)
        }

        for (step in stepsLatLngList)   {
            // Place the earth anchor at the same altitude as that of the camera to make it easier to view.
            // The rotation quaternion of the anchor in the East-Up-South (EUS) coordinate system.
            val qx = 0f
            val qy = 0f
            val qz = 0f
            val qw = 0f
            val anchor = earth?.createAnchor(step.latitude, step.longitude, earth.cameraGeospatialPose.altitude, qx, qy, qz, qw)
            if (anchor != null) {
                render.renderCompassAtAnchor(anchor)
                anchorList.add(anchor)
            }
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
    }

    var earthAnchor: Anchor? = null

    fun onMapClick(latLng: LatLng) {
        // TODO: place an anchor at the given position.
        val earth = session?.earth ?: return
        Log.d(TAG, "OnMapClick: $latLng")

        if (earth.trackingState != TrackingState.TRACKING) {
            return
        }
            earthAnchor?.detach()
            // Place the earth anchor at the same altitude as that of the camera to make it easier to view.
            val altitude = earth.cameraGeospatialPose.altitude - 1
            // The rotation quaternion of the anchor in the East-Up-South (EUS) coordinate system.
            val qx = 0f
            val qy = 0f
            val qz = 0f
            val qw = 1f
            earthAnchor =
                    earth.createAnchor(latLng.latitude, latLng.longitude, altitude, qx, qy, qz, qw)

            activity.view.mapView?.earthMarker?.apply {
                position = latLng
                isVisible = true
            }
    }

    val stepsLatLngList = ArrayList<LatLng>()

    var response: Response? = null

    fun getDirection(destinationPose: LatLng)  {
            destination = destinationPose

        val session = session ?: return

        val earth = session.earth
        val cameraGeospatialPose = earth!!.cameraGeospatialPose
        val latitude = cameraGeospatialPose.latitude
        val longitude = cameraGeospatialPose.longitude
        val dlatitude = destinationPose.latitude
        val dlongitude = destinationPose.longitude

        val thread = Thread {
            val okHttpClient = OkHttpClient.Builder()
                    .build()
            val request = Request.Builder()
                    .url("https://maps.googleapis.com/maps/api/directions/json?origin=${latitude},${longitude}&destination=${dlatitude},${dlongitude}&mode=walking&key=AIzaSyD7nL20gslNTJ3KXg8E7f4iuzMBhABdEnM")
                    .method("GET", null)
                    .build()
            response = okHttpClient.newCall(request).execute()

            val json = response?.body?.string()
            val jsonObject = JSONObject(json)
            val routes = jsonObject.getJSONArray("routes")
            val instructionsList = ArrayList<String>()
            for (i in 0 until routes.length()) {
                val legs = routes.getJSONObject(i).getJSONArray("legs")
                for (j in 0 until legs.length()) {
                    val steps = legs.getJSONObject(j).getJSONArray("steps")
                    Log.d(TAG, "API response" + steps.toString())
                    for (k in 0 until steps.length()) {
                        val polyline = steps.getJSONObject(k).getJSONObject("polyline")
                                .getString("points")
                        val startLocationLat = steps.getJSONObject(k).getJSONObject("start_location")
                                .getString("lat")
                        val startLocationLng = steps.getJSONObject(k).getJSONObject("start_location")
                                .getString("lng")
                        val htmlInstruction = steps.getJSONObject(k).getString("html_instructions")
                        // decode polyline and use it for drawing a route on a map
                        val decodedPath = PolyUtil.decode(polyline)
                        val latLngList = ArrayList<LatLng>()
                        instructionsList.add(htmlInstruction)

                        val latLng = LatLng(startLocationLat.toDouble(), startLocationLng.toDouble())

                        // adding each point in the polyline to a list
                        for (point in decodedPath) {
                            val lat = point.latitude
                            val lng = point.longitude
                            val latLng = LatLng(lat, lng)
                            latLngList.add(latLng)
                            stepsLatLngList.add(latLng)
                        }

                        activity.view.mapView?.createPolyline(latLngList)
                    }
                }
            }
            placeStepsAnchor(stepsLatLngList)
            activity.runOnUiThread {
                activity.view.updateInstructionText(HtmlCompat.fromHtml(instructionsList[0], HtmlCompat.FROM_HTML_MODE_LEGACY).toString())
            }
        }.start()
    }

    var stepsAnchor: Anchor? = null

    private fun placeStepsAnchor(stepsPoseList: ArrayList<LatLng>)  {
        val earth = session?.earth ?: return
        if (earth.trackingState != TrackingState.TRACKING)  {
            return
        }
        clearAnchor()
        // Place the earth anchor at the same altitude as that of the camera to make it easier to view.
        // The rotation quaternion of the anchor in the East-Up-South (EUS) coordinate system.
        val qx = 0f
        val qy = 0f
        val qz = 0f
        val qw = 0f

        for (step in stepsPoseList) {
            Log.d(TAG, "placedStepsAnchor" + step.toString())
            stepsAnchor = earth.createAnchor(step.latitude, step.longitude, earth.cameraGeospatialPose.altitude, qx, qy, qz, qw)
            activity.view.mapView?.earthMarker?.apply {
                activity.runOnUiThread  {
                    position = LatLng(step.latitude, step.longitude)
                    isVisible = true
                }
            }
        }
    }

    fun clearAnchor() {
        val iterator = anchorList.iterator()
        while (iterator.hasNext())  {
            val anchor = iterator.next()
            anchor.detach()
        }

    }

    private fun SampleRender.renderCompassAtAnchor(anchor: Anchor) {
        // Get the current pose of the Anchor in world space. The Anchor pose is updated
        // during calls to session.update() as ARCore refines its estimate of the world.
        anchor.pose.toMatrix(modelMatrix, 0)

        // Calculate model/view/projection matrices
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        // Update shader properties and draw
        virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
        draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
    }

    private fun showError(errorMessage: String) =
            activity.view.snackbarHelper.showError(activity, errorMessage)
}