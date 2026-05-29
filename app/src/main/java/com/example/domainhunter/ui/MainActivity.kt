package com.example.domainhunter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.domainhunter.data.AppDatabase
import com.example.domainhunter.databinding.ActivityMainBinding
import com.example.domainhunter.service.DomainScanService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val loadedDomains = ArrayList<String>()
    private val domainAdapter = DomainAdapter()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { readDomainsFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeDatabase("ALL")

        binding.btnImport.setOnClickListener { filePickerLauncher.launch("text/plain") }

        binding.btnStart.setOnClickListener {
            if (loadedDomains.isNotEmpty()) {
                val intent = Intent(this, DomainScanService::class.java).apply {
                    action = DomainScanService.ACTION_START
                    putStringArrayListExtra("DOMAINS", loadedDomains)
                }
                startService(intent)
                Toast.makeText(this, "🚀 انطلق نبش الخلفية بنجاح!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ يرجى استيراد ملف النطاقات أولاً", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPause.setOnClickListener {
            val intent = Intent(this, DomainScanService::class.java).apply { action = DomainScanService.ACTION_PAUSE }
            startService(intent)
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, DomainScanService::class.java).apply { action = DomainScanService.ACTION_STOP }
            startService(intent)
            Toast.makeText(this, "🛑 تم إنهاء عملية الفحص كلياً", Toast.LENGTH_SHORT).show()
        }

        // تفعيل شريط فلاتر الوقت الأفقي الذكي لفرز قاعدة البيانات فورا
        binding.chipAll.setOnClickListener { observeDatabase("ALL") }
        binding.chipUrgent.setOnClickListener { observeDatabase("URGENT") }
        binding.chipSoon.setOnClickListener { observeDatabase("SOON") }
        binding.chipNextYear.setOnClickListener { observeDatabase("NEXT_YEAR") }

        // تفعيل محرك النبش والبحث السريع الحي في النتائج
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                observeDatabase("SEARCH", s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // زر نسخ كل الأهداف المكتشفة إلى الحافظة دفعة واحدة لسهولة النقل
        binding.btnCopyAll.setOnClickListener {
            val currentList = domainAdapter.currentList
            if (currentList.isNotEmpty()) {
                val sb = StringBuilder()
                for (item in currentList) {
                    sb.append(item.domainName).append("\n")
                }
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("all_domains", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "📋 تم نسخ جميع النطاقات الحالية (${currentList.size})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "📭 القائمة فارغة حالياً!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = domainAdapter
        }
    }

    private fun observeDatabase(filterType: String, query: String = "") {
        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).domainDao()
            val flow = when (filterType) {
                "URGENT" -> dao.getUrgentDomains()
                "SOON" -> dao.getSoonDomains()
                "NEXT_YEAR" -> dao.getNextYearDomains()
                "SEARCH" -> dao.searchDomains("%$query%")
                else -> dao.getAllDomains()
            }
            flow.collectLatest { list ->
                domainAdapter.submitList(list)
                binding.tvStats.text = "🎯 ${list.size} نطاق مكتشف"
            }
        }
    }

    private fun readDomainsFromFile(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                loadedDomains.clear()
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val cleanLine = line?.trim() ?: ""
                            if (cleanLine.isNotEmpty()) {
                                loadedDomains.add(cleanLine)
                            }
                        }
                    }
                }
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "📂 تم تحميل ${loadedDomains.size} نطاق بنجاح!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ فشل قراءة الملف!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
