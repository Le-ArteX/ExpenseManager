package com.example.expensemanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.expensemanager.databinding.FragmentCameraBinding
import com.example.expensemanager.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CameraFragment : Fragment() {
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val args: CameraFragmentArgs by navArgs()
    private val vaultRepo = VaultRepository()

    private var imageCapture: ImageCapture? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnClose.setOnClickListener { findNavController().popBackStack() }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            requireContext().cacheDir,
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        binding.btnCapture.isEnabled = false
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo capture failed: ${exc.message}", exc)
                    binding.btnCapture.isEnabled = true
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    uploadPhoto(photoFile)
                }
            }
        )
    }

    private fun uploadPhoto(file: File) {
        Toast.makeText(requireContext(), "Uploading receipt...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = if (args.warrantyItemId.isNotEmpty()) {
                vaultRepo.addReceiptToItem(args.houseId, args.warrantyItemId, file)
            } else {
                vaultRepo.uploadReceiptImage(args.houseId, file)
            }

            if (result.isSuccess) {
                Toast.makeText(requireContext(), "Receipt saved!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else {
                Toast.makeText(requireContext(), "Upload failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                binding.btnCapture.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
