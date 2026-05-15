package com.Ailock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LockFlowScreen()
        }
    }
}

enum class Screen {
    LOCK_REASON,
    LOCK_COMPLETE,
    LOCKED_HOME,
    UNLOCK_REASON,
    AI_LOADING,
    AI_RESULT,
    UNLOCKED_HOME
}

data class CurrentStats(
    val willPowerScore: Int,
    val todayOpenAppCount: Int,
    val accumUseApp: Int
)

data class UserRequest(
    val appName: String,
    val userInput: String,
    val lockReason: String,
    val currentStats: CurrentStats
)

data class UserResponse(
    val status: String,
    val text: String,
    val allowedTime: Int
)

interface ApiService {
    @POST("/testFinal")
    suspend fun judgeUnlock(
        @Body request: UserRequest
    ): UserResponse
}

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8080/"

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

@Composable
fun LockFlowScreen() {
    var screen by remember { mutableStateOf(Screen.LOCK_REASON) }

    var lockReason by remember { mutableStateOf("") }
    var aiText by remember { mutableStateOf("") }
    var allowedTime by remember { mutableStateOf(0) }
    var aiStatus by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val appName = "INSTAGRAM"

    val currentStats = CurrentStats(
        willPowerScore = 70,
        todayOpenAppCount = 5,
        accumUseApp = 45
    )

    when (screen) {
        Screen.LOCK_REASON -> LockReasonScreen(
            appName = appName,
            onSubmit = { inputLockReason ->
                lockReason = inputLockReason
                screen = Screen.LOCK_COMPLETE
            }
        )

        Screen.LOCK_COMPLETE -> LockCompleteScreen(
            appName = appName,
            onTimeout = {
                screen = Screen.LOCKED_HOME
            }
        )

        Screen.LOCKED_HOME -> LockedHomeScreen(
            onClickInstagram = {
                screen = Screen.UNLOCK_REASON
            }
        )

        Screen.UNLOCK_REASON -> UnlockReasonScreen(
            onSubmit = { userInput ->
                coroutineScope.launch {
                    try {
                        screen = Screen.AI_LOADING

                        val response = RetrofitClient.api.judgeUnlock(
                            UserRequest(
                                appName = appName,
                                userInput = userInput,
                                lockReason = lockReason,
                                currentStats = currentStats
                            )
                        )

                        isError = false
                        aiStatus = response.status
                        aiText = response.text
                        allowedTime = response.allowedTime
                        screen = Screen.AI_RESULT

                    } catch (e: Exception) {
                        isError = true
                        aiStatus = "FAIL"
                        aiText = "서버 연결 중 오류가 발생했어.\n다시 시도해줘."
                        allowedTime = 0
                        screen = Screen.AI_RESULT
                    }
                }
            }
        )

        Screen.AI_LOADING -> AiLoadingScreen()

        Screen.AI_RESULT -> AiResultScreen(
            text = aiText,
            allowedTime = allowedTime,
            showButton = allowedTime > 0 && !isError && aiStatus != "CRITICAL" && aiStatus != "FAIL",
            isError = isError,
            onClickGoApp = {
                screen = Screen.UNLOCKED_HOME
            },
            onTimeout = {
                screen = Screen.LOCKED_HOME
            }
        )

        Screen.UNLOCKED_HOME -> UnlockedHomeScreen()
    }
}

@Composable
fun LockReasonScreen( //잠금 이유 입력 화면
    appName: String,
    onSubmit: (String) -> Unit
) {
    var lockReason by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(230.dp))

        Text(
            text = "$appName\n잠금하려는\n이유를 알려줘",
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 45.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(145.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = lockReason,
                onValueChange = { lockReason = it },
                placeholder = {
                    Text("잠금 이유를 입력하세요.")
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (lockReason.isNotBlank()) {
                        onSubmit(lockReason)
                    }
                },
                modifier = Modifier
                    .width(58.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("→", fontSize = 22.sp, color = Color.Black)
            }
        }
    }
}

@Composable
fun LockCompleteScreen( //잠금 완료 화면
    appName: String,
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(5000)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(285.dp))

        Text(
            text = appName,
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "잠금 완료 !",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "5초 후 자동으로 돌아갑니다.",
            fontSize = 20.sp,
            color = Color.Black
        )
    }
}

@Composable
fun LockedHomeScreen(
    onClickInstagram: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "잠긴 앱 목록",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onClickInstagram,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Instagram 열기",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Instagram은 현재 잠금 상태입니다.",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun UnlockReasonScreen( //잠금 해제 이유 입력 화면
    onSubmit: (String) -> Unit
) {
    var userInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(260.dp))

        Text(
            text = "잠금을 해제하려는\n이유가 뭐야 ?",
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 45.sp,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(150.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                placeholder = {
                    Text("잠금 해제 이유를 입력하세요.")
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Black,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    cursorColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (userInput.isNotBlank()) {
                        onSubmit(userInput)
                    }
                },
                modifier = Modifier
                    .width(58.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("→", fontSize = 22.sp, color = Color.Black)
            }
        }
    }
}

@Composable
fun AiLoadingScreen() { //로딩 화면
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "잠시만 !\n판단 중이야",
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 46.sp,
            color = Color.Black
        )
    }
}

@Composable
fun AiResultScreen( //판단 결과 화면
    text: String,
    allowedTime: Int,
    showButton: Boolean,
    isError: Boolean,
    onClickGoApp: () -> Unit,
    onTimeout: () -> Unit
) {
    LaunchedEffect(showButton, isError) {
        if (!showButton) {
            delay(5000)
            onTimeout()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(310.dp))

        Text(
            text = text,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            color = Color.Black
        )

        if (!isError) {
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "허용 시간: ${allowedTime}분",
                fontSize = 25.sp,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(180.dp))

        if (showButton) {
            Button(
                onClick = onClickGoApp,
                modifier = Modifier
                    .width(255.dp)
                    .height(62.dp),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Black),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "앱 사용하러 가기",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Black
                )
            }
        } else {
            Text(
                text = "5초 후, 홈 화면으로 돌아갑니다.",
                fontSize = 20.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
fun UnlockedHomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.instagram),
            contentDescription = "Instagram",
            modifier = Modifier.size(150.dp)
        )
    }
}