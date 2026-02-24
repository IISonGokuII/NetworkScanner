package com.mycompany.networkscanner.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mycompany.networkscanner.R
import kotlinx.coroutines.Job

abstract class BaseScannerFragment : Fragment() {

    protected lateinit var inputLayout1: TextInputLayout
    protected lateinit var input1: TextInputEditText
    protected lateinit var inputLayout2: TextInputLayout
    protected lateinit var input2: TextInputEditText
    protected lateinit var btnStart: MaterialButton
    protected lateinit var btnClear: MaterialButton
    protected lateinit var progressBar: LinearProgressIndicator
    protected lateinit var statusText: TextView
    protected lateinit var resultsText: TextView
    protected var scanJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_scanner_tool, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        inputLayout1 = view.findViewById(R.id.input_layout_1)
        input1 = view.findViewById(R.id.input_1)
        inputLayout2 = view.findViewById(R.id.input_layout_2)
        input2 = view.findViewById(R.id.input_2)
        btnStart = view.findViewById(R.id.btn_start)
        btnClear = view.findViewById(R.id.btn_clear)
        progressBar = view.findViewById(R.id.progress_bar)
        statusText = view.findViewById(R.id.status_text)
        resultsText = view.findViewById(R.id.results_text)

        setupUI()

        btnStart.setOnClickListener { onStartClicked() }
        btnClear.setOnClickListener {
            resultsText.text = ""
            statusText.text = ""
        }
    }

    abstract fun setupUI()
    abstract fun onStartClicked()

    protected fun showProgress(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    protected fun appendResult(text: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            resultsText.append(text + "\n")
        }
    }

    protected fun setStatus(text: String) {
        if (!isAdded) return
        requireActivity().runOnUiThread {
            statusText.text = text
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
    }
}
