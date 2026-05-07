package com.example.expensemanager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanager.R
import com.example.expensemanager.databinding.ItemVaultBinding
import com.example.expensemanager.model.WarrantyItem

class VaultAdapter(
    private val onItemClick: (WarrantyItem) -> Unit
) : ListAdapter<WarrantyItem, VaultAdapter.VaultViewHolder>(DIFF_CALLBACK) {

    inner class VaultViewHolder(private val binding: ItemVaultBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: WarrantyItem) {
            binding.tvItemName.text = item.itemName
            binding.tvBrand.text = item.brand
            binding.tvPrice.text = "BDT %.0f".format(item.purchasePrice)
            
            val status = item.warrantyStatus()
            binding.tvWarrantyStatus.text = status
            
            val statusColor = when (status) {
                "VALID" -> R.color.green_success
                "EXPIRING" -> R.color.amber_accent
                else -> R.color.red_error
            }
            binding.tvWarrantyStatus.setTextColor(ContextCompat.getColor(binding.root.context, statusColor))
            
            val count = item.receiptImageUrls.size
            binding.tvViewReceipt.text = if (count > 0) "$count receipts saved" else "No receipts"
            
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val binding = ItemVaultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VaultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<WarrantyItem>() {
            override fun areItemsTheSame(old: WarrantyItem, new: WarrantyItem) = old.id == new.id
            override fun areContentsTheSame(old: WarrantyItem, new: WarrantyItem) = old == new
        }
    }
}
