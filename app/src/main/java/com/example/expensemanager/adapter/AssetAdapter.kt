package com.example.expensemanager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.expensemanager.R
import com.example.expensemanager.databinding.ItemAssetBinding
import com.example.expensemanager.model.SharedAsset

class AssetAdapter(
    private val onItemClick: (SharedAsset) -> Unit
) : ListAdapter<SharedAsset, AssetAdapter.AssetViewHolder>(DIFF_CALLBACK) {

    inner class AssetViewHolder(private val binding: ItemAssetBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: SharedAsset) {
            binding.tvAssetName.text = asset.name
            binding.tvCategory.text = "${asset.categoryIcon()} ${asset.category}"
            binding.tvAmount.text = "৳ %.0f".format(asset.monthlyAmount)
            binding.tvPerPerson.text = "৳ %.0f / person".format(asset.perPersonAmount())
            binding.tvMemberCount.text = "${asset.splitAmong.size} members"
            
            val iconRes = when (asset.category) {
                "SUBSCRIPTION" -> R.drawable.ic_launcher_foreground // Replace with category icons
                "UTILITY" -> R.drawable.ic_launcher_foreground
                "MAINTENANCE" -> R.drawable.ic_launcher_foreground
                else -> R.drawable.ic_launcher_foreground
            }
            binding.ivCategoryIcon.setImageResource(iconRes)

            binding.root.setOnClickListener { onItemClick(asset) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AssetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SharedAsset>() {
            override fun areItemsTheSame(old: SharedAsset, new: SharedAsset) = old.id == new.id
            override fun areContentsTheSame(old: SharedAsset, new: SharedAsset) = old == new
        }
    }
}
