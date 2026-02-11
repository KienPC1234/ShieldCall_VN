package com.sentinel.antiscamvn.phone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sentinel.antiscamvn.R

class DialpadFragment : Fragment() {

    private lateinit var txtInput: TextView
    private var onCallRequest: ((String) -> Unit)? = null

    fun setOnCallRequestListener(listener: (String) -> Unit) {
        onCallRequest = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_dialpad, container, false)
        
        txtInput = view.findViewById(R.id.txt_input_number)
        val btnCall = view.findViewById<ImageButton>(R.id.btn_dial_pad_call)
        val btnBackspace = view.findViewById<ImageButton>(R.id.btn_backspace)

        // Find GridLayout and set up button clicks
        val gridLayout = view.findViewById<GridLayout>(R.id.dial_grid)
        
        for (i in 0 until gridLayout.childCount) {
            val child = gridLayout.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener {
                    appendDigit(child.text.toString())
                }
            }
        }

        btnCall.setOnClickListener {
            val number = txtInput.text.toString()
            if (number.isNotEmpty()) {
                onCallRequest?.invoke(number)
            }
        }

        btnBackspace.setOnClickListener {
            val current = txtInput.text.toString()
            if (current.isNotEmpty()) {
                txtInput.text = current.dropLast(1)
            }
        }
        
        btnBackspace.setOnLongClickListener {
            txtInput.text = ""
            true
        }

        return view
    }

    private fun appendDigit(digit: String) {
        txtInput.text = "${txtInput.text}$digit"
    }
}
