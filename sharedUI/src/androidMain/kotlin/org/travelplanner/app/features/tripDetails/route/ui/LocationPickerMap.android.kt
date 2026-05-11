package org.travelplanner.app.features.tripDetails.route.ui

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider

@Composable
actual fun LocationPickerMapCanvas(
    initialLatitude: Double?,
    initialLongitude: Double?,
    selectedLat: Double,
    selectedLon: Double,
    cameraTarget: Pair<Double, Double>?,
    onTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val collection =
        remember {
            mapView.mapWindow.map.mapObjects
                .addCollection()
        }

    val markerBitmap =
        remember {
            buildSimpleCircleBitmap(
                context = context,
                fillColor = 0xFFE53935.toInt(),
                radiusDp = 12,
                strokeDp = 3,
            )
        }
    val markerImage = remember { ImageProvider.fromBitmap(markerBitmap.bitmap) }
    val markerStyle =
        remember { IconStyle().setAnchor(PointF(markerBitmap.anchorX, markerBitmap.anchorY)) }

    val placemark =
        remember {
            collection.addPlacemark().apply {
                geometry = Point(selectedLat, selectedLon)
                setIcon(markerImage, markerStyle)
            }
        }

    val currentOnTap by rememberUpdatedState(onTap)
    val inputListener =
        remember {
            object : InputListener {
                override fun onMapTap(
                    map: Map,
                    point: Point,
                ) {
                    currentOnTap(point.latitude, point.longitude)
                }

                override fun onMapLongTap(
                    map: Map,
                    point: Point,
                ) = Unit
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        MapKitFactory.getInstance().onStart()
                        mapView.onStart()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        mapView.onStop()
                        MapKitFactory.getInstance().onStop()
                    }

                    else -> {
                        Unit
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.mapWindow.map.addInputListener(inputListener)
        onDispose {
            mapView.mapWindow.map.removeInputListener(inputListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(selectedLat, selectedLon) {
        placemark.geometry = Point(selectedLat, selectedLon)
    }

    LaunchedEffect(Unit) {
        val zoom = if (initialLatitude != null && initialLongitude != null) 14f else 4f
        mapView.mapWindow.map.move(
            CameraPosition(Point(selectedLat, selectedLon), zoom, 0f, 0f),
        )
    }

    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        mapView.mapWindow.map.move(
            CameraPosition(Point(target.first, target.second), 15f, 0f, 0f),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
