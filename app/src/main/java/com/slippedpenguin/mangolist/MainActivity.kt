package com.slippedpenguin.mangolist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.slippedpenguin.mangolist.ui.MangoNavRoot
import com.slippedpenguin.mangolist.ui.theme.MangoTheme

/*
 * Single-activity host — Compose handles all navigation. Edge-to-edge lets
 * status/nav bars draw over the app surface so the bottom NavigationBar
 * matches the JS prototype's "floats over content" feel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MangoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MangoNavRoot()
                }
            }
        }
    }
}
