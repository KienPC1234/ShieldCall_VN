package com.sentinel.antiscamvn

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast

class DebugController(private val context: Context) {

    fun showDebugMenu() {
        // Placeholder for a real debug menu
        AlertDialog.Builder(context)
            .setTitle("Debug Menu")
            .setMessage("This is a placeholder for the debug menu.")
            .setPositiveButton("OK", null)
            .setNegativeButton("Test Toast") { _, _ ->
                Toast.makeText(context, "Debug Toast!", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}
