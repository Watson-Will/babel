package com.example.myapplication.viewmodel

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.example.myapplication.Config
import com.example.myapplication.model.VMCollection
import com.example.myapplication.SwithunLog
import com.example.myapplication.framework.BaseViewModel
import com.example.myapplication.model.MessageTextDTO
import com.example.myapplication.util.SPUtil
import com.example.myapplication.util.SystemUtil
import com.swithun.liu.ServerSDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class NasViewModel :
    BaseViewModel<
            NasViewModel.Action,
            NasViewModel.UIState,
            NasViewModel.MutableUIState
            >() {

    // https://juejin.cn/post/6844903551408291848
    // https://github.com/koush/AndroidAsync
    private var vmCollection: VMCollection? = null
    private var uploadFileRootDir: String by mutableStateOf("")
    private var hasStartKernel = false

    override fun getInitialUIState(): MutableUIState {
        return MutableUIState()
    }

    abstract class UIState {
        abstract var getAllServerBtnText: String
        abstract var allServersInLan: List<String>
        abstract var lastTimeConnectServerIp: String
        abstract var startMeAsServerBtnText: String
    }

    class MutableUIState : UIState() {
        override var getAllServerBtnText: String by mutableStateOf("搜寻其他可用server")
        override var allServersInLan: List<String> by mutableStateOf(emptyList())
        override var lastTimeConnectServerIp: String by mutableStateOf("")
        override var startMeAsServerBtnText: String by mutableStateOf("启动server：未启动")
    }

    sealed class Action : BaseViewModel.Action() {
        object StartMeAsServer : Action()
        object ConnectMyServer : Action()
        object SearchAllServer : Action()
        class ChangeLastTimeConnectServer(val serverIP: String) : Action()
        class DownloadTransferFile(
            val text: String, val contentType: MessageTextDTO.ContentType, val context: Context,
        ) : Action()

        class ChooseUploadFileRootDir(val uploadPath: String) : Action()
    }

    sealed class Event : BaseViewModel.Event() {
        class NeedActivity(val block: (activity: Activity) -> Unit) : Event()
    }

    fun init(vmCollection: VMCollection) {
        this.vmCollection = vmCollection
        vmCollection.shareViewModel.reduce(ShareViewModel.Action.NeedActivity { activity ->
            SPUtil.ServerSetting.getLastTimeConnectServer(activity)?.let {
                SwithunLog.d("lastTimeConnectServerIp from SP: $it")
                uiState.lastTimeConnectServerIp = it
            }
            SPUtil.PathSetting.getUploadFileRootDir(activity)?.let {
                SwithunLog.d("uploadFileRootDir from SP: $it")
                uploadFileRootDir = it
            }
        })
    }

    override fun reduce(action: Action) {
        when (action) {
            Action.ConnectMyServer -> connectMyServer()
            Action.StartMeAsServer -> startMeAsServer()
            is Action.DownloadTransferFile -> downloadTransferFile(action)
            Action.SearchAllServer -> searchAllServer()
            is Action.ChangeLastTimeConnectServer -> changerLastTimeConnectServer(action)
            is Action.ChooseUploadFileRootDir -> chooseUploadFileRootDir(action)
        }
    }

    private fun chooseUploadFileRootDir(action: Action.ChooseUploadFileRootDir) {
        this.uploadFileRootDir = action.uploadPath
        vmCollection?.shareViewModel?.reduce(
            ShareViewModel.Action.NeedActivity { activity ->
                SPUtil.PathSetting.putUploadFileRootDir(activity, action.uploadPath)
            }
        )
    }

    private fun changerLastTimeConnectServer(action: Action.ChangeLastTimeConnectServer) {
        innerUISate.lastTimeConnectServerIp = action.serverIP
        vmCollection?.shareViewModel?.reduce(
            ShareViewModel.Action.NeedActivity { activity ->
                SPUtil.ServerSetting.putLastTimeConnectServer(activity, action.serverIP)
            }
        )
    }

    private fun startMeAsServer() {
        if (hasStartKernel) {
            innerUISate.startMeAsServerBtnText = "启动server：已连接内核 请勿重试"
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                innerUISate.startMeAsServerBtnText = "启动server：启动中..."
                // 启动内核
                launch {
                    delay(1000)
                    innerUISate.startMeAsServerBtnText = "启动server：连接内核中..."
                    try {
                        vmCollection?.connectKernelVM?.reduce(ConnectKernelViewModel.Action.ConnectKernelAction)
                        innerUISate.startMeAsServerBtnText = "启动server：已连接内核"
                        hasStartKernel = true
                    } catch (e: java.lang.Exception) {
                        innerUISate.startMeAsServerBtnText = "启动server：连接失败"
                        SwithunLog.e("启动内核失败：${e.message}")
                    }
                }
                val job = launch {
                    ServerSDK.startSever()
                }
                delay(1000)
                job.cancel()
            }
        }
    }

    private fun connectMyServer() {
        val connectServerM = vmCollection?.connectServerVM ?: return
        val myIP = Config.kernelConfig.kernelIP
        connectServerM.reduce(ConnectServerViewModel.Action.ConnectServer(myIP))
    }

    private fun searchAllServer() {
        viewModelScope.launch(Dispatchers.IO) {
            innerUISate.getAllServerBtnText = "搜寻中..."

            val ips = ServerSDK.getAllServerInLAN()
            innerUISate.allServersInLan = ips.toList()

            innerUISate.getAllServerBtnText = "搜寻其他可用server"
        }
    }

    private fun downloadTransferFile(action: Action.DownloadTransferFile) {
        when (action.contentType) {
            MessageTextDTO.ContentType.TEXT -> {
                SystemUtil.pushText2Clipboard(action.context, action.text)
                viewModelScope.launch(Dispatchers.IO) {
                    vmCollection?.shareViewModel?.snackbarHostState?.showSnackbar(message = "已复制")
                }
            }
            MessageTextDTO.ContentType.IMAGE -> {
                vmCollection?.connectServerVM?.reduce(
                    ConnectServerViewModel.Action.GetSessionFile(
                        action.text
                    )
                )
            }
        }
    }

}