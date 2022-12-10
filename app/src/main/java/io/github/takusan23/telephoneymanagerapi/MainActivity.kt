package io.github.takusan23.telephoneymanagerapi

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.takusan23.telephoneymanagerapi.ui.theme.TelephoneyManagerAPITheme

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

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val telephonyManager = remember { context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager }
    // プロパティ全部取得する
    val propertyList = remember {
        TelephonyManager::class.java.declaredFields.map {
            it.name to it?.apply { isAccessible = true }?.get(telephonyManager).toString()
        }
    }

    // 存在するメソッドも呼び出す
    // 引数がないもののみ
    val methodList = remember {
        TelephonyManager::class.java.declaredMethods
            .filter { it.parameterCount == 0 }
            .map {
                it.name to try {
                    it?.apply { isAccessible = true }?.invoke(telephonyManager).toString()
                } catch (e: Exception) {
                    e.javaClass.simpleName
                }
            }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = "メソッド",
            fontSize = 25.sp
        )
        Divider()
        methodList.forEach { (name, value) ->
            Text(text = "${name} = ${value}")
            Divider()
        }
        Text(
            text = "プロパティ",
            fontSize = 25.sp
        )
        propertyList.forEach { (name, value) ->
            Text(text = "${name} = ${value}")
        }
    }

}