package com.andrives.geosnap_cam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.andrives.geosnap_cam.ui.component.UpdateBanner
import com.andrives.geosnap_cam.ui.navigation.GeoSnapNavGraph
import com.andrives.geosnap_cam.ui.theme.GeoSnapTheme
import com.andrives.geosnap_cam.util.InAppUpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var inAppUpdateManager: InAppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        inAppUpdateManager.checkForUpdate()

        enableEdgeToEdge()
        setContent {
            GeoSnapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GeoSnapNavGraph()
                        
                        UpdateBanner(
                            inAppUpdateManager = inAppUpdateManager,
                            onStartUpdate = {
                                inAppUpdateManager.startUpdateFlow(this@MainActivity)
                            },
                            onCompleteUpdate = {
                                inAppUpdateManager.completeUpdate()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdateManager.onDestroy()
    }
}
