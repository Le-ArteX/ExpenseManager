package com.example.expensemanager.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.example.expensemanager.R
import com.example.expensemanager.databinding.ItemBillBinding
import com.example.expensemanager.model.Bill
import java.text.SimpleDateFormat
import java.util.*

class BillAdapter(
    private val currentUserId: String,
    private val onPayClick: (Bill) -> Unit
) : ListAdapter<Bill, BillAdapter.BillViewHolder>(DIFF_CALLBACK) {

    inner class BillViewHolder(private val binding: ItemBillBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: Bill) {
            binding.tvBillName.text = bill.assetName
            binding.tvAmount.text = "৳ %.0f".format(bill.amount)
            binding.tvPerPerson.text = "৳ %.0f/person".format(bill.perPersonAmount())
            
            // Due date and Status text
            val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
            val dueStr = bill.dueDate?.toDate()?.let { fmt.format(it) } ?: "—"
            binding.tvDueDate.text = "Due $dueStr · ${bill.status}"
            
            // Colors based on status
            val statusColor = when (bill.status) {
                "OVERDUE" -> R.color.red_error
                "PARTIAL" -> R.color.amber_accent
                "PAID" -> R.color.green_success
                else -> R.color.teal_mid
            }
            val colorInt = ContextCompat.getColor(binding.root.context, statusColor)
            
            binding.statusIndicator.setBackgroundColor(colorInt)
            binding.tvDueDate.setTextColor(colorInt)
            
            // Category icon
            val iconRes = when (bill.category) {
                "SUBSCRIPTION" -> R.drawable.ic_bills // In a real app, use specific icons
                "UTILITY" -> R.drawable.ic_bills
                "MAINTENANCE" -> R.drawable.ic_bills
                else -> R.drawable.ic_bills
            }
            binding.ivCategory.setImageResource(iconRes)
            binding.ivCategory.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(binding.root.context, R.color.teal_primary)
            )
            
            // Progress bar
            val paidCount = bill.paidCount()
            val total = bill.totalCount().coerceAtLeast(1)
            binding.progressPaid.progress = (paidCount * 100) / total
            binding.tvProgressText.text = "$paidCount of $total paid"
            
            // Build member chips
            binding.chipGroupMembers.removeAllViews()
            bill.paidBy.forEach { (memberId, paid) ->
                val chip = Chip(binding.root.context).apply {
                    text = "Member" // Ideally we'd map UID to name, but for now show placeholder or UID
                    isCheckable = false
                    setChipBackgroundColorResource(
                        if (paid) R.color.green_light else R.color.red_light
                    )
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (paid) R.color.green_success else R.color.red_error
                        )
                    )
                    chipMinHeight = 24f
                    textSize = 9f
                }
                binding.chipGroupMembers.addView(chip)
            }

            // Show Pay button only if current user hasn't paid
            val alreadyPaid = bill.isPaidBy(currentUserId)
            binding.btnPay.visibility = if (alreadyPaid || bill.status == "PAID")
                View.GONE else View.VISIBLE
            binding.btnPay.setOnClickListener { onPayClick(bill) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Bill>() {
            override fun areItemsTheSame(old: Bill, new: Bill) = old.id == new.id
            override fun areContentsTheSame(old: Bill, new: Bill) = old == new
        }
    }
}
