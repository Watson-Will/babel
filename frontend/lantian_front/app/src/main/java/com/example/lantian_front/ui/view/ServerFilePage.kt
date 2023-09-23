package com.example.lantian_front.ui.view

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lantian_front.*
import com.example.lantian_front.model.Storage
import com.example.lantian_front.model.StorageType
import com.example.lantian_front.ui.view.component.SCommonButton
import com.example.lantian_front.viewmodel.filemanager.Action
import com.example.lantian_front.viewmodel.filemanager.FileManagerViewModel
import com.example.lantian_front.viewmodel.filemanager.model.PathItem

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun ServerFilePage(
    fileManagerViewModel: FileManagerViewModel = viewModel(),
) {
    val actFileClick = { it: PathItem.FileItem ->
        fileManagerViewModel.reduce(Action.ClickFileV2(it))
    }
    val actFolderClick = { it: PathItem.FolderItem ->
        fileManagerViewModel.reduce(Action.ClickFolderV2(it))
    }
    val funGeneratePathMoreAction: (PathItem) -> List<Pair<Pair<String, ImageVector>, (pathItem: PathItem) -> Unit>> =
        { emptyList() }

    val storageList = fileManagerViewModel.uiState.storageList
    val onStorageClick = { s: Storage ->
        fileManagerViewModel.reduce(Action.GetFileListOfStorage(s, s.basePath))
    }

    Row {
        Button(onClick = {
            fileManagerViewModel.reduce(Action.GetStorageList)
        }) {
            Text(text = "获取存储")
        }
        StorageListView(storageList, onStorageClick)
        FileManagerView(
            funGeneratePathMoreAction,
            actFileClick,
            actFolderClick,
            fileManagerViewModel.uiState.pathList
        )
    }
}


@Preview
@Composable
fun StorageListView(
    storageList: List<Storage> = listOf(
        Storage(
            "name",
            "id",
            StorageType.LOCAL_INNER.value,
            "/storage/emulated/0/"
        )
    ),
    onStorageClick: (id: Storage) -> Unit = {},
) {
    LazyColumn() {
        items(storageList) { s ->
            SCommonButton(onClick = { onStorageClick.invoke(s) }) {
                Text(text = "Storage: ${s.name}")
            }
        }
    }
}

@Preview
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun FileManagerView(
    funGeneratePathMoreAction: (PathItem) -> List<Pair<Pair<String, ImageVector>, (pathItem: PathItem) -> Unit>> = { emptyList() },
    actFileClick: (file: PathItem.FileItem) -> Unit = {},
    actFolderClick: (folder: PathItem.FolderItem) -> Unit = {},
    dataPathItemList: List<PathItem> = listOf(
        PathItem.FolderItem.getPreview(),
        PathItem.FileItem.getPreview(),
        PathItem.FileItem.getPreview(),
    ),
) {
    LazyColumn(
        modifier = Modifier
            .width(Dp(600f))
    ) {
        items(dataPathItemList) { path: PathItem ->
            when (path) {
                is PathItem.FileItem -> {
                    FileItemView(file = path, itemCL = actFileClick)
                }

                is PathItem.FolderItem -> {
                    FolderItemView(
                        path,
                        funGeneratePathMoreAction,
                        actFileClick,
                        actFolderClick
                    )
                }
            }
        }
    }
}


@Composable
fun FileItemView(
    file: PathItem.FileItem = PathItem.FileItem.getPreview(),
    itemCL: (file: PathItem.FileItem) -> Unit = {},
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight()
            .clickable {
                SwithunLog.d("click file: ${file.name}")
                itemCL(file)
            },
        shape = RoundedCornerShape(10),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceTint)
    ) {
        Row(Modifier.padding(10.dp)) {
            Text(text = "File: ")
            Text(text = file.name)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun FolderItemView(
    data4Folder: PathItem.FolderItem = PathItem.FolderItem.getPreview(),
    funGeneratePathMoreAction: (PathItem) -> List<Pair<Pair<String, ImageVector>, (pathItem: PathItem) -> Unit>> = { emptyList() },
    actFileClick: (PathItem.FileItem) -> Unit = {},
    actFolderClick: (folder: PathItem.FolderItem) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable {
                SwithunLog.d("click folder: ${data4Folder.name}")
                actFolderClick(data4Folder)
            },
        shape = RoundedCornerShape(10),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceTint)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .weight(1f)
            ) {
                Row {
                    Icon(Icons.Filled.List, contentDescription = "Folder Icon")
                    Text(
                        text = data4Folder.name,
                        modifier = Modifier.padding(10.dp, 0.dp)
                    )
                }
                if (data4Folder.isOpening) {
                    NestedPathListView(
                        data4Folder.children,
                        funGeneratePathMoreAction,
                        actFileClick,
                        actFolderClick
                    )
                }
            }
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .wrapContentSize(Alignment.TopStart)
            ) {

                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Folder Icon",
                        modifier = Modifier
                            .width(30.dp)
                            .height(30.dp)
                    )
                }
                val list: List<Pair<Pair<String, ImageVector>, (pathItem: PathItem) -> Unit>> =
                    funGeneratePathMoreAction(data4Folder)

                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    for (l in list) {
                        DropdownMenuItem(
                            text = { Text(text = "haha") },
                            onClick = { },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun NestedPathListView(
    data4PathList: List<PathItem> = listOf(
        PathItem.FolderItem.getPreview(),
        PathItem.FileItem.getPreview()
    ),
    funGeneratePathMoreAction: (PathItem) -> List<Pair<Pair<String, ImageVector>, (pathItem: PathItem) -> Unit>> = { emptyList() },
    actFileClick: (PathItem.FileItem) -> Unit = {},
    actFolderClick: (folder: PathItem.FolderItem) -> Unit = {},
) {
    data4PathList.forEach { path ->
        when (path) {
            is PathItem.FileItem -> {
                FileItemView(path, actFileClick)
            }

            is PathItem.FolderItem -> {
                FolderItemView(path, funGeneratePathMoreAction, actFileClick, actFolderClick)
            }
        }
    }
}