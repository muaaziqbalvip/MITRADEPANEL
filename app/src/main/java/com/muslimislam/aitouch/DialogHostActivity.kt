package com.muslimislam.aitouch

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/**
 * A fully transparent, invisible-background Activity whose only job is to
 * host dialogs launched from OverlayService (a Service cannot reliably show
 * a focusable/touchable AlertDialog on top of a TYPE_APPLICATION_OVERLAY
 * window — buttons don't receive touch events). This Activity has real
 * window focus, so its dialogs work correctly, then it finishes and sends
 * the result back to OverlayService via a broadcast.
 */
class DialogHostActivity : Activity() {

    companion object {
        const val ACTION_DOT_NAME_RESULT = "com.muslimislam.aitouch.DOT_NAME_RESULT"
        const val ACTION_DOT_MENU_RESULT = "com.muslimislam.aitouch.DOT_MENU_RESULT"
        const val EXTRA_MODE = "mode"
        const val EXTRA_DOT_ID = "dot_id"
        const val EXTRA_EXISTING_NAME = "existing_name"
        const val EXTRA_RESULT_NAME = "result_name"
        const val EXTRA_MENU_CHOICE = "menu_choice"

        const val MODE_ADD = "add"
        const val MODE_RENAME = "rename"
        const val MODE_MENU = "menu"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ADD
        val dotId = intent.getStringExtra(EXTRA_DOT_ID)

        when (mode) {
            MODE_ADD -> showNameDialog(dotId, null)
            MODE_RENAME -> showNameDialog(dotId, intent.getStringExtra(EXTRA_EXISTING_NAME))
            MODE_MENU -> showMenuDialog(dotId, intent.getStringExtra(EXTRA_EXISTING_NAME))
        }
    }

    private fun showNameDialog(dotId: String?, existingName: String?) {
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)
        existingName?.let { editText.setText(it) }

        AlertDialog.Builder(this)
            .setTitle(if (existingName == null) "Naya Touching Dot" else "Naam Badlo")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editText.text.toString().trim()
                val intent = Intent(ACTION_DOT_NAME_RESULT)
                intent.putExtra(EXTRA_DOT_ID, dotId)
                intent.putExtra(EXTRA_RESULT_NAME, name)
                sendBroadcast(intent)
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showMenuDialog(dotId: String?, currentName: String?) {
        val locked = currentName?.startsWith("[LOCKED]") == true
        val options = if (locked)
            arrayOf("Unlock (move karne do)", "Rename", "Delete")
        else
            arrayOf("Lock (fix kar do)", "Rename", "Delete")

        AlertDialog.Builder(this)
            .setTitle("Dot Options")
            .setItems(options) { _, which ->
                val intent = Intent(ACTION_DOT_MENU_RESULT)
                intent.putExtra(EXTRA_DOT_ID, dotId)
                intent.putExtra(EXTRA_MENU_CHOICE, which)
                sendBroadcast(intent)
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}