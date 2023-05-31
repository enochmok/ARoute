package scm.finalYearProject.aroute

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.annotation.ColorInt
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

class MapView(val activity: ARoute, val googleMap: GoogleMap) {
    private val CAMERA_MARKER_COLOR: Int = Color.argb(255, 0, 255, 0)
    private val SEARCH_MARKER_COLOR: Int = Color.argb(255, 255, 0, 0)
    private val EARTH_MARKER_COLOR: Int = Color.argb(255, 125, 125, 125)

    var setInitialCameraPosition = false
    val cameraMarker = createMarker(CAMERA_MARKER_COLOR)
    val searchMarker = createMapPin(SEARCH_MARKER_COLOR)
    var cameraIdle = true

    val earthMarker = createMarker(EARTH_MARKER_COLOR)
    val polylines = ArrayList<Polyline>()

    init {
        googleMap.uiSettings.apply {
            isMapToolbarEnabled = false
            isIndoorLevelPickerEnabled = false
            isZoomControlsEnabled = false
            isTiltGesturesEnabled = false
            isScrollGesturesEnabled = true
        }

        googleMap.setOnMarkerClickListener { unused -> false }

        // Add listeners to keep track of when the GoogleMap camera is moving.
        googleMap.setOnCameraMoveListener { cameraIdle = false }
        googleMap.setOnCameraIdleListener { cameraIdle = true }


    }

    fun makeSearchLocationMarker(latitude: Double, longitude: Double)   {
        val position = LatLng(latitude, longitude)
        activity.runOnUiThread  {
            searchMarker.isVisible = true
            searchMarker.position = position
        }
    }

    fun createPolyline(latLngList: ArrayList<LatLng>) {
        val polylineOptions =  PolylineOptions()
                .addAll(latLngList)
                .color(Color.RED)
                .width(5f)

        activity.runOnUiThread {
                polylines.add(googleMap.addPolyline(polylineOptions))
        }
    }

    fun clearPolyline() {
        for (line in polylines) {
            line.remove()
        }
        polylines.clear()
    }

    fun updateMapPosition(latitude: Double, longitude: Double, heading: Double) {
        val position = LatLng(latitude, longitude)
        activity.runOnUiThread {
            // If the map is already in the process of a camera update, then don't move it.
            if (!cameraIdle) {
                return@runOnUiThread
            }
            cameraMarker.isVisible = true
            cameraMarker.position = position
            cameraMarker.rotation = heading.toFloat()



            val cameraPositionBuilder: CameraPosition.Builder = if (!setInitialCameraPosition) {
                // Set the camera position with an initial default zoom level.
                setInitialCameraPosition = true
                CameraPosition.Builder().zoom(20f).target(position)
            } else {
                // Set the camera position and keep the same zoom level.
                CameraPosition.Builder()
                        .zoom(googleMap.cameraPosition.zoom)
                        .target(position)
            }
            googleMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(cameraPositionBuilder.build()))
        }
    }

    /** Creates and adds a 2D anchor marker on the 2D map view.  */
    private fun createMarker(
            color: Int,
    ): Marker {
        val markersOptions = MarkerOptions()
                .position(LatLng(0.0,0.0))
                .draggable(true)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .visible(false)
                .icon(BitmapDescriptorFactory.fromBitmap(createColoredMarkerBitmap(color)))
        return googleMap.addMarker(markersOptions)!!
    }

    private fun createMapPin(color: Int): Marker {
        val markersOptions = MarkerOptions()
                .title("Searched Position")
                .position(LatLng(0.0, 0.0))
        return googleMap.addMarker(markersOptions)!!
    }

    private fun createColoredMarkerBitmap(@ColorInt color: Int): Bitmap {
        val opt = BitmapFactory.Options()
        opt.inMutable = true
        opt.inScaled = false
        val navigationIcon =
                BitmapFactory.decodeResource(activity.resources, R.drawable.ic_navigation_white_48dp, opt)
        val p = Paint()
        p.colorFilter = LightingColorFilter(color,  /* add= */1)
        val canvas = Canvas(navigationIcon)
        canvas.drawBitmap(navigationIcon,  /* left= */0f,  /* top= */0f, p)
        return navigationIcon
    }


}