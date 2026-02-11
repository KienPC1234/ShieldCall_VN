package com.sentinel.antiscamvn.phone

import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sentinel.antiscamvn.R

class RecentsFragment : Fragment() {

    private var onCallRequest: ((String) -> Unit)? = null

    fun setOnCallRequestListener(listener: (String) -> Unit) {
        onCallRequest = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recent_calls, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_recent_calls)
        recycler.layoutManager = LinearLayoutManager(context)

        if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            loadCallLog(recycler)
        } else {
             // Permission request handled in Activity or request here
             // For simplicity, assuming granted or Activity asks. 
             // Ideally show "Permission needed" view.
        }
        return view
    }

    private fun loadCallLog(recycler: RecyclerView) {
        val logs = mutableListOf<CallLogItem>()
        val cursor = requireContext().contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            null, null, null, CallLog.Calls.DATE + " DESC LIMIT 50"
        )

        cursor?.use {
            val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val number = it.getString(numberIdx)
                val type = it.getInt(typeIdx)
                val date = it.getLong(dateIdx)
                val duration = it.getLong(durationIdx)
                logs.add(CallLogItem(number, type, date, duration))
            }
        }
        
        recycler.adapter = CallLogAdapter(logs) { number ->
            onCallRequest?.invoke(number)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload logs if needed
    }
}
