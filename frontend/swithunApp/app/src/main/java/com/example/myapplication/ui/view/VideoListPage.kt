package com.example.myapplication.ui.view

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.model.SectionItem
import com.example.myapplication.viewmodel.VideoViewModel

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun VideoListPage(
    videoVM: VideoViewModel = viewModel(),
) {
    Row {
        ConanVideoView()
        QRCode(videoVM)
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ConanVideoView(
    videoViewModel: VideoViewModel = viewModel(),
) {
    Row {
        Column {
            Button(onClick = {
                if (videoViewModel.uiState.player.isPlaying) {
                    videoViewModel.uiState.player.pause()
                } else {
                    videoViewModel.uiState.player.start()
                }
            }) {
                Text(text = "stop")
            }
            Text(text = videoViewModel.uiState.currentProcess.toString())
        }

        LazyColumn(
            modifier = Modifier
                .background(Color(R.color.purple_200))
                .width(Dp(100f))
        ) {
            items(videoViewModel.uiState.itemList) { sectionItem: SectionItem ->
                Button(onClick = {
                    videoViewModel.reduce(VideoViewModel.Action.CyclePlayEpisode(sectionItem.id))
                }) {
                    Text(text = "${sectionItem.shortTitle}: ${sectionItem.longTitle}")
                }
            }
        }
    }
}

@Composable
fun QRCode(
    videoVM: VideoViewModel,
) {
    Column(verticalArrangement = Arrangement.Top) {
        Text(text = videoVM.uiState.loginStatus)
        Image(painter = BitmapPainter(videoVM.uiState.qrCodeImage), contentDescription = "qrCode")
    }
}