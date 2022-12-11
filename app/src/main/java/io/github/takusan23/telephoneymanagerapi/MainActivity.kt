package io.github.takusan23.telephoneymanagerapi

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.telephoneymanagerapi.ui.theme.TelephoneyManagerAPITheme
import java.lang.reflect.Field
import java.lang.reflect.Method

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelephoneyManagerAPITheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    HomeScreen()
                }
            }
        }
    }
}

enum class Page {
    CELL_INFO,
    STRENGTH,
    METHOD,
    FIELD,
    CALLBACK,
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }
    val searchWord = remember { mutableStateOf("") }
    val currentPage = remember { mutableStateOf(Page.CELL_INFO) }

    val callbackSignalStrengthPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val callbackCellInfoPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val callbackDisplayInfoPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    DisposableEffect(key1 = searchWord.value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.CellInfoListener, TelephonyCallback.DisplayInfoListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    callbackSignalStrengthPair.value = signalStrength.javaClass.sageInvokeAndGet(signalStrength).filterWord(searchWord.value)
                }

                override fun onCellInfoChanged(cellInfoList: MutableList<CellInfo>) {
                    callbackCellInfoPair.value = cellInfoList.map { cellInfo ->
                        cellInfo.javaClass.sageInvokeAndGet(cellInfo).filterWord(searchWord.value)
                    }.flatten()
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    callbackDisplayInfoPair.value = telephonyDisplayInfo.javaClass.sageInvokeAndGet(telephonyDisplayInfo).filterWord(searchWord.value)
                }
            }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            onDispose {
                telephonyManager.unregisterTelephonyCallback(callback)
            }
        } else {
            onDispose { /* do nothing */ }
        }
    }

    // プロパティ全部取得する
    val propertyList = remember(searchWord.value) {
        TelephonyManager::class.java.declaredFields
            .map {
                it.joinNameAndClassName to it.safeString(telephonyManager)
            }.filterWord(searchWord.value) // フィルターする拡張関数を書いた
    }

    // 存在するメソッドも呼び出す
    // 引数がないもののみ
    val methodList = remember(searchWord.value) {
        TelephonyManager::class.java.declaredMethods
            .filter { it.parameterCount == 0 }
            .map {
                // システム権限が必要な場合例外投げるので
                it.joinNameAndClassName to it.safeInvoke(telephonyManager)
            }.filterWord(searchWord.value) // フィルターする拡張関数を書いた
    }

    val getSignalStrengthResult = remember(searchWord.value) {
        val signalStrength = telephonyManager.signalStrength ?: emptyList<Pair<String, String>>()
        val methodList = signalStrength.javaClass.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(signalStrength) }
        val fieldList = signalStrength.javaClass.declaredFields.map { it.joinNameAndClassName to it.safeString(signalStrength) }
        (methodList + fieldList).filterWord(searchWord.value)
    }

    // getAllCellInfo
    val getAllCellInfoResult = remember(searchWord.value) {
        telephonyManager.allCellInfo.map { cellInfo ->
            val clazz = cellInfo.javaClass
            val method = clazz.declaredMethods
            val field = clazz.declaredFields
            val identityClazz = cellInfo.cellIdentity.javaClass
            val signalClazz = cellInfo.cellSignalStrength.javaClass

            val returnList = mutableListOf<Pair<String, String>>()
            returnList += identityClazz.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(cellInfo.cellIdentity) }
            returnList += signalClazz.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(cellInfo.cellSignalStrength) }
            returnList += identityClazz.declaredFields.map { it.joinNameAndClassName to it.safeString(cellInfo.cellIdentity) }
            returnList += signalClazz.declaredFields.map { it.joinNameAndClassName to it.safeString(cellInfo.cellSignalStrength) }
            returnList += method.map { it.name to it.safeInvoke(cellInfo) }
            returnList += field.map { it.name to it?.get(cellInfo).toString() }
            returnList
        }.flatten().filterWord(searchWord.value)
    }

    /** 保存する */
    fun saveToTextFile() {
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "電測API_${System.currentTimeMillis()}.txt")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, "Documents")
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return
        context.contentResolver.openOutputStream(uri, "w")?.use {
            val text = """
getAllCellInfoResult ---
${getAllCellInfoResult.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

getSignalStrengthResult ---
${getSignalStrengthResult.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackSignalStrengthPair ---
${callbackSignalStrengthPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackCellInfoPair ---
${callbackCellInfoPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackDisplayInfoPair ---
${callbackDisplayInfoPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

methodList ---
${methodList.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

propertyList ---
${propertyList.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}
            """.trimIndent()
            it.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    LazyColumn {
        item {
            Row {
                OutlinedTextField(
                    value = searchWord.value,
                    label = { Text(text = "検索") },
                    onValueChange = { searchWord.value = it }
                )
                Button(onClick = { saveToTextFile() }) {
                    Text(text = "保存")
                }
            }
        }
        item {
            ScrollableTabRow(
                selectedTabIndex = currentPage.value.ordinal,
                backgroundColor = Color.Transparent
            ) {
                Page.values().forEach {
                    Tab(
                        selected = it == currentPage.value,
                        onClick = { currentPage.value = it },
                    ) {
                        Text(
                            modifier = Modifier.padding(10.dp),
                            text = it.name
                        )
                    }
                }
            }
        }
        when (currentPage.value) {
            Page.CELL_INFO -> {
                item {
                    Text(
                        text = "getAllCellInfo",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(getAllCellInfoResult) {
                    Text(text = "${it.first} = ${it.second}")
                    Divider()
                }
            }
            Page.STRENGTH -> {
                item {
                    Text(
                        text = "getSignalStrength",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(getSignalStrengthResult) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }
            }
            Page.METHOD -> {
                item {
                    Text(
                        text = "メソッド",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(methodList) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }
            }
            Page.FIELD -> {
                item {
                    Text(
                        text = "フィールド",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(propertyList) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }
            }
            Page.CALLBACK -> {
                item {
                    Text(
                        text = "SignalStrength",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(callbackSignalStrengthPair.value) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }

                item {
                    Text(
                        text = "CellInfo",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(callbackCellInfoPair.value) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }

                item {
                    Text(
                        text = "DisplayInfo",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(callbackDisplayInfoPair.value) { (name, value) ->
                    Text(text = "${name} = ${value}")
                    Divider()
                }
            }
        }
    }
}

private val Method.joinNameAndClassName: String
    get() = "${declaringClass.simpleName}.${name}"

private val Field.joinNameAndClassName: String
    get() = "${declaringClass.simpleName}.${name}"

private fun <T : Any> Class<T>.sageInvokeAndGet(obj: Any) = declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(obj) } + declaredFields.map { it.joinNameAndClassName to it.safeString(obj) }

private fun Method.safeInvoke(obj: Any): String = runCatching { apply { isAccessible = true }.invoke(obj) }.onFailure { it.javaClass.simpleName }.getOrNull().toString()

private fun Field.safeString(obj: Any): String = this.apply { isAccessible = true }.get(obj)!!.toString()

private fun Collection<Pair<String, String>>.filterWord(word: String) = filter { it.first.contains(word) || it.second.contains(word) }