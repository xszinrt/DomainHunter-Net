package com.example.domainhunter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovered_domains")
data class DomainEntity(
    @PrimaryKey 
    val domainName: String,       // مفتاح رئيسي لمنع تكرار نفس الدومين
    val expiryDate: String,       // تاريخ الانتهاء بصيغة نصية قياسية YYYY-MM-DD
    val daysLeft: Int,            // الأيام المتبقية لسهولة الفرز العددي السريع
    val sessionId: Long,          // رقم الجلسة لحماية البيانات ودعم استئناف الفحص
    val isFavorite: Boolean = false
)
