package com.sentinel.antiscamvn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sentinel.antiscamvn.R
import io.noties.markwon.Markwon
import java.io.BufferedReader
import java.io.InputStreamReader

class GuideFragment : Fragment() {

    private lateinit var markwon: Markwon
    private val guideFiles = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val textView = view.findViewById<TextView>(R.id.txt_guide)
        val spinner = view.findViewById<Spinner>(R.id.spinner_topics)
        markwon = Markwon.create(requireContext())

        try {
            // Load file list
            val files = requireContext().assets.list("guides")
            if (!files.isNullOrEmpty()) {
                guideFiles.addAll(files.sorted())
                
                // Format titles for display (remove number and extension)
                val titles = guideFiles.map { 
                    it.substringBeforeLast(".").replace("_", " ").substringAfter(" ") 
                }

                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, titles)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter

                spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        loadGuide("guides/${guideFiles[position]}", textView)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                textView.text = "Không tìm thấy tài liệu hướng dẫn."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            textView.text = "Lỗi tải danh sách: ${e.message}"
        }
    }

    private fun loadGuide(path: String, textView: TextView) {
        try {
            val content = readAsset(path)
            markwon.setMarkdown(textView, content)
        } catch (e: Exception) {
            textView.text = "Không thể tải nội dung."
        }
    }

    private fun readAsset(fileName: String): String {
        return try {
            val inputStream = requireContext().assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            "# Lỗi\nKhông tìm thấy file hướng dẫn."
        }
    }
}