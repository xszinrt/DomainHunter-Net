package com.example.domainhunter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.domainhunter.data.DomainEntity
import com.example.domainhunter.databinding.ItemDomainBinding

class DomainAdapter : ListAdapter<DomainEntity, DomainAdapter.DomainViewHolder>(DomainDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DomainViewHolder {
        val binding = ItemDomainBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DomainViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DomainViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DomainViewHolder(private val binding: ItemDomainBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(domain: DomainEntity) {
            val context = binding.root.context
            binding.tvDomainName.text = domain.domainName
            binding.tvExpiryDate.text = "ينتهي في: ${domain.expiryDate}"
            binding.tvDaysLeft.text = "متبقٍ: ${domain.daysLeft} يوم"

            // هندسة التلوين الديناميكي النفسي حسب المسافة الزمنية للسقوط
            when {
                domain.daysLeft <= 30 -> {
                    binding.tvExpiringSoon.visibility = View.VISIBLE
                    binding.tvExpiringSoon.text = "🚨 حرِج جداً"
                    binding.tvDaysLeft.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
                }
                domain.daysLeft <= 90 -> {
                    binding.tvExpiringSoon.visibility = View.VISIBLE
                    binding.tvExpiringSoon.text = "⚠️ قريب السقوط"
                    binding.tvDaysLeft.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
                }
                else -> {
                    binding.tvExpiringSoon.visibility = View.GONE
                    binding.tvDaysLeft.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
            }

            // 🌐 تفعيل زر الفتح المباشر في المتصفح لرؤية النطاق
            binding.btnOpen.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://${domain.domainName}"))
                context.startActivity(intent)
            }

            // 📋 تفعيل زر النسخ المباشر لاسم النطاق إلى الحافظة
            binding.btnCopy.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("domain", domain.domainName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "تم نسخ: ${domain.domainName}", Toast.LENGTH_SHORT).show()
            }

            // 🔍 تفعيل زر الفحص والبحث خلف الدومين في جوجل بلمحة بصر
            binding.btnGoogle.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${domain.domainName}"))
                context.startActivity(intent)
            }
        }
    }

    class DomainDiffCallback : DiffUtil.ItemCallback<DomainEntity>() {
        override fun areItemsTheSame(oldItem: DomainEntity, newItem: DomainEntity): Boolean = oldItem.domainName == newItem.domainName
        override fun areContentsTheSame(oldItem: DomainEntity, newItem: DomainEntity): Boolean = oldItem == newItem
    }
}
