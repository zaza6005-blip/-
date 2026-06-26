package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.Content
import com.example.data.GenerateContentRequest
import com.example.data.GenerationConfig
import com.example.data.MorphologicalWord
import com.example.data.Part
import com.example.data.ResponseFormat
import com.example.data.ResponseFormatText
import com.example.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun loadHistoryItem(item: HistoryItem) {
        _uiState.value = UiState.Success(item.result)
    }

    fun analyzeText(text: String) {
        if (text.isBlank()) return
        
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.value = UiState.Error("API Key is missing. Please add it via the Secrets panel.")
                    return@launch
                }

                val schema = buildJsonObject {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("word") {
                                put("type", "STRING")
                            }
                            putJsonObject("root") {
                                put("type", "STRING")
                            }
                            putJsonObject("lemma") {
                                put("type", "STRING")
                            }
                            putJsonObject("pattern") {
                                put("type", "STRING")
                            }
                            putJsonObject("affixes") {
                                put("type", "STRING")
                            }
                        }
                        put("required", kotlinx.serialization.json.buildJsonArray {
                            add(kotlinx.serialization.json.JsonPrimitive("word"))
                            add(kotlinx.serialization.json.JsonPrimitive("root"))
                            add(kotlinx.serialization.json.JsonPrimitive("lemma"))
                            add(kotlinx.serialization.json.JsonPrimitive("pattern"))
                            add(kotlinx.serialization.json.JsonPrimitive("affixes"))
                        })
                    }
                }

                val systemInstruction = "أنت خبير متمرس في اللسانيات الحاسوبية وعلم الصرف العربي.\nمهمتك هي تحليل الشواهد النصية التي يرسلها المستخدم تحليلاً صرفياً دقيقاً وفقاً لمعايير الأصل الفصيح وقواعد المجامع اللغوية.\n\nلكل كلمة يتم تحديدها في النص، استخرج ما يلي:\n1. الكلمة كما وردت في النص (Surface Form).\n2. الجذر الثلاثي أو الرباعي (Root/Stem).\n3. الشكل المعجمي المجرد (Lemma).\n4. الوزن الصرفي.\n5. السوابق واللواحق المرتبطة بها (إن وجدت).\n\nقواعد الإخراج:\n- يجب أن يكون المخرج حصرياً بتنسيق JSON صالح (Valid JSON).\n- لا تضف أي نصوص أو شروحات خارج هيكل الـ JSON."

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = text)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
                    generationConfig = GenerationConfig(
                        responseFormat = ResponseFormat(
                            text = ResponseFormatText(
                                mimeType = "application/json",
                                schema = schema
                            )
                        ),
                        temperature = 0.2f
                    )
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val jsonResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (jsonResponse != null) {
                    val parsedResponse = Json { ignoreUnknownKeys = true }.decodeFromString<List<MorphologicalWord>>(jsonResponse)
                    _uiState.value = UiState.Success(parsedResponse)
                    
                    val currentHistory = _history.value.toMutableList()
                    currentHistory.removeAll { it.text == text }
                    currentHistory.add(0, HistoryItem(text, parsedResponse))
                    if (currentHistory.size > 5) {
                        currentHistory.removeLast()
                    }
                    _history.value = currentHistory
                } else {
                    _uiState.value = UiState.Error("No response received from the model.")
                }

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val result: List<MorphologicalWord>) : UiState()
    data class Error(val message: String) : UiState()
}

data class HistoryItem(
    val text: String,
    val result: List<MorphologicalWord>
)
