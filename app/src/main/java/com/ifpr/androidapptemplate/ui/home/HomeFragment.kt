package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.graphics.BitmapFactory
import android.util.Base64
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.databinding.FragmentHomeBinding
import com.ifpr.androidapptemplate.ui.ai.AiLogicActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private var currentLocation: Location? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "HomeFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root

        // Inicializa localização (permissões + updates)
        inicializaGerenciamentoLocalizacao()

        // Carrega itens do Firebase no container
        carregarItensMarketplace(binding.itemContainer)

        // FAB para IA
        binding.fabAi.setOnClickListener {
            startActivity(Intent(requireContext(), AiLogicActivity::class.java))
        }

        return view
    }

    private fun inicializaGerenciamentoLocalizacao() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
        } else {
            startLocationUpdates()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Snackbar.make(binding.root, "Permissão negada. Não é possível acessar a localização.", Snackbar.LENGTH_LONG).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Compatível e testado: Request clássico que funciona na maioria dos dispositivos
        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                currentLocation = loc
                displayAddress(loc)
                Log.d(TAG, "onLocationResult lat=${loc.latitude} lon=${loc.longitude}")
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun displayAddress(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
                    ?: "Endereço não encontrado"

                withContext(Dispatchers.Main) {
                    binding.currentAddressTextView.text = address
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.currentAddressTextView.text = "Erro: ${e.message}"
                }
            }
        }
    }

    fun carregarItensMarketplace(container: LinearLayout) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("itens")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                container.removeAllViews()

                for (userSnapshot in snapshot.children) {
                    for (itemSnapshot in userSnapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue

                        val itemView = LayoutInflater.from(container.context).inflate(R.layout.item_template, container, false)

                        val imageView = itemView.findViewById<ImageView>(R.id.item_image)
                        val objetoView = itemView.findViewById<TextView>(R.id.objetoAdd)
                        val quantidadeView = itemView.findViewById<TextView>(R.id.quantidadeItens)
                        val btnSetLocation = itemView.findViewById<Button>(R.id.btnSetLocation)
                        val btnGoogleMaps = itemView.findViewById<Button>(R.id.btnGoogleMaps)
                        val btnWaze = itemView.findViewById<Button>(R.id.btnWaze)

                        objetoView.text = "Objeto: ${item.objeto ?: "Não informado"}"
                        quantidadeView.text = "Quantidade: ${item.quantidade ?: "Não informado"}"

                        // Imagem: URL ou Base64
                        if (!item.imageUrl.isNullOrEmpty()) {
                            Glide.with(container.context).load(item.imageUrl).into(imageView)
                        } else if (!item.base64Image.isNullOrEmpty()) {
                            try {
                                val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) { /* ignore */ }
                        }

                        // Listeners
                        btnSetLocation.setOnClickListener { setItemLocation(item) }
                        btnGoogleMaps.setOnClickListener { openLocationInGoogleMaps(item) }
                        btnWaze.setOnClickListener { openLocationInWaze(item) }

                        container.addView(itemView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(container.context, "Erro ao carregar dados: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setItemLocation(item: Item) {
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(requireContext(), "Localização ainda não disponível.", Toast.LENGTH_SHORT).show()
            return
        }

        // Atualiza também o objeto local (se desejar)
        item.latitude = loc.latitude
        item.longitude = loc.longitude

        val ref = FirebaseDatabase.getInstance().getReference("itens")
        ref.get().addOnSuccessListener { snapshot ->
            var salvou = false

            // Percorre usuários -> itens (estrutura esperada)
            for (userSnap in snapshot.children) {
                for (itemSnap in userSnap.children) {
                    val dbItem = itemSnap.getValue(Item::class.java)

                    // comparação robusta por nome (sem maiúsculas/minúsculas e espaços)
                    if (dbItem?.objeto?.trim()?.lowercase() == item.objeto?.trim()?.lowercase()) {
                        itemSnap.ref.child("latitude").setValue(loc.latitude)
                        itemSnap.ref.child("longitude").setValue(loc.longitude)
                        salvou = true
                        break
                    }
                }
                if (salvou) break
            }

            if (salvou) {
                Toast.makeText(requireContext(), "Localização salva no Firebase!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Item não encontrado no banco.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Erro ao acessar Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Firebase get() failed", e)
        }
    }

    private fun openLocationInGoogleMaps(item: Item) {
        if (item.latitude == null || item.longitude == null) {
            Toast.makeText(requireContext(), "Localização do item não definida.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = "geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(Local+do+Objeto)"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage("com.google.android.apps.maps")
        }

        // Se Maps não instalado, abre sem package
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }

    private fun openLocationInWaze(item: Item) {
        if (item.latitude == null || item.longitude == null) {
            Toast.makeText(requireContext(), "Localização do item não definida.", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = "waze://?ll=${item.latitude},${item.longitude}&navigate=no"
        val wazeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage("com.waze")
        }

        // tenta abrir Waze nativo; se não existir, abre link web
        if (wazeIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(wazeIntent)
        } else {
            val webUri = "https://waze.com/ul?ll=${item.latitude},${item.longitude}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Remove updates para evitar leak
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        _binding = null
    }
}
