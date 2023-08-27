package com.example.myapplication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.model.KernelConfig
import com.example.myapplication.model.PathConfig
import com.example.myapplication.model.ServerConfig
import com.example.myapplication.model.VMCollection
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.ui.view.Myapp
import com.example.myapplication.util.AuthChecker
import com.example.myapplication.util.DocumentsUtils
import com.example.myapplication.util.SPUtil
import com.example.myapplication.util.StorageUtils
import com.example.myapplication.viewmodel.*
import com.example.myapplication.viewmodel.connectserver.ConnectServerViewModel
import com.example.myapplication.viewmodel.filemanager.FileManagerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.jahnen.libaums.core.UsbMassStorageDevice
import java.io.File


/**
 * [api](https://github.com/SocialSisterYi/bilibili-API-collect/tree/master/login/login_action)
 */

class MainActivity : ComponentActivity() {

    private val connectKernelViewModel: ConnectKernelViewModel by viewModels()
    private val connectServerViewModel: ConnectServerViewModel by viewModels()
    private val videoViewModel: VideoViewModel by viewModels()
    private val nasViewModel: NasViewModel by viewModels()
    private val fileViewModel: FileManagerViewModel by viewModels()
    private val shareViewModel: ShareViewModel by viewModels()

    private val vmCollection by lazy {
        VMCollection(
            connectKernelViewModel,
            connectServerViewModel,
            videoViewModel,
            nasViewModel,
            fileViewModel,
            shareViewModel
        )
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Config.pathConfig.init(this)
        Config.kernelConfig.init(this)

        lifecycleScope.launch {
            vmCollection.shareViewModel.uiEvent.collect {
                when (it) {
                    is ShareViewModel.Event.NeedActivity -> {
                        it.block(this@MainActivity)
                    }
                    is ShareViewModel.Event.ToastEvent -> {
                        vmCollection.shareViewModel.snackbarHostState.showSnackbar(it.text.toString())
                        it.block()
                    }
                }
            }
        }
        vmCollection.videoVM.initDependency(VideoViewModel.Dependency(
            SPUtil.getString(this, "SESSDATA").nullCheck("get cookieSessionData", true) ?: ""
        ))
        vmCollection.videoVM.init()

        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // ScreenSetup(activityVar)
                    Myapp()
                }
            }
        }
        AuthChecker.checkWriteExternalStorage(this)

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = UsbMassStorageDevice.getMassStorageDevices(this)





        Log.d("swithun-xxxx", "devices: ${devices.size}")
        for (device in devices) {

            val permissionIntent = PendingIntent.getBroadcast(
                this, 0, Intent(
                    ACTION_USB_PERMISSION
                ), PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device.usbDevice, permissionIntent)
            usbManager.hasPermission(device.usbDevice).nullCheck("usb haspermission", true)

            try {
                SwithunLog.d("usb 1")
                val init = device.init()
                SwithunLog.d("usb 2 : init : $init")
                SwithunLog.d("usb device.partitions: ${device?.partitions?.size}")
                val currentFs = device.partitions?.getOrNull(0)?.fileSystem
                if (currentFs == null) {
                    SwithunLog.d("usb currentFs null")
                    return
                }

                vmCollection.fileVM.initUsbDevices(currentFs)
                vmCollection.nasVM.initUstDevices(currentFs)


                break

            } catch (e: java.lang.Exception) {
                SwithunLog.d("usb exception: $e")
            }
        }

        val path = StorageUtils.getUsbDir(this).nullCheck("usb new path", true)


        path?.let { path ->

            lifecycleScope.launch {
                showOpenDocumentTree(path)
                delay(10000)

                val files = File(path).listFiles().nullCheck("usb new list files", true)
                files?.forEach {
                    SwithunLog.d("usb file: ${it.name}")
                }
            }

        }



    }

    private fun showOpenDocumentTree(rootPath: String) {
        var intent: Intent? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = getSystemService(StorageManager::class.java)
            val volume = sm.getStorageVolume(File(rootPath))
            if (volume != null) {
                intent = volume.createAccessIntent(null)
            }
        }
        if (intent == null) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
        Log.d("MainActivity", "startActivityForResult...")
        startActivityForResult(intent, DocumentsUtils.OPEN_DOCUMENT_TREE_CODE)
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.myapp.USB_PERMISSION"
    }
}

object Config {
    val kernelConfig: KernelConfig = KernelConfig
    val pathConfig: PathConfig = PathConfig
    val serverConfig: ServerConfig = ServerConfig
}