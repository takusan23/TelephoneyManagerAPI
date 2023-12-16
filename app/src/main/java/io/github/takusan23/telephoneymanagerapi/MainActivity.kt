package io.github.takusan23.telephoneymanagerapi

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.takusan23.telephoneymanagerapi.ui.theme.TelephoneyManagerAPITheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HiddenApiBypass.addHiddenApiExemptions("")

        setContent {
            TelephoneyManagerAPITheme {
                Scaffold {
                    Box(modifier = Modifier.padding(it)) {
                        HomeScreen()
                    }
                }
            }
        }
    }
}

enum class Page {
    CELL_INFO,
    STRENGTH,
    SERVICE_STATE,
    CARRIER_CONFIG,
    METHOD,
    FIELD,
    CALLBACK
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val telephonyManager = remember {
        (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
            .createForSubscriptionId(SubscriptionManager.getActiveDataSubscriptionId())
    }
    val searchWord = remember { mutableStateOf("") }
    val currentPage = remember { mutableStateOf(Page.CELL_INFO) }

    val callbackSignalStrengthPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val callbackCellInfoPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val callbackDisplayInfoPair = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val callbackServiceState = remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    DisposableEffect(key1 = searchWord.value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.CellInfoListener, TelephonyCallback.DisplayInfoListener, TelephonyCallback.ServiceStateListener {
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

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    callbackServiceState.value = serviceState.javaClass.sageInvokeAndGet(serviceState).filterWord(searchWord.value)
                }
            }
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            onDispose { telephonyManager.unregisterTelephonyCallback(callback) }
        } else {
            onDispose { /* do nothing */ }
        }
    }

    // プロパティ全部取得する
    var propertyList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // 存在するメソッドも呼び出す
    // 引数がないもののみ
    var methodList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // getSignalStrength
    var getSignalStrengthResult by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // getAllCellInfo
    var getAllCellInfoResult by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // getServiceState
    var getServiceStateResult by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // CarrierConfig
    var getCarrierConfigResult by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    fun updateState() {
        propertyList = TelephonyManager::class.java.declaredFields
            .map {
                it.joinNameAndClassName to it.safeString(telephonyManager)
            }.filterWord(searchWord.value) // フィルターする拡張関数を書いた

        methodList = TelephonyManager::class.java.declaredMethods
            .filter { it.parameterCount == 0 }
            .map {
                // システム権限が必要な場合例外投げるので
                it.joinNameAndClassName to it.safeInvoke(telephonyManager)
            }.filterWord(searchWord.value) // フィルターする拡張関数を書いた

        getSignalStrengthResult = (telephonyManager.signalStrength ?: emptyList<Pair<String, String>>()).let { signalStrength ->
            val methodList = signalStrength.javaClass.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(signalStrength) }
            val fieldList = signalStrength.javaClass.declaredFields.map { it.joinNameAndClassName to it.safeString(signalStrength) }
            (methodList + fieldList).filterWord(searchWord.value)
        }

        getAllCellInfoResult = telephonyManager.allCellInfo.map { cellInfo ->
            val clazz = cellInfo.javaClass
            val method = (clazz.declaredMethods + clazz.methods)
            val field = (clazz.declaredFields + clazz.fields)
            val identityClazz = cellInfo.cellIdentity.javaClass
            val signalClazz = cellInfo.cellSignalStrength.javaClass

            val returnList = mutableListOf<Pair<String, String>>()
            returnList += identityClazz.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(cellInfo.cellIdentity) }
            returnList += signalClazz.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(cellInfo.cellSignalStrength) }
            returnList += identityClazz.declaredFields.map { it.joinNameAndClassName to it.safeString(cellInfo.cellIdentity) }
            returnList += signalClazz.declaredFields.map { it.joinNameAndClassName to it.safeString(cellInfo.cellSignalStrength) }
            returnList += method.map { it.name to it.safeInvoke(cellInfo) }
            returnList += field.map { it.name to it?.safeString(cellInfo).toString() }
            returnList
        }.flatten().filterWord(searchWord.value)

        getServiceStateResult = (telephonyManager.serviceState ?: emptyList<Pair<String, String>>()).let { serviceState ->
            val methodList = serviceState.javaClass.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(serviceState) }
            val fieldList = serviceState.javaClass.declaredFields.map { it.joinNameAndClassName to it.safeString(serviceState) }
            (methodList + fieldList).filterWord(searchWord.value)
        }

        getCarrierConfigResult = telephonyManager.carrierConfig.let { carrierConfig ->
            // CarrierConfig の各値を取得する
            val valueList = carrierConfig.keySet().map { key -> key to carrierConfig.get(key).toString() }
            // メソッド
            val methodList = carrierConfig.javaClass.declaredMethods.map { it.joinNameAndClassName to it.safeInvoke(carrierConfig) }
            val fieldList = carrierConfig.javaClass.declaredFields.map { it.joinNameAndClassName to it.safeString(carrierConfig) }
            (valueList + methodList + fieldList).filterWord(searchWord.value)
        }
    }

    // キーワード変化時に
    LaunchedEffect(key1 = searchWord.value) {
        updateState()
    }

    // onStart のたびに（画面表示時に）
    DisposableEffect(key1 = Unit) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                updateState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

getServiceState ---
${getServiceStateResult.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

getCarrierConfig ---
${getCarrierConfigResult.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackSignalStrengthPair ---
${callbackSignalStrengthPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackCellInfoPair ---
${callbackCellInfoPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackDisplayInfoPair ---
${callbackDisplayInfoPair.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

callbackServiceState ---
${callbackServiceState.value.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

methodList ---
${methodList.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}

propertyList ---
${propertyList.joinToString(separator = "\n") { "${it.first} = ${it.second}" }}
            """.trimIndent()
            it.write(text.toByteArray(Charsets.UTF_8))
        }
        Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
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
            ScrollableTabRow(selectedTabIndex = currentPage.value.ordinal) {
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
                items(getAllCellInfoResult) { (name, value) ->
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
                    Divider()
                }
            }

            Page.SERVICE_STATE -> {
                item {
                    Text(
                        text = "getServiceState",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(getServiceStateResult) { (name, value) ->
                    Text(text = "$name = $value")
                    Divider()
                }
            }

            Page.CARRIER_CONFIG -> {
                item {
                    Text(
                        text = "CarrierConfig",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(getCarrierConfigResult) { (name, value) ->
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
                    Divider()
                }

                item {
                    Text(
                        text = "ServiceInfo",
                        fontSize = 30.sp
                    )
                    Divider()
                }
                items(callbackServiceState.value) { (name, value) ->
                    Text(text = "$name = $value")
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
                    Text(text = "$name = $value")
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

private fun Method.safeInvoke(obj: Any): String = runCatching { apply { isAccessible = true }.invoke(obj) }.onFailure { it.javaClass.simpleName }.map { (it as? IntArray)?.toList() ?: it }.getOrNull().toString()

private fun Field.safeString(obj: Any): String = this.apply { isAccessible = true }.get(obj)?.toString() ?: "error"

private fun Collection<Pair<String, String>>.filterWord(word: String) = filter { it.first.contains(word) || it.second.contains(word) }