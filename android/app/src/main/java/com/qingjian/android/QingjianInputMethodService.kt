
package com.qingjian.android

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class QingjianInputMethodService : InputMethodService() {

    private lateinit var compositionView: TextView

    private val composition = StringBuilder()

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }

        compositionView = TextView(this).apply {
            text = "青简输入法 · ARM64"
            textSize = 18f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }

        root.addView(
            compositionView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                44.dp()
            )
        )

        val candidateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        listOf("青", "简", "输入法", "你好").forEach { word ->
            candidateRow.addView(
                makeKey(word) {
                    currentInputConnection?.commitText(word, 1)
                    clearComposition()
                },
                LinearLayout.LayoutParams(0, 44.dp(), 1f)
            )
        }

        root.addView(candidateRow)

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
                line.addView(
                    makeKey(key) { handleKey(key) },
                    LinearLayout.LayoutParams(
                        0,
                        46.dp(),
                        if (key == "空格") 2f else 1f
                    ).apply {
                        setMargins(2.dp(), 3.dp(), 2.dp(), 3.dp())
                    }
                )
            }

            root.addView(line)
        }

        return root
    }

    private fun makeKey(label: String, action: () -> Unit): Button {
        return Button(this).apply {
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
    }

    private fun handleKey(key: String) {
        val connection: InputConnection =
            currentInputConnection ?: return

        when (key) {
            "⌫" -> {
                if (composition.isNotEmpty()) {
                    composition.deleteCharAt(composition.length - 1)
                    updateComposition()
                } else {
                    connection.deleteSurroundingText(1, 0)
                }
            }

            "空格" -> {
                if (composition.isNotEmpty()) {
                    connection.commitText(composition.toString(), 1)
                    clearComposition()
                } else {
                    connection.commitText(" ", 1)
                }
            }

            "↵" -> {
                connection.sendKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_ENTER
                    )
                )
                clearComposition()
            }

            "中/英", "?123", "⇧" -> {
                // 暂时保留功能入口，后续接入完整输入模式。
            }

            "。" -> connection.commitText("。", 1)
            "," -> connection.commitText("，", 1)

            else -> {
                if (key.length == 1) {
                    composition.append(key)
                    updateComposition()
                }
            }
        }
    }

    private fun updateComposition() {
        compositionView.text = if (composition.isEmpty()) {
            "青简输入法"
        } else {
            composition.toString()
        }
    }

    private fun clearComposition() {
        composition.clear()
        if (::compositionView.isInitialized) {
            updateComposition()
        }
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()
}
