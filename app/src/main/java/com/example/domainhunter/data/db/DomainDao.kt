package com.example.domainhunter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {

    // إدخال أو تحديث البيانات تلقائياً دون تكرار الدومين
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomain(domain: DomainEntity)

    // 1. جلب الكل وترتيبهم تنازلياً حسب الأقرب للسقوط
    @Query("SELECT * FROM discovered_domains ORDER BY daysLeft ASC")
    fun getAllDomains(): Flow<List<DomainEntity>>

    // 2. فلتر الطوارئ: النطاقات التي تسقط خلال أقل من 30 يوماً
    @Query("SELECT * FROM discovered_domains WHERE daysLeft <= 30 ORDER BY daysLeft ASC")
    fun getUrgentDomains(): Flow<List<DomainEntity>>

    // 3. فلتر القريب: النطاقات التي تسقط خلال أقل من 90 يوماً
    @Query("SELECT * FROM discovered_domains WHERE daysLeft <= 90 ORDER BY daysLeft ASC")
    fun getSoonDomains(): Flow<List<DomainEntity>>

    // 4. فلتر النطاقات البعيدة (المحجوزة لعام 2026 وما فوق)
    @Query("SELECT * FROM discovered_domains WHERE expiryDate >= '2026-01-01' ORDER BY daysLeft ASC")
    fun getNextYearDomains(): Flow<List<DomainEntity>>

    // 5. محرك البحث الذكي: النبش بالاسم داخل النتائج المحلية المسترجعة
    @Query("SELECT * FROM discovered_domains WHERE domainName LIKE :searchQuery ORDER BY daysLeft ASC")
    fun searchDomains(searchQuery: String): Flow<List<DomainEntity>>

    // مسح كافة البيانات لبدء جلسة تصفية جديدة بالكامل
    @Query("DELETE FROM discovered_domains")
    suspend fun clearAll()
}
