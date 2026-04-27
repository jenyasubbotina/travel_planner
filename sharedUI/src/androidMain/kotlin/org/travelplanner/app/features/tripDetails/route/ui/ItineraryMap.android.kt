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
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import org.travelplanner.app.domain.Event

@Composable
actual fun ItineraryMap(
    events: List<Event>,
    selectedEventId: Long?,
    onSelect: (Long) -> Unit,
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
    val placemarks = remember { mutableMapOf<Long, PlacemarkMapObject>() }

    val currentOnSelect by rememberUpdatedState(onSelect)
    val tapListener =
        remember {
            MapObjectTapListener { mapObject: MapObject, _ ->
                (mapObject.userData as? Long)?.let(currentOnSelect)
                true
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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(events, selectedEventId) {
        val live =
            events
                .filter { it.latitude != 0.0 && it.longitude != 0.0 }
                .associateBy { it.id }

        placemarks.keys.toList().forEach { id ->
            if (id !in live) {
                placemarks.remove(id)?.let { collection.remove(it) }
            }
        }

        live.forEach { (id, ev) ->
            val isSelected = id == selectedEventId
            val marker =
                buildLabeledMarkerBitmap(
                    context = context,
                    title = ev.title,
                    fillColor = if (isSelected) 0xFFFFB300.toInt() else 0xFF155DFC.toInt(),
                    radiusDp = if (isSelected) 13 else 11,
                    strokeDp = if (isSelected) 3 else 2,
                )
            val image = ImageProvider.fromBitmap(marker.bitmap)
            val style = IconStyle().setAnchor(PointF(marker.anchorX, marker.anchorY))
            val point = Point(ev.latitude, ev.longitude)

            val existing = placemarks[id]
            if (existing == null) {
                val pm = collection.addPlacemark()
                pm.geometry = point
                pm.setIcon(image, style)
                pm.userData = id
                pm.addTapListener(tapListener)
                placemarks[id] = pm
            } else {
                existing.geometry = point
                existing.setIcon(image, style)
            }
        }
    }

    LaunchedEffect(selectedEventId) {
        val target = events.find { it.id == selectedEventId } ?: return@LaunchedEffect
        if (target.latitude == 0.0 && target.longitude == 0.0) return@LaunchedEffect
        mapView.mapWindow.map.move(
            CameraPosition(Point(target.latitude, target.longitude), 14f, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.4f),
            null,
        )
    }

    LaunchedEffect(Unit) {
        if (selectedEventId == null) {
            val anchor =
                events.firstOrNull { it.latitude != 0.0 && it.longitude != 0.0 }
            val initialPoint =
                if (anchor != null) {
                    Point(anchor.latitude, anchor.longitude)
                } else {
                    Point(50.0, 10.0)
                }
            mapView.mapWindow.map.move(
                CameraPosition(initialPoint, if (anchor != null) 13f else 5f, 0f, 0f),
            )
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
