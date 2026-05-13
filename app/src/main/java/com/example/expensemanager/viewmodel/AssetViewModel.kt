package com.example.expensemanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.Bill
import com.example.expensemanager.model.House
import com.example.expensemanager.model.Member
import com.example.expensemanager.model.Notification
import com.example.expensemanager.model.SharedAsset
import com.example.expensemanager.model.WarrantyItem
import com.example.expensemanager.repository.AssetRepository
import com.example.expensemanager.repository.HouseRepository
import com.example.expensemanager.repository.NotificationRepository
import com.example.expensemanager.repository.VaultRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class AssetViewModel(
    private val assetRepo : AssetRepository = AssetRepository(),
    private val vaultRepo : VaultRepository = VaultRepository(),
    private val houseRepo : HouseRepository = HouseRepository(),
    private val notificationRepo: NotificationRepository = NotificationRepository()
) : ViewModel() {
    private val uid get() = Firebase.auth.currentUser?.uid ?: ""
    private val _houseId = MutableStateFlow("")


    val house: StateFlow<House?> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { houseRepo.listenHouse(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val members: StateFlow<List<Member>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { houseRepo.listenMembers(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val pendingBills: StateFlow<List<Bill>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { assetRepo.getPendingBills(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allBills: StateFlow<List<Bill>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { assetRepo.getAllBills(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val assets: StateFlow<List<SharedAsset>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { assetRepo.getAssets(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val warrantyItems: StateFlow<List<WarrantyItem>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { vaultRepo.getWarrantyItems(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val notifications: StateFlow<List<Notification>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { notificationRepo.getNotifications(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalMonthly: StateFlow<Double> = assets
        .map { it.sumOf { a -> a.monthlyAmount } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)


    val myTotalDue: StateFlow<Double> = allBills
        .map { bills -> 
            bills.filter { !it.isPaidBy(uid) }.sumOf { it.perPersonAmount() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val myTotalPaid: StateFlow<Double> = allBills
        .map { bills -> 
            bills.filter { it.isPaidBy(uid) }.sumOf { it.perPersonAmount() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)


    private val _filter = MutableStateFlow("ALL")
    val filteredBills: StateFlow<List<Bill>> = combine(pendingBills, _filter) { bills, f ->
        if (f == "ALL") bills else bills.filter { it.category == f }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    //  Actions
    fun setHouseId(id: String) { _houseId.value = id }
    fun setFilter(f: String)   { _filter.value = f }

    fun addAsset(name: String, category: String, amount: Double,
                 dueDay: Int, members: List<String>, notes: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val asset = SharedAsset(name = name, category = category,
                monthlyAmount = amount, dueDay = dueDay,
                splitAmong = members, notes = notes)
            val r = assetRepo.addAsset(_houseId.value, asset)
            if (r.isSuccess) {
                sendNotification("Asset Added", "$name has been added to assets.")
                _uiState.value = UiState.Success("Asset added! Bill scheduled.")
            } else {
                _uiState.value = UiState.Error(r.exceptionOrNull()?.message ?: "Failed")
            }
        }
    }

    fun markPaid(billId: String) {
        if (uid.isEmpty()) {
            _uiState.value = UiState.Error("Auth error: Please log in again.")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val r = assetRepo.markMemberPaid(_houseId.value, billId, uid)
            if (r.isSuccess) {
                sendNotification("Bill Paid", "You paid your share of a bill.")
                _uiState.value = UiState.Success("Successfully marked as paid!")
            } else {
                val errorMsg = r.exceptionOrNull()?.message ?: "Could not mark as paid."
                _uiState.value = UiState.Error(errorMsg)
            }
        }
    }

    fun addWarrantyItem(item: WarrantyItem, photoFile: File?) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val urls = item.receiptImageUrls.toMutableList()
                if (photoFile != null) {
                    val up = vaultRepo.uploadReceiptImage(_houseId.value, photoFile)
                    up.getOrThrow().also { urls.add(it) }
                }
                vaultRepo.saveWarrantyItem(_houseId.value, item.copy(receiptImageUrls = urls))
                sendNotification("Vault Item Added", "${item.itemName} added to Warranty Vault.")
                _uiState.value = UiState.Success("Item added to Warranty Vault!")
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun deleteWarrantyItem(item: WarrantyItem) {
        viewModelScope.launch {
            val result = vaultRepo.deleteItem(_houseId.value, item.id)
            if (result.isSuccess) {
                sendNotification("Vault Item Deleted", "${item.itemName} removed.")
            }
        }
    }

    private fun sendNotification(title: String, message: String) {
        viewModelScope.launch {
            val notification = Notification(
                title = title,
                message = message,
                type = "INFO",
                timestamp = Timestamp.now(),
                houseId = _houseId.value
            )
            notificationRepo.addNotification(_houseId.value, notification)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            notificationRepo.markAsRead(_houseId.value, notificationId)
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            notificationRepo.deleteNotification(_houseId.value, notificationId)
        }
    }

    fun resetState() { _uiState.value = UiState.Idle }

    sealed class UiState {
        object Idle    : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String)   : UiState()
    }
}
