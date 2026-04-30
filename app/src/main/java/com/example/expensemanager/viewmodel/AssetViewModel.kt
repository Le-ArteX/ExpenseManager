package com.example.expensemanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.expensemanager.model.Bill
import com.example.expensemanager.model.SharedAsset
import com.example.expensemanager.model.WarrantyItem
import com.example.expensemanager.repository.AssetRepository
import com.example.expensemanager.repository.VaultRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
class AssetViewModel(
    private val assetRepo : AssetRepository = AssetRepository(),
    private val vaultRepo : VaultRepository = VaultRepository()
) : ViewModel() {
    private val uid get() = Firebase.auth.currentUser?.uid ?: ""
    private val _houseId = MutableStateFlow("")
    //  Exposed StateFlows
    val pendingBills: StateFlow<List<Bill>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { assetRepo.getPendingBills(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val assets: StateFlow<List<SharedAsset>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { assetRepo.getAssets(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val warrantyItems: StateFlow<List<WarrantyItem>> = _houseId
        .filter { it.isNotEmpty() }
        .flatMapLatest { vaultRepo.getWarrantyItems(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val totalMonthly: StateFlow<Double> = assets
        .map { it.sumOf { a -> a.monthlyAmount } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    val myTotalDue: StateFlow<Double> = pendingBills
        .map { bills -> bills.filter { !it.isPaidBy(uid) }.sumOf { it.perPersonAmount() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    // Category filter
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
            _uiState.value = if (r.isSuccess)
                UiState.Success("Asset added! Bill scheduled automatically.")
            else UiState.Error(r.exceptionOrNull()?.message ?: "Failed")
        }
    }
    fun markPaid(billId: String) {
        viewModelScope.launch {
            val r = assetRepo.markMemberPaid(_houseId.value, billId, uid)
            if (r.isFailure)
                _uiState.value = UiState.Error("Could not mark as paid. Please retry.")
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
                _uiState.value = UiState.Success("Item added to Warranty Vault!")
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Upload failed")
            }
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
