package com.example.myapplication

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.contextaware.ContextAware
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewModelScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.util.HeaderParams
import com.example.myapplication.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * [api](https://github.com/SocialSisterYi/bilibili-API-collect/tree/master/login/login_action)
 */

private var mySurfaceView: SurfaceView? = null
private var activity: Activity? = null

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this

        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    ScreenSetup(
                        wordsViewModel = WordsViewModel(),
                        videoViewModel = VideoViewModel { this }
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ScreenSetup(
    wordsViewModel: WordsViewModel,
    videoViewModel: VideoViewModel,
) {
    Row {
        VideoScreen(
            videoViewModel,
        )
        WordsScreen(
            wordsResult = wordsViewModel.wordsResult,
        ) { wordsViewModel.sendMessage(it) }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun VideoScreen(
    videoViewModel: VideoViewModel,
) {
    Column {
        QRCode(videoViewModel)
        VideoView(videoViewModel)
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun VideoView(
    videoViewModel: VideoViewModel
) {
    val conanUrl by remember { mutableStateOf("") }

    val context = LocalContext.current

    val onGetConanUrl = { conanUrl: String ->
        mySurfaceView?.let { surfaceView ->
            videoViewModel.player.let { player ->
                val ua = WebSettings.getDefaultUserAgent(context)
                SwithunLog.d(ua)

                val headerParams = HeaderParams().apply {
                    setBilibiliReferer()
                    // params["Referer"] = "https://www.bilibili.com"
//                    params["Origin"] =
//                        "https://www.bilibili.com/bangumi/play/ep323843?from_spmid=666.25.player.continue&from_outer_spmid=333.337.0.0"
//                    params["User-Agent"] =
//                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36 Edg/108.0.1462.54"
//                    params["User-Agent"] = ua
//                    params["User-Agent"] = "Mozilla/5.0 BiliDroid/7.5.0 (bbcallen@gmail.com)"
//                    params["User-Agent"] =
//                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36 Edg/92.0.902.62"
//                    params["User-Agent"] =
//                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36 Edg/108.0.1462.54"
//                    setBilibiliCookie(activity)
//                    params["User-Agent"] = "Bilibili Freedoooooom/MarkII"
                }


                player.reset()
                player.setDataSource(conanUrl, headerParams.params)
                player.setSurface(surfaceView.holder.surface)
                player.prepareAsync()
                player.start()
            }
        }
    }

    Column {
        Button(onClick = {
            videoViewModel.viewModelScope.launch(Dispatchers.IO) {
                videoViewModel.getConan()?.let { newConanUrl->
                    onGetConanUrl(newConanUrl)
                }
            }
        }
        ) {
            Text(text = "get conna")
        }
        IjkPlayer(player = videoViewModel.player)
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun IjkPlayer(player: IjkMediaPlayer) {
    // https://juejin.cn/post/7034363130121551903
    AndroidView(factory = { context ->
        val surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val temp = surfaceView.layoutParams
//                temp.height = ViewGroup.LayoutParams.WRAP_CONTENT
//                temp.width = ViewGroup.LayoutParams.WRAP_CONTENT
//
                temp.height = 600
                temp.width = 900
                surfaceView.layoutParams = temp

//                player.dataSource =
//                    "https://vfx.mtime.cn/Video/2019/03/09/mp4/190309153658147087.mp4"
//                player.setSurface(surfaceView.holder.surface)
//                player.prepareAsync()
//                player.start()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }

        })

        mySurfaceView = surfaceView

        surfaceView
    }, update = { surfaceView ->
        SwithunLog.d("surfaceView update")
    })
}


@Composable
fun QRCode(
    videoVM: VideoViewModel,
) {
    Column(verticalArrangement = Arrangement.Top) {
        Text(text = videoVM.loginStatus)
        Image(painter = BitmapPainter(videoVM.qrCodeImage), contentDescription = "qrCode")
    }
}

@Composable
fun WordsScreen(
    wordsResult: WordsResult,
    sendMessage: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        var textState by remember { mutableStateOf("") }
        val onTextChange = { text: String ->
            textState = text
        }

        OutlinedTextField(
            value = textState,
            onValueChange = { onTextChange(it) },
            singleLine = true,
            label = { Text(text = "Enter message") },
            modifier = Modifier.padding(10.dp),
            textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "单词：")
            Text(text = wordsResult.word)
        }
        val bullet = "\u2022"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "解释：")
            Text(text = buildAnnotatedString {
                wordsResult.explains.forEach {
                    withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 12.sp))) {
                        append(bullet)
                        append("\t\t")
                        append(it)
                    }
                }
            })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "翻译：")
            Text(text = wordsResult.translation)
        }

        Button(onClick = { sendMessage(textState) }) {
            Text(text = "Send Message")
        }
    }
}