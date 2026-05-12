package com.yueming.baby.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("悦萌 YueMing", style = MaterialTheme.typography.headlineMedium)
        Text("宝宝成长记录 · 原生 Android 版", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Stats cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("16", style = MaterialTheme.typography.headlineLarge)
                    Text("月龄", style = MaterialTheme.typography.labelSmall)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("15", style = MaterialTheme.typography.headlineLarge)
                    Text("记录", style = MaterialTheme.typography.labelSmall)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("8", style = MaterialTheme.typography.headlineLarge)
                    Text("照片", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("原生 Jetpack Compose + Material3", style = MaterialTheme.typography.titleMedium)
                Text("数据仍在逐步从 Web 版迁移中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TimelineScreen() {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text("时间线 - 迁移中", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun PhotosScreen() {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text("照片墙 - 迁移中", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AIScreen() {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI 助手 - 迁移中", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Text("设置 - 迁移中", style = MaterialTheme.typography.titleMedium)
    }
}
