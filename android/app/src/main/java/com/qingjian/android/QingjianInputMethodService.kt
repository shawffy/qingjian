package com.qingjian.android

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class QingjianInputMethodService : InputMethodService() {
    private lateinit var compositionView: TextView
    private lateinit var candidateRow: LinearLayout
    private val fallbackComposition = StringBuilder()
    private var engineReady = false
    private var englishMode = false
    private external fun nativeInit(): Boolean
    private external fun nativePushChar(ch: Char)
    private external fun nativeBackspace()
    private external fun nativeClear()
    private external fun nativeComposition(): String
    private external fun nativeCandidates(): String
    private external fun nativeCommit(index: Int): String

    private fun initializeEngine() {
        if (engineReady) return
        try {
            System.loadLibrary("qingjian_android_bridge")
            engineReady = nativeInit()
        } catch (error: Throwable) {
            engineReady = false
            android.util.Log.e("QingjianIME", "Rust engine initialization failed", error)
        }
    }

    override fun onCreateInputView(): View {
        initializeEngine()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        compositionView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }
        root.addView(compositionView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(candidateRow)
        refreshFromEngine()

        val rows = listOf(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("?123", ",", "中/英", "空格", "。", "↵")
        )
        rows.forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEach { key ->
                line.addView(makeKey(key) { handleKey(key) }, LinearLayout.LayoutParams(0, 46.dp(), if (key == "空格") 2f else 1f).apply {
                    setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
                })
            }
            root.addView(line)
        }
        return root
    }

    private fun makeKey(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.rgb(35, 35, 35))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 7.dp().toFloat()
            setStroke(1.dp(), Color.LTGRAY)
        }
        setPadding(2, 0, 2, 0)
        setOnClickListener { action() }
    }

    private fun handleKey(key: String) {
        val connection: InputConnection = currentInputConnection ?: return
        when (key) {
            "⌫" -> {
                if (engineReady) {
                    if (nativeComposition().isNotEmpty()) {
                        nativeBackspace()
                        refreshFromEngine()
                    } else connection.deleteSurroundingText(1, 0)
                } else if (fallbackComposition.isNotEmpty()) {
                    fallbackComposition.deleteCharAt(fallbackComposition.length - 1)
                    updateFallbackComposition()
                } else connection.deleteSurroundingText(1, 0)
            }
            "空格" -> {
                if (engineReady) {
                    connection.commitText(" ", 1)
               } else if (engineReady) {
                    val candidates = currentCandidates()
                    val result = if (candidates.isNotEmpty()) nativeCommit(0)
                    else nativeComposition()
                    if (result.isNotEmpty()) connection.commitText(result, 1)
                    nativeClear()
                    refreshFromEngine()
                } else if (fallbackComposition.isNotEmpty()) {
                    connection.commitText(fallbackComposition.toString(), 1)
                    fallbackComposition.clear()
                    updateFallbackComposition()
                } else connection.commitText(" ", 1)
            }
            "↵" -> {
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                clearComposition()
            }
            "中/英" -> {
                if (engineReady) nativeClear()
                fallbackComposition.clear()
                englishMode = !englishMode
                refreshFromEngine()
            }
            "?123", "⇧" -> Unit
            "。" -> connection.commitText("。", 1)
            "," -> connection.commitText("，", 1)
            else -> if (key.length == 1 && key[0].isLetter()) {
                 if (englishMode) {
                    connection.commitText(key, 1)
                 if (englishMode) {
                    connection.commitText(key, 1)
                    refreshFromEngine()
                } else {
                    fallbackComposition.append(key)
                    updateFallbackComposition()
                }
            }
        }
    }

    private fun currentCandidates(): List<String> = if (!engineReady) emptyList() else
        nativeCandidates().split("\t").filter { it.isNotBlank() }

    private fun refreshFromEngine() {
        if (!::compositionView.isInitialized || !::candidateRow.isInitialized) return
        val composing = if (engineReady) nativeComposition() else fallbackComposition.toString()
        compositionView.text = when {
            !engineReady -> "青简输入法 · 引擎未加载"
            composing.isNotEmpty() -> composing
            else -> "青简输入法 · Rust"
        }
        candidateRow.removeAllViews()
        val candidates = currentCandidates()
        candidates.forEachIndexed { index, word ->
            candidateRow.addView(makeKey(word) {
                val connection = currentInputConnection ?: return@makeKey
                if (engineReady) {
                    val committed = nativeCommit(index)
                    if (committed.isNotEmpty()) connection.commitText(committed, 1)
                    refreshFromEngine()
                }
            }, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply {
                setMargins(1.dp(), 0, 1.dp(), 0)
            })
        }
        if (!engineReady) {
            candidateRow.addView(TextView(this).apply {
                text = "请检查原生库与词库"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        }
    }

    private fun updateFallbackComposition() {
        if (::compositionView.isInitialized) compositionView.text =
            if (fallbackComposition.isEmpty()) "青简输入法 · 引擎未加载" else fallbackComposition.toString()
    }

    private fun clearComposition() {
        fallbackComposition.clear()
        if (engineReady) nativeClear()
        refreshFromEngine()
        if (!engineReady) updateFallbackComposition()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
